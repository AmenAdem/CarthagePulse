"""
Enhanced Python Web Scraper with Async Architecture
Provides concurrent scraping with rate limiting, circuit breakers, and Kafka integration
"""

import asyncio
import aiohttp
import aiokafka
import structlog
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, asdict
from datetime import datetime
import json
from tenacity import retry, stop_after_attempt, wait_exponential
from prometheus_client import Counter, Histogram, Gauge
import os
from dotenv import load_dotenv

load_dotenv()

# Configure structured logging
logger = structlog.get_logger(__name__)

# Prometheus metrics
scraping_requests = Counter('scraping_requests_total', 'Total scraping requests', ['source', 'status'])
scraping_duration = Histogram('scraping_duration_seconds', 'Time spent scraping', ['source'])
active_connections = Gauge('active_connections', 'Number of active connections', ['source'])

@dataclass
class ScrapingConfig:
    """Configuration for scraping sources"""
    name: str
    url: str
    rate_limit: float  # requests per second
    timeout: int = 30
    headers: Dict[str, str] = None
    retry_attempts: int = 3
    circuit_breaker_threshold: int = 5

@dataclass
class StockData:
    """Stock data model with validation"""
    symbol: str
    price: float
    volume: int
    timestamp: datetime
    source: str
    change_percent: Optional[float] = None
    market_cap: Optional[float] = None
    
    def to_kafka_message(self) -> Dict[str, Any]:
        """Convert to Kafka message format"""
        return {
            **asdict(self),
            'timestamp': self.timestamp.isoformat()
        }

class CircuitBreaker:
    """Circuit breaker pattern implementation"""
    
    def __init__(self, failure_threshold: int = 5, recovery_timeout: int = 60):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.failure_count = 0
        self.last_failure_time = None
        self.state = 'CLOSED'  # CLOSED, OPEN, HALF_OPEN
    
    async def call(self, func, *args, **kwargs):
        if self.state == 'OPEN':
            if self._should_attempt_reset():
                self.state = 'HALF_OPEN'
            else:
                raise Exception(f"Circuit breaker is OPEN for {func.__name__}")
        
        try:
            result = await func(*args, **kwargs)
            self._on_success()
            return result
        except Exception as e:
            self._on_failure()
            raise e
    
    def _should_attempt_reset(self) -> bool:
        return (
            self.last_failure_time and
            (datetime.now().timestamp() - self.last_failure_time) >= self.recovery_timeout
        )
    
    def _on_success(self):
        self.failure_count = 0
        self.state = 'CLOSED'
    
    def _on_failure(self):
        self.failure_count += 1
        self.last_failure_time = datetime.now().timestamp()
        if self.failure_count >= self.failure_threshold:
            self.state = 'OPEN'

class RateLimiter:
    """Token bucket rate limiter"""
    
    def __init__(self, rate: float):
        self.rate = rate  # requests per second
        self.tokens = rate
        self.last_update = asyncio.get_event_loop().time()
    
    async def acquire(self):
        now = asyncio.get_event_loop().time()
        # Add tokens based on elapsed time
        self.tokens = min(self.rate, self.tokens + (now - self.last_update) * self.rate)
        self.last_update = now
        
        if self.tokens >= 1:
            self.tokens -= 1
            return
        
        # Wait for next token
        wait_time = (1 - self.tokens) / self.rate
        await asyncio.sleep(wait_time)
        self.tokens = 0

