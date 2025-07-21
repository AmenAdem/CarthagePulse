# Real-Time Investor Updates Pipeline - Enterprise Edition

A comprehensive, enterprise-grade real-time data pipeline utilizing Python async scraping, Spring Boot microservices, Apache Flink stream processing, and modern infrastructure to provide investors with intelligent, real-time market updates.

## Enterprise Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│   Data Sources  │───▶│  Python Scraper  │───▶│  Kafka Topics   │───▶│ Apache Flink │
│  (APIs/Websites)│    │   (Enhanced)     │    │                 │    │  Processing  │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
                                                                              │
                                                                              ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│   Frontend      │◀───│  API Gateway     │◀───│   PostgreSQL    │◀───│    Data      │
│   (React.js)    │    │  (Spring Boot)   │    │   Database      │    │   Consumer   │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │   Notification   │
                       │   Microservice   │
                       │  (Spring Boot)   │
                       └──────────────────┘
```

## Enterprise Features

- **Enhanced Async Web Scraping**: High-performance concurrent scraping with rate limiting and circuit breakers
- **Spring Boot Microservices**: Enterprise-grade microservices with OAuth2/JWT security
- **Apache Flink Stream Processing**: Real-time data transformation with complex event processing
- **PostgreSQL Time-series Optimization**: Partitioned tables with performance indexing
- **Comprehensive Monitoring**: Prometheus metrics with Grafana dashboards
- **Kubernetes-Ready**: Production deployment with auto-scaling and load balancing
- **Multi-channel Notifications**: Email, SMS, WebSocket, and push notifications
- **Security & Compliance**: Complete audit logging and data protection
- **Fault Tolerance**: Circuit breakers, retry mechanisms, and health checks

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Python 3.11+
- Maven 3.8+

### Development Setup
```bash
# Clone the repository
git clone https://github.com/AmenAdem/Real-Time-Investor-Updates-Pipeline.git
cd Real-Time-Investor-Updates-Pipeline

# Start the complete infrastructure
docker-compose -f infrastructure/docker-compose.yml up -d

# Wait for all services to be healthy (may take 2-3 minutes)
docker-compose -f infrastructure/docker-compose.yml ps

# Access the services
# API Gateway: http://localhost:8000
# Kafka UI: http://localhost:8080
# Flink Dashboard: http://localhost:8082
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

### Production Deployment
```bash
# Deploy to Kubernetes
kubectl create namespace investor-pipeline
kubectl apply -f infrastructure/kubernetes/

# Monitor deployment
kubectl get pods -n investor-pipeline
```

## Project Components

### 1. Enhanced Python Web Scraper (`/scraper`)
- **Async Architecture**: Uses asyncio and aiohttp for high-performance concurrent scraping
- **Rate Limiting**: Token bucket algorithm with per-source configuration
- **Circuit Breaker**: Automatic fault tolerance with configurable thresholds
- **Data Validation**: Pydantic schema validation before Kafka publishing
- **Monitoring**: Prometheus metrics and structured logging

### 2. Spring Boot Microservices (`/microservices`)
- **API Gateway**: OAuth2/JWT authentication with request routing and rate limiting
- **Data Consumer**: Kafka consumer with PostgreSQL integration and WebSocket support
- **Notification Service**: Multi-channel delivery (email, SMS, push, WebSocket)
- **User Management**: Complete user lifecycle with preferences and watchlists

### 3. Apache Flink Stream Processing (`/flink-jobs`)
- **Real-time Processing**: Enhanced stream processing with windowing and aggregations
- **Data Enrichment**: Price change calculations and moving averages
- **Alert Generation**: Intelligent alerting for significant price movements
- **State Management**: RocksDB backend with checkpointing for fault tolerance

### 4. PostgreSQL Database (`/database`)
- **Time-series Optimization**: Monthly partitioned tables for efficient storage
- **Performance Indexes**: Optimized indexing for real-time queries
- **User Management**: Comprehensive user data with preferences
- **Audit Logging**: Complete audit trail for compliance

