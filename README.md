# Market Stream Decision Engine

A comprehensive real-time data pipeline built with modern microservices architecture, featuring Spring Boot services, enhanced Python scrapers, Apache Flink stream processing, and complete monitoring infrastructure.

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────────┐
│   Data Sources  │───▶│ Enhanced Python  │───▶│  Kafka Cluster  │───▶│ Apache Flink     │
│  • Alpha Vantage│    │    Scraper       │    │ + Schema Registry│    │ Stream Processing│
│  • News APIs    │    │ • Async/Circuit  │    │                 │    │ • CEP Patterns   │
│  • FRED Economic│    │   Breaker        │    │                 │    │ • Aggregations   │
└─────────────────┘    │ • Rate Limiting  │    └─────────────────┘    │ • Alerts         │
                       └──────────────────┘                           └──────────────────┘
                                │                                               │
                                ▼                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              Spring Boot Microservices                                 │
│  ┌───────────────┐  ┌─────────────────┐  ┌──────────────────┐  ┌─────────────────┐    │
│  │ API Gateway   │  │ Data Consumer   │  │ Notification     │  │ User Service    │    │
│  │ • Routing     │  │ • Kafka Consumer│  │ • Email/SMS      │  │ • Auth/JWT      │    │
│  │ • Auth Filter │  │ • WebSocket     │  │ • Push Notifs    │  │ • User Mgmt     │    │
│  │ • Rate Limit  │  │ • Time-series DB│  │ • Templates      │  │ • Watchlists    │    │
│  └───────────────┘  └─────────────────┘  └──────────────────┘  └─────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                │                                               │
                                ▼                                               ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────────┐
│ PostgreSQL +    │    │ Redis Cache +    │    │ Prometheus +    │    │ React Dashboard  │
│ TimescaleDB     │    │ Session Store    │    │ Grafana         │    │ • Real-time UI   │
│ • Time-series   │    │                  │    │ • Monitoring    │    │ • WebSocket      │
│ • Partitioning  │    │                  │    │ • Alerting      │    │ • Charts/Graphs  │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────────┘
```

## 🚀 Features

### Enhanced Python Scraper
- **Async/Await Architecture**: High-performance data collection with aiohttp
- **Circuit Breaker Pattern**: Resilient API calls with automatic fallback
- **Rate Limiting**: Configurable per-source request throttling  
- **Prometheus Metrics**: Real-time monitoring and alerting
- **Kafka Integration**: Schema Registry with Avro serialization
- **Multi-source Support**: Alpha Vantage, News API, FRED Economic data

### Spring Boot Microservices
- **API Gateway**: Centralized routing, authentication, and rate limiting
- **Data Consumer**: Kafka stream processing with PostgreSQL/TimescaleDB storage
- **Notification Service**: Multi-channel alerts (Email, SMS, Push) with templating
- **User Service**: JWT authentication, user management, watchlists
- **Circuit Breakers**: Resilience4j fault tolerance
- **Actuator Endpoints**: Health checks and metrics

### Apache Flink Stream Processing
- **Real-time Analytics**: Moving averages, price change detection
- **Complex Event Processing (CEP)**: Pattern matching for trading signals
- **Volume Anomaly Detection**: Statistical analysis for unusual activity
- **Watermark Processing**: Handling late-arriving data
- **Savepoints**: Fault tolerance and state management

### Database & Storage
- **PostgreSQL + TimescaleDB**: Optimized time-series data storage
- **Automatic Partitioning**: Efficient data retention and querying
- **Redis Caching**: Session management and data caching
- **Connection Pooling**: HikariCP for optimal database performance

### Monitoring & Operations
- **Prometheus Metrics**: Comprehensive application and business metrics
- **Grafana Dashboards**: Real-time visualization and alerting
- **Health Checks**: Service health monitoring with automatic recovery
- **Structured Logging**: JSON logs with correlation IDs
- **Docker Compose**: Complete development environment

## 📋 Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Node.js 16+ (for frontend development)
- Python 3.8+ (for scraper development)

## ⚡ Quick Start

### 1. Clone and Setup
```bash
git clone https://github.com/AmenAdem/Real-Time-Investor-Updates-Pipeline.git
cd Real-Time-Investor-Updates-Pipeline

# Run setup script
./scripts/setup-dev.sh
```

### 2. Configure API Keys
Edit `.env` file with your actual API keys:
```env
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_key
NEWS_API_KEY=your_news_api_key
FRED_API_KEY=your_fred_api_key
JWT_SECRET=your_jwt_secret_key
```

### 3. Start the Infrastructure
```bash
# Start all services
docker-compose up -d

