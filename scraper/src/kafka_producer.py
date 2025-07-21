"""
Kafka Producer with Schema Registry integration
Handles publishing of scraped data to Kafka topics with Avro serialization
"""
import asyncio
import json
import time
from typing import Dict, Any, Optional, List
from confluent_kafka import Producer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import SerializationContext, MessageField
import structlog
from prometheus_client import Counter, Histogram, Gauge
from pydantic import BaseModel

logger = structlog.get_logger()

# Prometheus metrics
MESSAGES_PRODUCED = Counter(
    'kafka_messages_produced_total',
    'Total number of messages produced to Kafka',
    ['topic', 'status']
)

PRODUCE_DURATION = Histogram(
    'kafka_produce_duration_seconds',
    'Time spent producing messages to Kafka',
    ['topic']
)

KAFKA_CONNECTION_STATUS = Gauge(
    'kafka_connection_status',
    'Kafka connection status (1=connected, 0=disconnected)'
)


class StockData(BaseModel):
    symbol: str
    price: float
    volume: int
    timestamp: int
    change: float
    change_percent: float
    market_cap: Optional[float] = None
    pe_ratio: Optional[float] = None
    source: str


class NewsData(BaseModel):
    title: str
    content: str
    url: str
    timestamp: int
    source: str
    sentiment_score: Optional[float] = None
    symbols: List[str] = []
    category: str


class EconomicData(BaseModel):
    indicator: str
    value: float
    timestamp: int
    country: str
    source: str
    unit: str
    period: str


