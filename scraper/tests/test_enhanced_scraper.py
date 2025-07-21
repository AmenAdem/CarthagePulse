"""
Unit tests for enhanced scraper
"""
import pytest
import asyncio
import json
from unittest.mock import Mock, patch, AsyncMock
from scraper.src.enhanced_scraper import EnhancedAsyncScraper, StockDataParser
from scraper.src.circuit_breaker import AsyncCircuitBreaker, CircuitState
from scraper.src.kafka_producer import KafkaProducerManager, StockData


class TestCircuitBreaker:
    """Test circuit breaker functionality"""
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_opens_on_failures(self):
        """Test that circuit breaker opens after threshold failures"""
        breaker = AsyncCircuitBreaker(failure_threshold=2, timeout=1.0, name="test")
        
        # Test successful operation
        async with breaker:
            pass
        assert breaker.state == CircuitState.CLOSED
        
        # Simulate failures
        for _ in range(2):
            try:
                async with breaker:
                    raise Exception("Test failure")
            except Exception:
                pass
        
        assert breaker.state == CircuitState.OPEN
        
        # Should raise CircuitBreakerError when open
        with pytest.raises(Exception):  # CircuitBreakerError
            async with breaker:
                pass

    @pytest.mark.asyncio
    async def test_circuit_breaker_half_open_transition(self):
        """Test circuit breaker transitions to half-open after timeout"""
        breaker = AsyncCircuitBreaker(failure_threshold=1, timeout=0.1, name="test")
        
        # Force open state
        try:
            async with breaker:
                raise Exception("Test failure")
        except Exception:
            pass
        
        assert breaker.state == CircuitState.OPEN
        
        # Wait for timeout
        await asyncio.sleep(0.2)
        
        # Should transition to half-open on next success
        async with breaker:
            pass
        
        assert breaker.state == CircuitState.CLOSED


class TestStockDataParser:
    """Test stock data parsing functionality"""
    
    def test_parse_valid_stock_data(self):
        """Test parsing valid stock data JSON"""
        parser = StockDataParser()
        test_data = {
            "symbol": "AAPL",
            "price": 150.50,
            "volume": 1000000,
            "timestamp": 1640995200,  # 2022-01-01 00:00:00 UTC
            "change": 2.50,
            "change_percent": 1.69,
            "source": "test"
        }
        
        json_string = json.dumps(test_data)
        result = parser.map(json_string)
        
        assert result.symbol == "AAPL"
        assert result.price.amount == 150.50
        assert result.volume == 1000000
        assert result.source == "test"

    def test_parse_invalid_json_raises_exception(self):
        """Test that invalid JSON raises exception"""
        parser = StockDataParser()
        
        with pytest.raises(Exception):
            parser.map("invalid json")


class TestKafkaProducerManager:
    """Test Kafka producer functionality"""
    
    def test_stock_data_creation(self):
        """Test StockData model creation"""
        stock_data = StockData(
            symbol="AAPL",
            price=150.50,
            volume=1000000,
            timestamp=1640995200,
            change=2.50,
            change_percent=1.69,
            source="test"
        )
        
        assert stock_data.symbol == "AAPL"
        assert stock_data.price == 150.50
        assert stock_data.volume == 1000000

    @patch('scraper.src.kafka_producer.Producer')
    def test_kafka_producer_initialization(self, mock_producer):
        """Test Kafka producer initialization"""
        config = {
            'kafka': {
                'bootstrap_servers': 'localhost:9092',
                'topics': {
                    'stock_data': 'test-topic'
                }
            }
        }
        
        producer_manager = KafkaProducerManager(config)
        assert producer_manager.topics['stock_data'] == 'test-topic'


@pytest.mark.asyncio
class TestEnhancedAsyncScraper:
    """Test main scraper functionality"""
    
    @patch('scraper.src.enhanced_scraper.aiohttp.ClientSession')
    async def test_fetch_with_resilience(self, mock_session):
        """Test fetching data with circuit breaker and rate limiting"""
        # Setup mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.text = AsyncMock(return_value='{"test": "data"}')
        
        mock_session_instance = Mock()
        mock_session_instance.get.return_value.__aenter__.return_value = mock_response
        mock_session.return_value = mock_session_instance
        
        # Create scraper with test config
        scraper = EnhancedAsyncScraper()
        scraper.session = mock_session_instance
        
        # Test successful fetch
        result = await scraper._fetch_with_resilience("http://test.com", "test_source")
        
        assert result == '{"test": "data"}'

    def test_load_default_config(self):
        """Test loading default configuration"""
        with patch('builtins.open', side_effect=FileNotFoundError):
            scraper = EnhancedAsyncScraper()
            config = scraper.config
            
            assert 'scraper' in config
            assert 'kafka' in config
            assert config['scraper']['user_agent'] == 'StockTracker-Scraper/1.0'

    @patch('scraper.src.enhanced_scraper.start_http_server')
    @patch('scraper.src.enhanced_scraper.aiohttp.ClientSession')
    async def test_scraper_start_and_stop(self, mock_session, mock_http_server):
        """Test scraper start and stop functionality"""
        scraper = EnhancedAsyncScraper()
        
        # Test start
        await scraper.start()
        assert scraper.session is not None
        assert scraper._running is True
        
        # Test stop
        await scraper.stop()
        assert scraper._running is False

    @patch('scraper.src.enhanced_scraper.aiohttp.ClientSession')
    async def test_health_check(self, mock_session):
        """Test health check functionality"""
        scraper = EnhancedAsyncScraper()
        scraper.session = mock_session
        
        # Mock Kafka producer
        mock_producer = Mock()
        mock_producer.is_connected = True
        scraper.kafka_producer = mock_producer
        
        health = await scraper.health_check()
        
        assert health['status'] == 'healthy'
        assert 'components' in health
        assert 'timestamp' in health


# Integration test fixtures
@pytest.fixture
def test_config():
    """Test configuration fixture"""
    return {
        'scraper': {
            'user_agent': 'Test-Scraper/1.0',
            'concurrent_limit': 2,
            'timeout': 10,
            'stock_symbols': ['AAPL', 'GOOGL']
        },
        'kafka': {
            'bootstrap_servers': 'localhost:9092',
            'topics': {
                'stock_data': 'test-stock-data',
                'news_data': 'test-news-data'
            }
        }
    }


@pytest.fixture
def mock_kafka_producer():
    """Mock Kafka producer fixture"""
    producer = Mock()
    producer.is_connected = True
    producer.produce_stock_data = AsyncMock(return_value=True)
    producer.produce_news_data = AsyncMock(return_value=True)
    return producer


if __name__ == "__main__":
    pytest.main([__file__])