# Check service health
docker-compose ps

# View logs
docker-compose logs -f [service_name]
```

### 4. Access Applications
- **API Gateway**: http://localhost:8080
- **Grafana Dashboard**: http://localhost:3001 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Flink Web UI**: http://localhost:8084
- **Scraper Metrics**: http://localhost:8000/metrics

## 🔧 Component Details

### 1. Enhanced Python Scraper (`/scraper`)
- **Async Architecture**: aiohttp-based concurrent data collection
- **Circuit Breaker**: Fault tolerance with configurable thresholds
- **Rate Limiting**: Per-source request throttling
- **Kafka Producer**: Schema Registry integration with Avro
- **Metrics**: Prometheus endpoint on port 8000
- **Configuration**: YAML-based with environment overrides

### 2. API Gateway (`/microservices/api-gateway`)
- **Spring Cloud Gateway**: Reactive routing and filtering
- **JWT Authentication**: Token validation and user context injection
- **Rate Limiting**: Redis-backed request throttling
- **Circuit Breakers**: Resilience4j fault tolerance
- **CORS Configuration**: Frontend integration support

### 3. Data Consumer Service (`/microservices/data-consumer`)
- **Kafka Consumer**: Multi-topic processing with manual acknowledgment
- **TimescaleDB Integration**: Optimized time-series storage
- **WebSocket Broadcasting**: Real-time updates to frontend
- **Caching**: Redis-based data caching for performance
- **Health Checks**: Kafka, database, and Redis connectivity

### 4. Notification Service (`/microservices/notification-service`)
- **Multi-channel Support**: Email, SMS, Push notifications
- **Template Engine**: Thymeleaf-based email templates
- **Kafka Integration**: Event-driven notification triggers
- **Retry Logic**: Configurable retry with exponential backoff
- **Delivery Tracking**: Comprehensive notification status tracking

### 5. User Service (`/microservices/user-service`)
- **JWT Authentication**: Secure token-based authentication
- **User Management**: Registration, login, profile management
- **Watchlist Management**: Symbol tracking and alerts
- **Role-Based Access**: User, admin, premium user roles
- **Password Security**: BCrypt hashing with salt

### 6. Apache Flink Jobs (`/flink-jobs`)
- **Stock Data Processor**: Real-time price analysis and alerts
- **News Data Processor**: Sentiment analysis and trading signals
- **CEP Patterns**: Complex event processing for pattern detection
- **Window Operations**: Time-based aggregations and calculations
- **State Management**: RocksDB backend for fault tolerance

### 7. Database Schema (`/database`)
- **TimescaleDB Tables**: Hypertables for time-series data
- **Optimized Indexes**: Performance-tuned query optimization
- **Data Retention**: Automated cleanup and archival
- **Migration Scripts**: Flyway-compatible database migrations

## 📊 Monitoring and Observability

### Metrics Collection
- **Application Metrics**: Custom business metrics via Micrometer
- **System Metrics**: JVM, database, and Kafka metrics
- **Scraper Metrics**: Request rates, success/failure counts, circuit breaker status
- **Flink Metrics**: Job status, throughput, latency measurements

### Dashboards and Alerting
- **Grafana Dashboards**: Real-time visualization of all metrics
- **Alert Rules**: Prometheus-based alerting for critical conditions
- **Health Monitoring**: Service health checks and dependency monitoring
- **Log Aggregation**: Structured logging with correlation tracking

## 🔒 Security Features

- **JWT Authentication**: Stateless authentication with configurable expiration
- **API Rate Limiting**: Protection against abuse and DoS attacks
- **Input Validation**: Comprehensive validation using Bean Validation
- **SQL Injection Protection**: Parameterized queries and JPA repositories
- **Secrets Management**: Environment-based configuration management

## 📈 Performance Optimizations

- **Connection Pooling**: HikariCP for database connections
- **Caching Strategy**: Redis-based caching for frequently accessed data
- **Async Processing**: Non-blocking I/O throughout the application
- **Batch Processing**: Optimized database writes and Kafka consumption
- **Time-series Optimization**: TimescaleDB for efficient time-based queries

## 🧪 Testing Strategy

### Unit Tests
```bash
# Python scraper tests
cd scraper && python -m pytest tests/

# Spring Boot microservices tests
cd microservices/data-consumer && mvn test
cd microservices/notification-service && mvn test
cd microservices/user-service && mvn test
```

### Integration Tests
```bash
# Start test infrastructure
docker-compose -f tests/integration/docker-compose.test.yml up -d

