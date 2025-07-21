# Stock Tracker Data Pipeline - Development Setup

## Prerequisites

Before setting up the development environment, ensure you have the following installed:

- **Docker** (version 20.0 or higher)
- **Docker Compose** (version 2.0 or higher)
- **Node.js** (version 16 or higher) - for local development
- **Python** (version 3.8 or higher) - for scraper development
- **Java** (version 11 or higher) - for Flink development
- **Maven** (version 3.6 or higher) - for Java builds
- **Git** - for version control

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/AmenAdem/Real-Time-Investor-Updates-Pipeline.git
cd Real-Time-Investor-Updates-Pipeline
```

### 2. Initial Setup

Run the setup script to initialize the development environment:

```bash
./scripts/setup.sh
```

This script will:
- Create a `.env` file with default configuration
- Start infrastructure services (Kafka, PostgreSQL, Redis, etc.)
- Create necessary Kafka topics
- Build Docker images for application services

### 3. Configure Environment

Edit the `.env` file with your API keys and credentials:

```bash
# External API Keys (get free keys from respective services)
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_key
NEWS_API_KEY=your_news_api_key

# Email Configuration (for notifications)
SMTP_USER=your_email@gmail.com
SMTP_PASS=your_app_password

# SMS Configuration (optional - Twilio)
TWILIO_SID=your_twilio_sid
TWILIO_TOKEN=your_twilio_token
TWILIO_PHONE=+1234567890

# Security (change in production)
JWT_SECRET=your-super-secret-jwt-key
```

### 4. Start All Services

```bash
./scripts/start.sh
```

### 5. Access the Application

- **Frontend Dashboard**: http://localhost:3002
- **Backend API**: http://localhost:3000
- **Grafana Monitoring**: http://localhost:3001 (admin/admin)
- **Flink Dashboard**: http://localhost:8081
- **Prometheus**: http://localhost:9090

## Development Workflow

### Frontend Development (React)

For active frontend development with hot reloading:

```bash
cd frontend
npm install
npm start
```

This will start the React development server on http://localhost:3000 (different from the backend port).

### Backend Development (Node.js)

For backend development with auto-restart:

```bash
cd backend
npm install
npm run dev
```

### Scraper Development (Python)

For scraper development:

```bash
cd scraper
pip install -r requirements.txt
python main.py
```

### Flink Development (Java)

Build the Flink job:

```bash
cd flink
mvn clean package
```

Submit the job to Flink cluster:

```bash
# Using Flink CLI (if installed locally)
flink run -c com.stocktracker.flink.StockDataStreamProcessor target/flink-stream-processor-1.0-SNAPSHOT.jar
```

## Local Development Services

### Database Management

Connect to PostgreSQL:

```bash
docker-compose exec postgres psql -U stockuser -d stocktracker
```

Useful SQL commands:

```sql
-- View all companies
SELECT * FROM companies;

-- View user tracking data
SELECT u.email, c.symbol, c.name 
FROM users u 
JOIN user_companies uc ON u.id = uc.user_id 
JOIN companies c ON uc.company_id = c.id;

-- View recent stock prices
SELECT c.symbol, sp.price, sp.timestamp 
FROM stock_prices sp 
JOIN companies c ON sp.company_id = c.id 
ORDER BY sp.timestamp DESC 
LIMIT 10;
```

### Redis Management

Connect to Redis:

```bash
docker-compose exec redis redis-cli
```

### Kafka Management

List topics:

```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

View topic messages:

```bash
# Stock prices
docker-compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic stock-prices --from-beginning

# News articles
docker-compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic news-articles --from-beginning

# Stock alerts
docker-compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic stock-alerts --from-beginning
```

## Testing

### Unit Tests

Run backend tests:

```bash
cd backend
npm test
```

Run frontend tests:

```bash
cd frontend
npm test
```

### Integration Tests

Test the complete pipeline:

```bash
# Start all services
./scripts/start.sh

# Run integration test script
./scripts/test-integration.sh
```

### Load Testing

For performance testing:

```bash
# Install artillery
npm install -g artillery

# Run load tests
artillery run tests/load-test.yml
```

## Debugging

### View Service Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f scraper
docker-compose logs -f notifications
```

### Debug Node.js Services

Add debug breakpoints and run with Node debugger:

```bash
cd backend
node --inspect=0.0.0.0:9229 src/server.js
```

Connect with Chrome DevTools or VS Code debugger.

### Debug Flink Jobs

View Flink job details:

```bash
# Access Flink web UI
open http://localhost:8081

# View job logs
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager
```

## Code Quality

### Linting

Run ESLint for JavaScript/TypeScript:

```bash
cd backend && npm run lint
cd frontend && npm run lint
```

### Code Formatting

Use Prettier for consistent formatting:

```bash
npx prettier --write "src/**/*.{js,jsx,ts,tsx,json,css,md}"
```

### Pre-commit Hooks

Set up pre-commit hooks:

```bash
npm install -g husky lint-staged
```

## Environment Management

### Development Environment

```bash
# Start minimal services for development
docker-compose up -d postgres redis kafka zookeeper
```

### Testing Environment

```bash
# Use test database
export NODE_ENV=test
export DATABASE_URL=postgresql://stockuser:stockpass@localhost:5432/stocktracker_test
```

### Production Environment

```bash
# Use production configuration
export NODE_ENV=production
# Set production database URL, API keys, etc.
```

## Troubleshooting

### Common Issues

#### Kafka Connection Issues

```bash
# Check Kafka logs
docker-compose logs kafka

# Restart Kafka services
docker-compose restart zookeeper kafka
```

#### Database Connection Issues

```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Reset database
docker-compose down -v
docker-compose up -d postgres
```

#### Port Conflicts

Update port mappings in `docker-compose.yml` if you have conflicts:

```yaml
services:
  backend:
    ports:
      - "3001:3000"  # Change external port
```

#### Memory Issues

Increase Docker memory limits:

```bash
# For Docker Desktop, increase memory in settings
# For Linux, check available memory
free -h
```

### Performance Optimization

#### Database Performance

```sql
-- Add indexes for better query performance
CREATE INDEX CONCURRENTLY idx_stock_prices_company_timestamp 
ON stock_prices(company_id, timestamp DESC);

-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM stock_prices WHERE company_id = 1;
```

#### Kafka Performance

```bash
# Monitor Kafka lag
docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups
```

## Contributing

### Development Guidelines

1. **Branch Naming**: Use descriptive branch names (feature/add-sentiment-analysis)
2. **Commit Messages**: Follow conventional commits format
3. **Code Review**: All changes require review before merging
4. **Testing**: Ensure all tests pass before submitting PR

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Update documentation
7. Submit pull request

### Code Standards

- **JavaScript**: Use ESLint and Prettier
- **Python**: Follow PEP 8 guidelines
- **Java**: Follow Google Java Style Guide
- **Documentation**: Update README and docs for any changes

This development setup provides a comprehensive environment for building and testing the Stock Tracker Data Pipeline with all necessary tools and services.