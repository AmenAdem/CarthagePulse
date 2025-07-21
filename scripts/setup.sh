#!/bin/bash

# Setup script for Stock Tracker Data Pipeline

set -e

echo "🚀 Setting up Stock Tracker Data Pipeline..."

# Check if Docker and Docker Compose are installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Create environment file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating environment file..."
    cat > .env << EOF
# Database
DATABASE_URL=postgresql://stockuser:stockpass@postgres:5432/stocktracker
REDIS_URL=redis://redis:6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
SCHEMA_REGISTRY_URL=http://schema-registry:8081

# External APIs (replace with your actual API keys)
ALPHA_VANTAGE_API_KEY=demo
NEWS_API_KEY=demo

# Notifications (configure for production)
SMTP_HOST=smtp.gmail.com
SMTP_USER=your_email@gmail.com
SMTP_PASS=your_app_password
TWILIO_SID=your_twilio_sid
TWILIO_TOKEN=your_twilio_token
TWILIO_PHONE=+1234567890

# Security
JWT_SECRET=your-super-secret-jwt-key-change-this-in-production

# Frontend
REACT_APP_API_URL=http://localhost:3000
REACT_APP_WS_URL=ws://localhost:3000

# Environment
NODE_ENV=development
EOF
    echo "✅ Environment file created. Please update with your API keys and credentials."
fi

# Build and start infrastructure services
echo "🐳 Starting infrastructure services..."
docker-compose up -d zookeeper kafka schema-registry postgres redis prometheus grafana

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check if Kafka is ready
echo "🔍 Checking Kafka connectivity..."
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list || {
    echo "❌ Kafka is not ready. Please check the logs."
    exit 1
}

# Create Kafka topics
echo "📝 Creating Kafka topics..."
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --create --topic stock-prices --partitions 3 --replication-factor 1 --if-not-exists
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --create --topic news-articles --partitions 3 --replication-factor 1 --if-not-exists
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --create --topic economic-indicators --partitions 1 --replication-factor 1 --if-not-exists
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --create --topic stock-alerts --partitions 3 --replication-factor 1 --if-not-exists

echo "✅ Kafka topics created successfully"

# Build application services
echo "🏗️  Building application services..."
docker-compose build backend frontend scraper notifications

echo "✅ Setup completed successfully!"
echo ""
echo "📚 Next steps:"
echo "1. Update .env file with your API keys and credentials"
echo "2. Run './scripts/start.sh' to start all services"
echo "3. Access the application at http://localhost:3002"
echo "4. Access Grafana at http://localhost:3001 (admin/admin)"
echo "5. Access Flink Dashboard at http://localhost:8081"
echo ""
echo "🔗 Useful commands:"
echo "  - View logs: docker-compose logs -f [service_name]"
echo "  - Stop services: docker-compose down"
echo "  - Restart services: docker-compose restart [service_name]"