class AsyncWebScraper:
    """Enhanced async web scraper with enterprise features"""
    
    def __init__(self, kafka_bootstrap_servers: str = None):
        self.session: Optional[aiohttp.ClientSession] = None
        self.kafka_producer: Optional[aiokafka.AIOKafkaProducer] = None
        self.sources: Dict[str, ScrapingConfig] = {}
        self.rate_limiters: Dict[str, RateLimiter] = {}
        self.circuit_breakers: Dict[str, CircuitBreaker] = {}
        self.kafka_bootstrap_servers = kafka_bootstrap_servers or os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
        
    async def __aenter__(self):
        await self.start()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.stop()
    
    async def start(self):
        """Initialize connections and producers"""
        # Create HTTP session with connection pooling
        connector = aiohttp.TCPConnector(
            limit=100,  # Total connection pool size
            limit_per_host=30,  # Per-host connection limit
            ttl_dns_cache=300,  # DNS cache TTL
            use_dns_cache=True,
        )
        
        timeout = aiohttp.ClientTimeout(total=30, connect=10)
        self.session = aiohttp.ClientSession(
            connector=connector,
            timeout=timeout,
            headers={'User-Agent': 'InvestorPipeline/1.0'}
        )
        
        # Initialize Kafka producer
        self.kafka_producer = aiokafka.AIOKafkaProducer(
            bootstrap_servers=self.kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            compression_type='gzip',
            batch_size=16384,
            linger_ms=10
        )
        await self.kafka_producer.start()
        
        logger.info("AsyncWebScraper started", 
                   kafka_servers=self.kafka_bootstrap_servers)
    
    async def stop(self):
        """Clean up connections"""
        if self.kafka_producer:
            await self.kafka_producer.stop()
        if self.session:
            await self.session.close()
        logger.info("AsyncWebScraper stopped")
    
    def add_source(self, config: ScrapingConfig):
        """Add a new scraping source"""
        self.sources[config.name] = config
        self.rate_limiters[config.name] = RateLimiter(config.rate_limit)
        self.circuit_breakers[config.name] = CircuitBreaker(config.circuit_breaker_threshold)
        
        logger.info("Added scraping source", 
                   source=config.name, 
                   rate_limit=config.rate_limit)
    
    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=4, max=10))
    async def _fetch_data(self, source_name: str, url: str, headers: Dict[str, str] = None) -> str:
        """Fetch data from a source with retries"""
        config = self.sources[source_name]
        
        # Apply rate limiting
        await self.rate_limiters[source_name].acquire()
        
        # Track active connections
        active_connections.labels(source=source_name).inc()
        
        try:
            with scraping_duration.labels(source=source_name).time():
                async with self.session.get(
                    url, 
                    headers=headers or config.headers,
                    timeout=aiohttp.ClientTimeout(total=config.timeout)
                ) as response:
                    response.raise_for_status()
                    content = await response.text()
                    
                    scraping_requests.labels(source=source_name, status='success').inc()
                    logger.info("Successfully scraped data", 
                               source=source_name, 
                               url=url,
                               response_size=len(content))
                    return content
                    
        except Exception as e:
            scraping_requests.labels(source=source_name, status='error').inc()
            logger.error("Failed to scrape data", 
                        source=source_name, 
                        url=url, 
                        error=str(e))
            raise
        finally:
            active_connections.labels(source=source_name).dec()
    
    async def scrape_source(self, source_name: str) -> List[StockData]:
        """Scrape data from a specific source"""
        if source_name not in self.sources:
            raise ValueError(f"Unknown source: {source_name}")
        
        config = self.sources[source_name]
        circuit_breaker = self.circuit_breakers[source_name]
        
        try:
            # Use circuit breaker pattern
            content = await circuit_breaker.call(
                self._fetch_data, 
                source_name, 
                config.url, 
                config.headers
            )
            
            # Parse the content (this would be source-specific)
            stock_data = await self._parse_stock_data(source_name, content)
            
            # Publish to Kafka
            if stock_data:
                await self._publish_to_kafka('stock-data', stock_data)
            
            return stock_data
            
        except Exception as e:
            logger.error("Source scraping failed", 
                        source=source_name, 
                        error=str(e))
            return []
    
    async def _parse_stock_data(self, source_name: str, content: str) -> List[StockData]:
        """Parse scraped content into structured data"""
        # This is a placeholder - would implement source-specific parsing
        # For now, return mock data for demonstration
        return [
            StockData(
                symbol="AAPL",
                price=150.25,
                volume=1000000,
                timestamp=datetime.now(),
                source=source_name,
                change_percent=1.5
            )
        ]
    
    async def _publish_to_kafka(self, topic: str, stock_data: List[StockData]):
        """Publish stock data to Kafka topic"""
        try:
            for data in stock_data:
                message = data.to_kafka_message()
                await self.kafka_producer.send(
                    topic,
                    key=data.symbol,
                    value=message
                )
            
            logger.info("Published data to Kafka", 
                       topic=topic, 
                       count=len(stock_data))
                       
        except Exception as e:
            logger.error("Failed to publish to Kafka", 
                        topic=topic, 
                        error=str(e))
            raise
    
    async def scrape_all_sources(self) -> Dict[str, List[StockData]]:
        """Scrape all configured sources concurrently"""
        tasks = [
            self.scrape_source(source_name) 
            for source_name in self.sources.keys()
        ]
        
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # Process results
        source_data = {}
        for i, (source_name, result) in enumerate(zip(self.sources.keys(), results)):
            if isinstance(result, Exception):
                logger.error("Source failed", source=source_name, error=str(result))
                source_data[source_name] = []
            else:
                source_data[source_name] = result
        
        return source_data

async def main():
    """Main scraping loop"""
    scraper_config = [
        ScrapingConfig(
            name="alpha_vantage",
            url="https://www.alphavantage.co/query",
            rate_limit=5.0,  # 5 requests per second
            headers={"X-API-Key": os.getenv("ALPHA_VANTAGE_API_KEY", "")}
        ),
        ScrapingConfig(
            name="yahoo_finance",
            url="https://finance.yahoo.com/quote/AAPL",
            rate_limit=2.0,  # 2 requests per second
        )
    ]
    
    async with AsyncWebScraper() as scraper:
        # Configure sources
        for config in scraper_config:
            scraper.add_source(config)
        
        # Main scraping loop
        while True:
            try:
                logger.info("Starting scraping cycle")
                results = await scraper.scrape_all_sources()
                
                total_data_points = sum(len(data) for data in results.values())
                logger.info("Scraping cycle completed", 
                           total_data_points=total_data_points)
                
                # Wait before next cycle
                await asyncio.sleep(60)  # 1-minute intervals
                
            except KeyboardInterrupt:
                logger.info("Scraping stopped by user")
                break
            except Exception as e:
                logger.error("Scraping cycle failed", error=str(e))
                await asyncio.sleep(30)  # Wait 30 seconds before retry

if __name__ == "__main__":
    asyncio.run(main())