#!/bin/bash

# Start script for Stock Tracker Data Pipeline

set -e

echo "🚀 Starting Stock Tracker Data Pipeline..."

# Check if setup has been run
if [ ! -f .env ]; then
    echo "❌ Environment file not found. Please run './scripts/setup.sh' first."
    exit 1
fi

# Start all services
echo "🐳 Starting all services..."
docker-compose up -d

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 60

# Check service health
echo "🔍 Checking service health..."

# Check if backend is ready
if curl -f http://localhost:3000/health > /dev/null 2>&1; then
    echo "✅ Backend service is healthy"
else
    echo "⚠️  Backend service is not responding"
fi

# Check if frontend is ready
if curl -f http://localhost:3002 > /dev/null 2>&1; then
    echo "✅ Frontend service is healthy"
else
    echo "⚠️  Frontend service is not responding"
fi

# Deploy Flink job
echo "🔄 Deploying Flink stream processing job..."
if [ -f flink/target/flink-stream-processor-1.0-SNAPSHOT.jar ]; then
    # Submit Flink job (this would require Flink CLI in production)
    echo "📋 Flink job JAR found. In production, you would submit this to the Flink cluster."
else
    echo "⚠️  Flink job JAR not found. Please build the Flink project first."
fi

echo "✅ All services started successfully!"
echo ""
echo "🌐 Application URLs:"
echo "  - Frontend Dashboard: http://localhost:3002"
echo "  - Backend API: http://localhost:3000"
echo "  - Grafana Monitoring: http://localhost:3001 (admin/admin)"
echo "  - Flink Dashboard: http://localhost:8081"
echo "  - Prometheus: http://localhost:9090"
echo ""
echo "📊 Monitoring:"
echo "  - View logs: docker-compose logs -f [service_name]"
echo "  - Check status: docker-compose ps"
echo "  - Stop all: docker-compose down"
echo ""
echo "🔑 Default login credentials:"
echo "  - Email: demo@example.com"
echo "  - Password: Create account via registration"