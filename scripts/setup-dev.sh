#!/bin/bash

# Stock Tracker Development Setup Script
set -e

echo "🚀 Setting up Stock Tracker Real-Time Pipeline..."

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
    echo "📝 Creating .env file with default values..."
    cat > .env << EOF
# API Keys (replace with your actual keys)
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_api_key_here
NEWS_API_KEY=your_news_api_key_here
FRED_API_KEY=your_fred_api_key_here

# JWT Secret
JWT_SECRET=myVeryLongSecretKeyThatShouldBeAtLeast256BitsLong

# Email Configuration
SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_app_password_here

# Twilio (for SMS notifications)
TWILIO_ACCOUNT_SID=your_twilio_account_sid
TWILIO_AUTH_TOKEN=your_twilio_auth_token
TWILIO_FROM_NUMBER=your_twilio_phone_number
EOF
    echo "⚠️  Please edit .env file with your actual API keys before running the application"
fi

# Create necessary directories
echo "📁 Creating required directories..."
mkdir -p logs
mkdir -p data/postgres
mkdir -p data/redis
mkdir -p data/kafka
mkdir -p data/zookeeper
mkdir -p scraper/logs

# Pull required Docker images
echo "📦 Pulling Docker images..."
docker-compose pull

# Build custom images
echo "🔨 Building custom Docker images..."
docker-compose build

echo "✅ Setup completed successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Edit .env file with your actual API keys"
echo "2. Run 'docker-compose up -d' to start all services"
echo "3. Wait for all services to be healthy (check with 'docker-compose ps')"
echo "4. Access the application:"
echo "   - API Gateway: http://localhost:8080"
echo "   - Grafana: http://localhost:3001 (admin/admin)"
echo "   - Prometheus: http://localhost:9090"
echo "   - Flink UI: http://localhost:8084"
echo "   - Scraper Metrics: http://localhost:8000/metrics"
echo ""
echo "🔍 To monitor services: docker-compose logs -f [service_name]"
echo "🛑 To stop services: docker-compose down"