# Run integration tests
cd tests/integration && python -m pytest
```

### End-to-End Tests
```bash
# Full pipeline test
cd tests/e2e && python test_pipeline.py
```

## 🚀 Deployment

### Development Environment
```bash
# Start all services
docker-compose up -d

# Scale specific services
docker-compose up -d --scale flink-taskmanager=3
```

### Production Environment
```bash
# Use production configuration
docker-compose -f docker-compose.prod.yml up -d

# Or deploy to Kubernetes
kubectl apply -f infrastructure/kubernetes/
```

## 📋 Environment Variables

```env
# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/stocktracker
DATABASE_USERNAME=stocktracker
DATABASE_PASSWORD=password

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081

# External APIs
ALPHA_VANTAGE_API_KEY=your_api_key
NEWS_API_KEY=your_news_api_key
FRED_API_KEY=your_fred_api_key

# Authentication
JWT_SECRET=your_jwt_secret_key

# Notifications
SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=your_email
SMTP_PASSWORD=your_password
TWILIO_ACCOUNT_SID=your_twilio_sid
TWILIO_AUTH_TOKEN=your_twilio_token

# Monitoring
PROMETHEUS_ENABLED=true
GRAFANA_ADMIN_PASSWORD=admin
```

## 📚 API Documentation

### Authentication Endpoints
```http
POST /api/users/register
POST /api/users/login
POST /api/users/refresh-token
POST /api/users/logout
```

### Stock Data Endpoints
```http
GET /api/stocks/{symbol}/latest
GET /api/stocks/{symbol}/history?from=2024-01-01&to=2024-01-31
GET /api/stocks/trending
GET /api/stocks/movers
```

### User Management Endpoints
```http
GET /api/users/profile
PUT /api/users/profile
GET /api/users/watchlist
POST /api/users/watchlist/{symbol}
DELETE /api/users/watchlist/{symbol}
```

### Notification Endpoints
```http
GET /api/notifications
POST /api/notifications/preferences
GET /api/notifications/history
```

## 🛠️ Development Guide

### Local Development Setup
```bash
# Install dependencies
pip install -r scraper/requirements.txt
mvn clean install -f microservices/pom.xml

# Start infrastructure only
docker-compose up -d postgres redis kafka zookeeper schema-registry

# Run services locally
cd scraper && python src/enhanced_scraper.py
cd microservices/api-gateway && mvn spring-boot:run
cd microservices/data-consumer && mvn spring-boot:run
cd microservices/notification-service && mvn spring-boot:run
cd microservices/user-service && mvn spring-boot:run
```

### Adding New Data Sources
1. Create new scraper module in `scraper/src/sources/`
2. Implement rate limiting and circuit breaker
3. Add Kafka topic configuration
4. Update Flink processors for new data type
5. Add corresponding database schema

### Contributing Guidelines
- Follow existing code style and patterns
- Add comprehensive unit tests
- Update documentation for new features
- Ensure all health checks pass
- Add appropriate logging and metrics

## 🔧 Troubleshooting

### Common Issues

**Services not starting:**
```bash
# Check service dependencies
docker-compose ps
docker-compose logs [service-name]

# Verify environment variables
cat .env
```

**Kafka connection issues:**
```bash
# Check Kafka topics
docker exec -it stocktracker-kafka kafka-topics --list --bootstrap-server localhost:9092

# Test producer/consumer
docker exec -it stocktracker-kafka kafka-console-producer --topic stock-data --bootstrap-server localhost:9092
```

**Database connection problems:**
```bash
# Check PostgreSQL
docker exec -it stocktracker-postgres psql -U stocktracker -d stocktracker -c "\dt"

# Verify TimescaleDB extension
docker exec -it stocktracker-postgres psql -U stocktracker -d stocktracker -c "\dx"
```

**Performance issues:**
- Monitor Grafana dashboards for bottlenecks
- Check Prometheus metrics for high latency
- Verify database query performance
- Scale services horizontally if needed

## 📖 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Apache Flink Documentation](https://flink.apache.org/docs/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [TimescaleDB Documentation](https://docs.timescale.com/)
- [Prometheus Monitoring](https://prometheus.io/docs/)

## 🏆 Production Readiness Checklist

- [ ] All services have health checks
- [ ] Monitoring and alerting configured
- [ ] Database migrations tested
- [ ] Security scanning completed
- [ ] Load testing performed
- [ ] Backup and recovery procedures documented
- [ ] Secrets properly managed
- [ ] Log aggregation configured
- [ ] Service dependencies documented
- [ ] Runbook created for operations

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- Apache Flink community for stream processing capabilities
- TimescaleDB for time-series database optimization
- Confluent for Kafka ecosystem tools
