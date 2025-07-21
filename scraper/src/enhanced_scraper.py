"""
Enhanced Async Web Scraper with circuit breaker, rate limiting, and Kafka integration
Supports multiple data sources: stock prices, news, and economic indicators
"""
import asyncio
import time
import json
from typing import Dict, List, Optional, Any, Set
from dataclasses import dataclass
from datetime import datetime, timezone
import aiohttp
import aiofiles
from asyncio_throttle import Throttler
from tenacity import retry, stop_after_attempt, wait_exponential
import structlog
from prometheus_client import Counter, Histogram, Gauge, start_http_server
from bs4 import BeautifulSoup
import pandas as pd
import numpy as np
from urllib.parse import urljoin, urlparse
import yaml
import os
from dotenv import load_dotenv

from circuit_breaker import AsyncCircuitBreaker, STOCK_API_BREAKER, NEWS_API_BREAKER, ECONOMIC_API_BREAKER
from kafka_producer import KafkaProducerManager, StockData, NewsData, EconomicData

# Load environment variables
load_dotenv()

# Setup structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    wrapper_class=structlog.stdlib.BoundLogger,
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()

# Prometheus metrics
SCRAPE_REQUESTS = Counter(
    'scraper_requests_total',
    'Total number of scrape requests',
    ['source', 'status']
)

SCRAPE_DURATION = Histogram(
    'scraper_duration_seconds',
    'Time spent scraping data',
    ['source']
)

ACTIVE_SCRAPERS = Gauge(
    'scraper_active_count',
    'Number of active scraper instances'
)

DATA_POINTS_SCRAPED = Counter(
    'scraper_data_points_total',
    'Total number of data points scraped',
    ['data_type', 'source']
)


@dataclass
class ScrapingTarget:
    """Configuration for a scraping target"""
    name: str
    url: str
    data_type: str  # 'stock', 'news', 'economic'
    rate_limit: float  # requests per second
    timeout: float
    headers: Dict[str, str]
    selector_config: Dict[str, Any]
    enabled: bool = True