class KafkaProducerManager:
    """
    Async Kafka producer with Schema Registry integration
    """
    
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.producer: Optional[Producer] = None
        self.schema_registry_client: Optional[SchemaRegistryClient] = None
        self.serializers: Dict[str, AvroSerializer] = {}
        self._is_connected = False
        
        # Kafka topics
        self.topics = {
            'stock_data': config.get('kafka', {}).get('topics', {}).get('stock_data', 'stock-data'),
            'news_data': config.get('kafka', {}).get('topics', {}).get('news_data', 'news-data'),
            'economic_data': config.get('kafka', {}).get('topics', {}).get('economic_data', 'economic-data')
        }
        
        self._setup_producer()
        self._setup_schema_registry()
    
    def _setup_producer(self) -> None:
        """
        Initialize Kafka producer
        """
        kafka_config = self.config.get('kafka', {})
        
        producer_config = {
            'bootstrap.servers': kafka_config.get('bootstrap_servers', 'localhost:9092'),
            'client.id': kafka_config.get('client_id', 'stock-scraper'),
            'acks': kafka_config.get('acks', 'all'),
            'retries': kafka_config.get('retries', 3),
            'batch.size': kafka_config.get('batch_size', 16384),
            'linger.ms': kafka_config.get('linger_ms', 10),
            'compression.type': kafka_config.get('compression_type', 'snappy'),
            'max.in.flight.requests.per.connection': kafka_config.get('max_in_flight', 5),
            'enable.idempotence': kafka_config.get('enable_idempotence', True),
        }
        
        # Add security configuration if provided
        security_config = kafka_config.get('security', {})
        if security_config:
            producer_config.update(security_config)
        
        try:
            self.producer = Producer(producer_config)
            self._is_connected = True
            KAFKA_CONNECTION_STATUS.set(1)
            logger.info("Kafka producer initialized successfully")
        except Exception as e:
            self._is_connected = False
            KAFKA_CONNECTION_STATUS.set(0)
            logger.error("Failed to initialize Kafka producer", error=str(e))
            raise
    
    def _setup_schema_registry(self) -> None:
        """
        Initialize Schema Registry client and serializers
        """
        schema_registry_config = self.config.get('schema_registry', {})
        
        if not schema_registry_config.get('url'):
            logger.warning("Schema Registry URL not provided, using JSON serialization")
            return
        
        try:
            self.schema_registry_client = SchemaRegistryClient({
                'url': schema_registry_config['url'],
                **schema_registry_config.get('auth', {})
            })
            
            # Setup Avro serializers for each data type
            self._setup_avro_serializers()
            
            logger.info("Schema Registry client initialized successfully")
        except Exception as e:
            logger.error("Failed to initialize Schema Registry client", error=str(e))
            # Continue without schema registry - will use JSON serialization
    
    def _setup_avro_serializers(self) -> None:
        """
        Setup Avro serializers for different data types
        """
        schemas = {
            'stock_data': {
                "type": "record",
                "name": "StockData",
                "fields": [
                    {"name": "symbol", "type": "string"},
                    {"name": "price", "type": "double"},
                    {"name": "volume", "type": "long"},
                    {"name": "timestamp", "type": "long"},
                    {"name": "change", "type": "double"},
                    {"name": "change_percent", "type": "double"},
                    {"name": "market_cap", "type": ["null", "double"], "default": None},
                    {"name": "pe_ratio", "type": ["null", "double"], "default": None},
                    {"name": "source", "type": "string"}
                ]
            },
            'news_data': {
                "type": "record",
                "name": "NewsData",
                "fields": [
                    {"name": "title", "type": "string"},
                    {"name": "content", "type": "string"},
                    {"name": "url", "type": "string"},
                    {"name": "timestamp", "type": "long"},
                    {"name": "source", "type": "string"},
                    {"name": "sentiment_score", "type": ["null", "double"], "default": None},
                    {"name": "symbols", "type": {"type": "array", "items": "string"}},
                    {"name": "category", "type": "string"}
                ]
            },
            'economic_data': {
                "type": "record",
                "name": "EconomicData",
                "fields": [
                    {"name": "indicator", "type": "string"},
                    {"name": "value", "type": "double"},
                    {"name": "timestamp", "type": "long"},
                    {"name": "country", "type": "string"},
                    {"name": "source", "type": "string"},
                    {"name": "unit", "type": "string"},
                    {"name": "period", "type": "string"}
                ]
            }
        }
        
        for data_type, schema in schemas.items():
            try:
                self.serializers[data_type] = AvroSerializer(
                    self.schema_registry_client,
                    json.dumps(schema),
                    lambda obj, ctx: obj if isinstance(obj, dict) else obj.dict()
                )
                logger.info(f"Avro serializer setup for {data_type}")
            except Exception as e:
                logger.error(f"Failed to setup Avro serializer for {data_type}", error=str(e))
    
    async def produce_stock_data(self, data: StockData) -> bool:
        """
        Produce stock data to Kafka
        """
        return await self._produce_message(
            topic=self.topics['stock_data'],
            data=data,
            data_type='stock_data'
        )
    
    async def produce_news_data(self, data: NewsData) -> bool:
        """
        Produce news data to Kafka
        """
        return await self._produce_message(
            topic=self.topics['news_data'],
            data=data,
            data_type='news_data'
        )
    
    async def produce_economic_data(self, data: EconomicData) -> bool:
        """
        Produce economic data to Kafka
        """
        return await self._produce_message(
            topic=self.topics['economic_data'],
            data=data,
            data_type='economic_data'
        )
    
    async def _produce_message(
        self,
        topic: str,
        data: BaseModel,
        data_type: str,
        key: Optional[str] = None
    ) -> bool:
        """
        Produce a message to Kafka with proper serialization
        """
        if not self._is_connected or not self.producer:
            logger.error("Kafka producer not connected")
            MESSAGES_PRODUCED.labels(topic=topic, status='error').inc()
            return False
        
        try:
            with PRODUCE_DURATION.labels(topic=topic).time():
                # Serialize the message
                if self.schema_registry_client and data_type in self.serializers:
                    # Use Avro serialization
                    serializer = self.serializers[data_type]
                    value = serializer(
                        data.dict(),
                        SerializationContext(topic, MessageField.VALUE)
                    )
                else:
                    # Use JSON serialization
                    value = json.dumps(data.dict(), default=str).encode('utf-8')
                
                # Generate key if not provided
                if not key:
                    if hasattr(data, 'symbol'):
                        key = data.symbol
                    elif hasattr(data, 'indicator'):
                        key = data.indicator
                    else:
                        key = f"{data_type}_{int(time.time())}"
                
                # Produce the message
                await asyncio.get_event_loop().run_in_executor(
                    None,
                    lambda: self.producer.produce(
                        topic=topic,
                        key=key.encode('utf-8') if key else None,
                        value=value,
                        callback=self._delivery_callback
                    )
                )
                
                # Trigger delivery
                await asyncio.get_event_loop().run_in_executor(
                    None,
                    lambda: self.producer.poll(0)
                )
                
                MESSAGES_PRODUCED.labels(topic=topic, status='success').inc()
                logger.debug(f"Message produced to {topic}", key=key, data_type=data_type)
                return True
                
        except Exception as e:
            MESSAGES_PRODUCED.labels(topic=topic, status='error').inc()
            logger.error(f"Failed to produce message to {topic}", error=str(e))
            return False
    
    def _delivery_callback(self, err, msg):
        """
        Callback for message delivery confirmation
        """
        if err:
            logger.error("Message delivery failed", error=str(err))
        else:
            logger.debug(
                "Message delivered",
                topic=msg.topic(),
                partition=msg.partition(),
                offset=msg.offset()
            )
    
    async def flush(self, timeout: float = 30.0) -> None:
        """
        Flush all pending messages
        """
        if self.producer:
            await asyncio.get_event_loop().run_in_executor(
                None,
                lambda: self.producer.flush(timeout)
            )
    
    async def close(self) -> None:
        """
        Close the producer and cleanup resources
        """
        if self.producer:
            await self.flush()
            self.producer = None
            self._is_connected = False
            KAFKA_CONNECTION_STATUS.set(0)
            logger.info("Kafka producer closed")
    
    @property
    def is_connected(self) -> bool:
        return self._is_connected