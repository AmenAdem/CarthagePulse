import pytest
import asyncio
import aioresponses
from unittest.mock import Mock, AsyncMock, patch
from datetime import datetime
import json

from src.async_scraper import (
    AsyncWebScraper, 
    ScrapingConfig, 
    StockData, 
    CircuitBreaker, 
    RateLimiter
)

@pytest.fixture
def scraper_config():
    return ScrapingConfig(
        name="test_source",
        url="https://api.test.com/data",
        rate_limit=10.0,
        timeout=30,
        headers={"Authorization": "Bearer test_token"}
    )

@pytest.fixture
def sample_stock_data():
    return {
        "symbol": "AAPL",
        "price": 150.25,
        "volume": 1000000,
        "timestamp": datetime.now().isoformat(),
        "source": "test_source",
        "change_percent": 1.5
    }

@pytest.mark.asyncio
class TestAsyncWebScraper:
    
    async def test_scraper_initialization(self):
        async with AsyncWebScraper() as scraper:
            assert scraper.session is not None
            assert scraper.kafka_producer is not None
            assert len(scraper.sources) == 0
    
    async def test_add_source(self, scraper_config):
        async with AsyncWebScraper() as scraper:
            scraper.add_source(scraper_config)
            
            assert scraper_config.name in scraper.sources
            assert scraper_config.name in scraper.rate_limiters
            assert scraper_config.name in scraper.circuit_breakers
    
    @patch('aiokafka.AIOKafkaProducer')
    async def test_fetch_data_success(self, mock_producer, scraper_config):
        with aioresponses.aioresponses() as m:
            m.get(scraper_config.url, payload={"data": "test"})
            
            async with AsyncWebScraper() as scraper:
                scraper.add_source(scraper_config)
                
                result = await scraper._fetch_data(
                    scraper_config.name, 
                    scraper_config.url
                )
                
                assert result is not None
                assert "test" in result
    
    @patch('aiokafka.AIOKafkaProducer')
    async def test_fetch_data_with_retry(self, mock_producer, scraper_config):
        with aioresponses.aioresponses() as m:
            # First request fails, second succeeds
            m.get(scraper_config.url, status=500)
            m.get(scraper_config.url, payload={"data": "test"})
            
            async with AsyncWebScraper() as scraper:
                scraper.add_source(scraper_config)
                
                result = await scraper._fetch_data(
                    scraper_config.name, 
                    scraper_config.url
                )
                
                assert result is not None
    
    @patch('aiokafka.AIOKafkaProducer')
    async def test_parse_stock_data(self, mock_producer, sample_stock_data):
        async with AsyncWebScraper() as scraper:
            parsed_data = await scraper._parse_stock_data("test_source", json.dumps(sample_stock_data))
            
            assert len(parsed_data) > 0
            stock_data = parsed_data[0]
            assert isinstance(stock_data, StockData)
    
    @patch('aiokafka.AIOKafkaProducer')
    async def test_publish_to_kafka(self, mock_producer, sample_stock_data):
        mock_producer_instance = Mock()
        mock_producer_instance.send = AsyncMock()
        mock_producer.return_value = mock_producer_instance
        
        async with AsyncWebScraper() as scraper:
            scraper.kafka_producer = mock_producer_instance
            
            stock_data = [StockData(
                symbol=sample_stock_data["symbol"],
                price=sample_stock_data["price"],
                volume=sample_stock_data["volume"],
                timestamp=datetime.now(),
                source=sample_stock_data["source"]
            )]
            
            await scraper._publish_to_kafka("test-topic", stock_data)
            
            mock_producer_instance.send.assert_called_once()

class TestCircuitBreaker:
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_closed_state(self):
        circuit_breaker = CircuitBreaker(failure_threshold=2)
        
        async def successful_function():
            return "success"
        
        result = await circuit_breaker.call(successful_function)
        assert result == "success"
        assert circuit_breaker.state == "CLOSED"
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_open_state(self):
        circuit_breaker = CircuitBreaker(failure_threshold=2)
        
        async def failing_function():
            raise Exception("Test failure")
        
        # First failure
        with pytest.raises(Exception):
            await circuit_breaker.call(failing_function)
        
        # Second failure - should open circuit
        with pytest.raises(Exception):
            await circuit_breaker.call(failing_function)
        
        assert circuit_breaker.state == "OPEN"
        
        # Third call should be rejected immediately
        with pytest.raises(Exception, match="Circuit breaker is OPEN"):
            await circuit_breaker.call(failing_function)