class EnhancedAsyncScraper:
    """
    Enhanced async web scraper with resilience patterns
    """
    
    def __init__(self, config_path: str = "config/config.yaml"):
        self.config = self._load_config(config_path)
        self.session: Optional[aiohttp.ClientSession] = None
        self.kafka_producer: Optional[KafkaProducerManager] = None
        self.throttlers: Dict[str, Throttler] = {}
        self.circuit_breakers: Dict[str, AsyncCircuitBreaker] = {}
        self.active_tasks: Set[asyncio.Task] = set()
        self._running = False
        
        # Initialize components
        self._setup_throttlers()
        self._setup_circuit_breakers()
        
        ACTIVE_SCRAPERS.inc()
    
    def _load_config(self, config_path: str) -> Dict[str, Any]:
        """Load configuration from YAML file"""
        try:
            with open(config_path, 'r') as file:
                config = yaml.safe_load(file)
            
            # Override with environment variables
            self._apply_env_overrides(config)
            
            logger.info("Configuration loaded successfully", config_file=config_path)
            return config
        except FileNotFoundError:
            logger.warning("Config file not found, using default configuration")
            return self._default_config()
        except Exception as e:
            logger.error("Failed to load configuration", error=str(e))
            raise
    
    def _apply_env_overrides(self, config: Dict[str, Any]) -> None:
        """Apply environment variable overrides to configuration"""
        env_mappings = {
            'KAFKA_BOOTSTRAP_SERVERS': ['kafka', 'bootstrap_servers'],
            'SCHEMA_REGISTRY_URL': ['schema_registry', 'url'],
            'ALPHA_VANTAGE_API_KEY': ['apis', 'alpha_vantage', 'api_key'],
            'NEWS_API_KEY': ['apis', 'news_api', 'api_key'],
            'FRED_API_KEY': ['apis', 'fred', 'api_key'],
        }
        
        for env_var, config_path in env_mappings.items():
            value = os.getenv(env_var)
            if value:
                # Navigate to nested config and set value
                current = config
                for key in config_path[:-1]:
                    current = current.setdefault(key, {})
                current[config_path[-1]] = value
    
    def _default_config(self) -> Dict[str, Any]:
        """Return default configuration"""
        return {
            'scraper': {
                'user_agent': 'StockTracker-Scraper/1.0',
                'concurrent_limit': 10,
                'timeout': 30,
                'retry_attempts': 3,
                'retry_delay': 1,
            },
            'kafka': {
                'bootstrap_servers': 'localhost:9092',
                'topics': {
                    'stock_data': 'stock-data',
                    'news_data': 'news-data',
                    'economic_data': 'economic-data'
                }
            },
            'targets': [],
            'monitoring': {
                'metrics_port': 8000,
                'health_check_interval': 60
            }
        }
    
    def _setup_throttlers(self) -> None:
        """Setup rate limiters for different sources"""
        default_rate = self.config.get('scraper', {}).get('default_rate_limit', 1.0)
        
        # Create throttlers for each configured target
        for target_config in self.config.get('targets', []):
            target_name = target_config['name']
            rate_limit = target_config.get('rate_limit', default_rate)
            self.throttlers[target_name] = Throttler(rate_limit=rate_limit)
        
        # Default throttlers for common sources
        if 'stock_api' not in self.throttlers:
            self.throttlers['stock_api'] = Throttler(rate_limit=5.0)
        if 'news_api' not in self.throttlers:
            self.throttlers['news_api'] = Throttler(rate_limit=2.0)
        if 'economic_api' not in self.throttlers:
            self.throttlers['economic_api'] = Throttler(rate_limit=1.0)
    
    def _setup_circuit_breakers(self) -> None:
        """Setup circuit breakers for different sources"""
        self.circuit_breakers = {
            'stock_api': STOCK_API_BREAKER,
            'news_api': NEWS_API_BREAKER,
            'economic_api': ECONOMIC_API_BREAKER,
        }
        
        # Add circuit breakers for configured targets
        for target_config in self.config.get('targets', []):
            target_name = target_config['name']
            if target_name not in self.circuit_breakers:
                self.circuit_breakers[target_name] = AsyncCircuitBreaker(
                    failure_threshold=target_config.get('failure_threshold', 3),
                    timeout=target_config.get('circuit_timeout', 60.0),
                    name=target_name
                )
    
    async def start(self) -> None:
        """Start the scraper and all its components"""
        logger.info("Starting enhanced async scraper")
        
        # Start metrics server
        metrics_port = self.config.get('monitoring', {}).get('metrics_port', 8000)
        start_http_server(metrics_port)
        logger.info(f"Metrics server started on port {metrics_port}")
        
        # Initialize HTTP session
        connector = aiohttp.TCPConnector(
            limit=self.config.get('scraper', {}).get('concurrent_limit', 10),
            limit_per_host=5,
            ttl_dns_cache=300,
            use_dns_cache=True,
        )
        
        timeout = aiohttp.ClientTimeout(
            total=self.config.get('scraper', {}).get('timeout', 30)
        )
        
        headers = {
            'User-Agent': self.config.get('scraper', {}).get('user_agent', 'StockTracker-Scraper/1.0')
        }
        
        self.session = aiohttp.ClientSession(
            connector=connector,
            timeout=timeout,
            headers=headers
        )
        
        # Initialize Kafka producer
        self.kafka_producer = KafkaProducerManager(self.config)
        
        self._running = True
        logger.info("Scraper started successfully")
    
    async def stop(self) -> None:
        """Stop the scraper and cleanup resources"""
        logger.info("Stopping scraper")
        self._running = False
        
        # Cancel active tasks
        for task in self.active_tasks:
            if not task.done():
                task.cancel()
        
        # Wait for tasks to complete
        if self.active_tasks:
            await asyncio.gather(*self.active_tasks, return_exceptions=True)
        
        # Close HTTP session
        if self.session:
            await self.session.close()
        
        # Close Kafka producer
        if self.kafka_producer:
            await self.kafka_producer.close()
        
        ACTIVE_SCRAPERS.dec()
        logger.info("Scraper stopped")
    
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=1, max=10)
    )
    async def _fetch_with_resilience(
        self,
        url: str,
        source: str,
        headers: Optional[Dict[str, str]] = None
    ) -> Optional[str]:
        """
        Fetch URL with circuit breaker, rate limiting, and retry logic
        """
        if not self.session:
            raise RuntimeError("Scraper not started")
        
        # Apply rate limiting
        throttler = self.throttlers.get(source, self.throttlers.get('default'))
        if throttler:
            async with throttler:
                pass
        
        # Apply circuit breaker
        circuit_breaker = self.circuit_breakers.get(source)
        if circuit_breaker:
            async with circuit_breaker:
                return await self._fetch_url(url, source, headers)
        else:
            return await self._fetch_url(url, source, headers)
    
    async def _fetch_url(
        self,
        url: str,
        source: str,
        headers: Optional[Dict[str, str]] = None
    ) -> Optional[str]:
        """
        Fetch URL with error handling and metrics
        """
        start_time = time.time()
        
        try:
            with SCRAPE_DURATION.labels(source=source).time():
                request_headers = headers or {}
                
                async with self.session.get(url, headers=request_headers) as response:
                    if response.status == 200:
                        content = await response.text()
                        SCRAPE_REQUESTS.labels(source=source, status='success').inc()
                        logger.debug(
                            "Successfully fetched URL",
                            url=url,
                            source=source,
                            status=response.status,
                            content_length=len(content)
                        )
                        return content
                    else:
                        SCRAPE_REQUESTS.labels(source=source, status='error').inc()
                        logger.warning(
                            "HTTP error while fetching URL",
                            url=url,
                            source=source,
                            status=response.status
                        )
                        return None
        
        except asyncio.TimeoutError:
            SCRAPE_REQUESTS.labels(source=source, status='timeout').inc()
            logger.warning("Timeout while fetching URL", url=url, source=source)
            raise
        
        except Exception as e:
            SCRAPE_REQUESTS.labels(source=source, status='error').inc()
            logger.error(
                "Error while fetching URL",
                url=url,
                source=source,
                error=str(e)
            )
            raise
    
    async def scrape_stock_data(self, symbols: List[str]) -> List[StockData]:
        """
        Scrape stock data for given symbols
        """
        stock_data = []
        
        for symbol in symbols:
            try:
                # Example: Alpha Vantage API integration
                api_key = self.config.get('apis', {}).get('alpha_vantage', {}).get('api_key')
                if not api_key:
                    logger.warning("Alpha Vantage API key not configured")
                    continue
                
                url = f"https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol={symbol}&apikey={api_key}"
                
                content = await self._fetch_with_resilience(url, 'stock_api')
                if content:
                    data = json.loads(content)
                    quote = data.get('Global Quote', {})
                    
                    if quote:
                        stock_datum = StockData(
                            symbol=symbol,
                            price=float(quote.get('05. price', 0)),
                            volume=int(float(quote.get('06. volume', 0))),
                            timestamp=int(time.time()),
                            change=float(quote.get('09. change', 0)),
                            change_percent=float(quote.get('10. change percent', '0%').rstrip('%')),
                            source='alpha_vantage'
                        )
                        
                        stock_data.append(stock_datum)
                        DATA_POINTS_SCRAPED.labels(data_type='stock', source='alpha_vantage').inc()
                        
                        # Send to Kafka
                        if self.kafka_producer:
                            await self.kafka_producer.produce_stock_data(stock_datum)
            
            except Exception as e:
                logger.error(f"Failed to scrape stock data for {symbol}", error=str(e))
        
        return stock_data
    
    async def scrape_news_data(self, query: str = "stock market", limit: int = 10) -> List[NewsData]:
        """
        Scrape news data related to stocks
        """
        news_data = []
        
        try:
            # Example: News API integration
            api_key = self.config.get('apis', {}).get('news_api', {}).get('api_key')
            if not api_key:
                logger.warning("News API key not configured")
                return news_data
            
            url = f"https://newsapi.org/v2/everything?q={query}&sortBy=publishedAt&pageSize={limit}&apiKey={api_key}"
            
            content = await self._fetch_with_resilience(url, 'news_api')
            if content:
                data = json.loads(content)
                articles = data.get('articles', [])
                
                for article in articles:
                    if article.get('title') and article.get('content'):
                        news_datum = NewsData(
                            title=article['title'],
                            content=article.get('content', '')[:1000],  # Truncate content
                            url=article.get('url', ''),
                            timestamp=int(datetime.fromisoformat(
                                article.get('publishedAt', '').replace('Z', '+00:00')
                            ).timestamp()),
                            source=article.get('source', {}).get('name', 'unknown'),
                            category='general'
                        )
                        
                        news_data.append(news_datum)
                        DATA_POINTS_SCRAPED.labels(data_type='news', source='news_api').inc()
                        
                        # Send to Kafka
                        if self.kafka_producer:
                            await self.kafka_producer.produce_news_data(news_datum)
        
        except Exception as e:
            logger.error("Failed to scrape news data", error=str(e))
        
        return news_data
    
    async def scrape_economic_data(self) -> List[EconomicData]:
        """
        Scrape economic indicators data
        """
        economic_data = []
        
        try:
            # Example: FRED API integration for economic data
            api_key = self.config.get('apis', {}).get('fred', {}).get('api_key')
            if not api_key:
                logger.warning("FRED API key not configured")
                return economic_data
            
            # Common economic indicators
            indicators = ['GDP', 'UNRATE', 'CPIAUCSL', 'FEDFUNDS']
            
            for indicator in indicators:
                url = f"https://api.stlouisfed.org/fred/series/observations?series_id={indicator}&api_key={api_key}&file_type=json&limit=1&sort_order=desc"
                
                content = await self._fetch_with_resilience(url, 'economic_api')
                if content:
                    data = json.loads(content)
                    observations = data.get('observations', [])
                    
                    if observations and observations[0].get('value') != '.':
                        obs = observations[0]
                        economic_datum = EconomicData(
                            indicator=indicator,
                            value=float(obs['value']),
                            timestamp=int(datetime.fromisoformat(obs['date']).timestamp()),
                            country='US',
                            source='fred',
                            unit=data.get('units', 'Unknown'),
                            period='monthly'
                        )
                        
                        economic_data.append(economic_datum)
                        DATA_POINTS_SCRAPED.labels(data_type='economic', source='fred').inc()
                        
                        # Send to Kafka
                        if self.kafka_producer:
                            await self.kafka_producer.produce_economic_data(economic_datum)
        
        except Exception as e:
            logger.error("Failed to scrape economic data", error=str(e))
        
        return economic_data
    
    async def run_continuous_scraping(self, interval: int = 60) -> None:
        """
        Run continuous scraping with configurable interval
        """
        logger.info(f"Starting continuous scraping with {interval}s interval")
        
        while self._running:
            try:
                # Create scraping tasks
                tasks = []
                
                # Scrape stock data
                stock_symbols = self.config.get('scraper', {}).get('stock_symbols', ['AAPL', 'GOOGL', 'MSFT', 'TSLA'])
                if stock_symbols:
                    task = asyncio.create_task(self.scrape_stock_data(stock_symbols))
                    tasks.append(task)
                    self.active_tasks.add(task)
                
                # Scrape news data
                task = asyncio.create_task(self.scrape_news_data())
                tasks.append(task)
                self.active_tasks.add(task)
                
                # Scrape economic data (less frequently)
                if int(time.time()) % (interval * 10) == 0:  # Every 10 intervals
                    task = asyncio.create_task(self.scrape_economic_data())
                    tasks.append(task)
                    self.active_tasks.add(task)
                
                # Wait for all tasks to complete
                if tasks:
                    await asyncio.gather(*tasks, return_exceptions=True)
                
                # Clean up completed tasks
                self.active_tasks = {task for task in self.active_tasks if not task.done()}
                
                # Wait before next iteration
                await asyncio.sleep(interval)
                
            except Exception as e:
                logger.error("Error in continuous scraping loop", error=str(e))
                await asyncio.sleep(interval)
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform health check and return status
        """
        status = {
            'status': 'healthy',
            'timestamp': int(time.time()),
            'components': {}
        }
        
        # Check HTTP session
        status['components']['http_session'] = {
            'status': 'healthy' if self.session and not self.session.closed else 'unhealthy'
        }
        
        # Check Kafka producer
        status['components']['kafka_producer'] = {
            'status': 'healthy' if self.kafka_producer and self.kafka_producer.is_connected else 'unhealthy'
        }
        
        # Check circuit breakers
        for name, breaker in self.circuit_breakers.items():
            status['components'][f'circuit_breaker_{name}'] = {
                'status': breaker.state.value,
                'failure_count': breaker.failure_count
            }
        
        # Overall status
        if any(comp['status'] == 'unhealthy' for comp in status['components'].values()):
            status['status'] = 'unhealthy'
        
        return status


async def main():
    """
    Main function to run the scraper
    """
    scraper = EnhancedAsyncScraper()
    
    try:
        await scraper.start()
        
        # Run continuous scraping
        await scraper.run_continuous_scraping(interval=60)
        
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    except Exception as e:
        logger.error("Unexpected error in main", error=str(e))
    finally:
        await scraper.stop()


if __name__ == "__main__":
    asyncio.run(main())