### 5. Infrastructure & Monitoring (`/infrastructure`)
- **Docker Compose**: Complete development environment
- **Kubernetes**: Production-ready deployment manifests
- **Prometheus & Grafana**: Comprehensive monitoring and visualization
- **Redis**: Caching and session management

## Environment Variables

```bash
# Database Configuration
DATABASE_URL=postgresql://investor_user:investor_pass@localhost:5432/investorpipeline
REDIS_URL=redis://localhost:6379

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081

# External API Keys
ALPHA_VANTAGE_API_KEY=your_api_key
NEWS_API_KEY=your_news_api_key
FINNHUB_API_KEY=your_finnhub_key

# Notification Services
SMTP_HOST=smtp.gmail.com
SMTP_USER=your_email
SMTP_PASS=your_app_password
TWILIO_SID=your_twilio_sid
TWILIO_TOKEN=your_twilio_token

# Security Configuration
JWT_SECRET=your_jwt_secret_key
OAUTH2_CLIENT_ID=your_oauth2_client_id
OAUTH2_CLIENT_SECRET=your_oauth2_client_secret

# Monitoring
PROMETHEUS_ENABLED=true
GRAFANA_ADMIN_PASSWORD=secure_password
```

## Testing

### Python Scraper Tests
```bash
cd scraper
pip install -r requirements.txt
pytest test_async_scraper.py -v --cov=src
```

### Spring Boot Service Tests
```bash
cd microservices/api-gateway
mvn test

cd ../data-consumer
mvn test
```

### Apache Flink Job Tests
```bash
cd flink-jobs
mvn test
```

### Integration Tests
```bash
# Start test environment
docker-compose -f infrastructure/docker-compose.yml up -d

# Run integration tests
python scripts/run_integration_tests.py
```

## Documentation

- [Enterprise Architecture Guide](docs/ARCHITECTURE.md) - Comprehensive architecture documentation
- [API Reference](docs/api.md) - Complete API documentation with examples
- [Deployment Guide](docs/deployment.md) - Production deployment instructions
- [Development Guide](docs/development.md) - Developer setup and contribution guidelines
- [Security Guidelines](docs/security.md) - Security best practices and compliance
- [Performance Tuning](docs/performance.md) - Optimization and scaling guidelines
- [Monitoring & Alerting](docs/monitoring.md) - Observability and alerting setup

## Performance Characteristics

### Throughput
- **Python Scraper**: 1,000+ requests/minute per source with intelligent rate limiting
- **Apache Flink**: 10,000+ messages/second processing capacity
- **PostgreSQL**: Optimized for 100,000+ inserts/second with partitioning
- **API Gateway**: 1,000+ concurrent users with Redis caching

### Latency
- **End-to-end Pipeline**: <500ms from data source to user notification
- **API Response Times**: <100ms for cached data, <1s for database queries
- **WebSocket Updates**: Real-time updates with <50ms latency

### Scalability
- **Horizontal Scaling**: All services designed for Kubernetes auto-scaling
- **Data Partitioning**: Time-series partitioning for efficient data management
- **Load Balancing**: Built-in load balancing with health checks

## Production Deployment Considerations

### Infrastructure Requirements
- **Kubernetes Cluster**: Minimum 3 nodes with 16GB RAM each
- **PostgreSQL**: Dedicated database server with SSD storage
- **Kafka Cluster**: 3-node cluster for high availability
- **Redis Cluster**: For session management and caching
- **Load Balancer**: For API Gateway with SSL termination

### Security Checklist
- [ ] SSL/TLS certificates configured
- [ ] OAuth2/JWT authentication enabled
- [ ] Database credentials secured in Kubernetes secrets
- [ ] API rate limiting configured
- [ ] Network policies applied
- [ ] Audit logging enabled

### Monitoring Setup
- [ ] Prometheus metrics collection
- [ ] Grafana dashboards configured
- [ ] Alert rules for critical metrics
- [ ] Log aggregation with ELK stack
- [ ] Distributed tracing with Jaeger

## Future Enhancements

- Machine Learning integration for predictive analysis
- Social media sentiment analysis
- Advanced analytics and reporting
- Mobile application
- Real-time collaboration features

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