class TestRateLimiter:
    
    @pytest.mark.asyncio
    async def test_rate_limiter_allows_initial_requests(self):
        rate_limiter = RateLimiter(rate=10.0)  # 10 requests per second
        
        start_time = asyncio.get_event_loop().time()
        await rate_limiter.acquire()
        end_time = asyncio.get_event_loop().time()
        
        # Should not wait for the first request
        assert (end_time - start_time) < 0.1
    
    @pytest.mark.asyncio
    async def test_rate_limiter_enforces_limit(self):
        rate_limiter = RateLimiter(rate=2.0)  # 2 requests per second
        
        # First request should be immediate
        start_time = asyncio.get_event_loop().time()
        await rate_limiter.acquire()
        await rate_limiter.acquire()
        
        # Third request should be delayed
        await rate_limiter.acquire()
        end_time = asyncio.get_event_loop().time()
        
        # Should have waited approximately 0.5 seconds (1/2 rate)
        duration = end_time - start_time
        assert duration >= 0.4  # Allow some tolerance

class TestStockData:
    
    def test_stock_data_creation(self):
        stock_data = StockData(
            symbol="AAPL",
            price=150.25,
            volume=1000000,
            timestamp=datetime.now(),
            source="test_source"
        )
        
        assert stock_data.symbol == "AAPL"
        assert stock_data.price == 150.25
        assert stock_data.volume == 1000000
        assert stock_data.source == "test_source"
    
    def test_stock_data_to_kafka_message(self):
        now = datetime.now()
        stock_data = StockData(
            symbol="AAPL",
            price=150.25,
            volume=1000000,
            timestamp=now,
            source="test_source",
            change_percent=1.5
        )
        
        message = stock_data.to_kafka_message()
        
        assert message["symbol"] == "AAPL"
        assert message["price"] == 150.25
        assert message["volume"] == 1000000
        assert message["source"] == "test_source"
        assert message["change_percent"] == 1.5
        assert message["timestamp"] == now.isoformat()

@pytest.mark.integration
class TestIntegrationScenarios:
    
    @pytest.mark.asyncio
    @patch('aiokafka.AIOKafkaProducer')
    async def test_complete_scraping_workflow(self, mock_producer, scraper_config, sample_stock_data):
        mock_producer_instance = Mock()
        mock_producer_instance.send = AsyncMock()
        mock_producer_instance.start = AsyncMock()
        mock_producer_instance.stop = AsyncMock()
        mock_producer.return_value = mock_producer_instance
        
        with aioresponses.aioresponses() as m:
            m.get(scraper_config.url, payload=sample_stock_data)
            
            async with AsyncWebScraper() as scraper:
                scraper.kafka_producer = mock_producer_instance
                scraper.add_source(scraper_config)
                
                # Override the parse method to return actual StockData
                async def mock_parse(source_name, content):
                    data = json.loads(content)
                    return [StockData(
                        symbol=data["symbol"],
                        price=data["price"],
                        volume=data["volume"],
                        timestamp=datetime.now(),
                        source=source_name,
                        change_percent=data.get("change_percent")
                    )]
                
                scraper._parse_stock_data = mock_parse
                
                result = await scraper.scrape_source(scraper_config.name)
                
                assert len(result) == 1
                assert result[0].symbol == "AAPL"
                mock_producer_instance.send.assert_called()
    
    @pytest.mark.asyncio
    @patch('aiokafka.AIOKafkaProducer')
    async def test_multiple_sources_concurrent_scraping(self, mock_producer):
        configs = [
            ScrapingConfig("source1", "https://api1.test.com", 5.0),
            ScrapingConfig("source2", "https://api2.test.com", 5.0)
        ]
        
        mock_producer_instance = Mock()
        mock_producer_instance.send = AsyncMock()
        mock_producer_instance.start = AsyncMock()
        mock_producer_instance.stop = AsyncMock()
        mock_producer.return_value = mock_producer_instance
        
        with aioresponses.aioresponses() as m:
            m.get("https://api1.test.com", payload={"symbol": "AAPL", "price": 150})
            m.get("https://api2.test.com", payload={"symbol": "GOOGL", "price": 2750})
            
            async with AsyncWebScraper() as scraper:
                scraper.kafka_producer = mock_producer_instance
                
                for config in configs:
                    scraper.add_source(config)
                
                # Override parse method
                async def mock_parse(source_name, content):
                    data = json.loads(content)
                    return [StockData(
                        symbol=data["symbol"],
                        price=data["price"],
                        volume=1000000,
                        timestamp=datetime.now(),
                        source=source_name
                    )]
                
                scraper._parse_stock_data = mock_parse
                
                results = await scraper.scrape_all_sources()
                
                assert len(results) == 2
                assert "source1" in results
                assert "source2" in results