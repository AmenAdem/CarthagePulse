# API Documentation

## Authentication

All API endpoints (except registration and login) require authentication via JWT token.

### Authentication Header
```
Authorization: Bearer <jwt_token>
```

## Endpoints

### Authentication Endpoints

#### Register User
```http
POST /api/users/register
Content-Type: application/json

{
  "username": "string",
  "email": "string",
  "password": "string",
  "firstName": "string",
  "lastName": "string"
}
```

**Response:**
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "status": "PENDING_VERIFICATION"
}
```

#### Login
```http
POST /api/users/login
Content-Type: application/json

{
  "email": "string",
  "password": "string"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "type": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

### Stock Data Endpoints

#### Get Latest Stock Data
```http
GET /api/stocks/{symbol}/latest
```

**Response:**
```json
{
  "symbol": "AAPL",
  "price": 175.25,
  "volume": 45234567,
  "timestamp": "2024-01-15T10:30:00Z",
  "change": 2.15,
  "changePercent": 1.24,
  "marketCap": 2750000000000,
  "peRatio": 28.5,
  "source": "alpha_vantage"
}
```

#### Get Historical Stock Data
```http
GET /api/stocks/{symbol}/history?from=2024-01-01&to=2024-01-31&interval=1d
```

**Parameters:**
- `from`: Start date (ISO 8601)
- `to`: End date (ISO 8601)
- `interval`: Data interval (1m, 5m, 15m, 1h, 1d)

**Response:**
```json
{
  "symbol": "AAPL",
  "data": [
    {
      "timestamp": "2024-01-15T00:00:00Z",
      "open": 173.10,
      "high": 175.50,
      "low": 172.80,
      "close": 175.25,
      "volume": 45234567
    }
  ],
  "pagination": {
    "page": 1,
    "size": 100,
    "total": 30
  }
}
```

#### Get Trending Stocks
```http
GET /api/stocks/trending?limit=10
```

**Response:**
```json
{
  "stocks": [
    {
      "symbol": "AAPL",
      "totalVolume": 450000000,
      "averageVolume": 85000000,
      "volumeRatio": 5.29
    }
  ]
}
```

#### Get Market Movers
```http
GET /api/stocks/movers?type=gainers&limit=10
```

**Parameters:**
- `type`: gainers, losers, most_active
- `limit`: Number of results (max 50)

### User Management Endpoints

#### Get User Profile
```http
GET /api/users/profile
```

**Response:**
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-0123",
  "status": "ACTIVE",
  "emailVerified": true,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

#### Update User Profile
```http
PUT /api/users/profile
Content-Type: application/json

{
  "firstName": "string",
  "lastName": "string",
  "phoneNumber": "string"
}
```

#### Get User Watchlist
```http
GET /api/users/watchlist
```

**Response:**
```json
{
  "symbols": [
    {
      "symbol": "AAPL",
      "addedAt": "2024-01-01T00:00:00Z",
      "latestPrice": 175.25,
      "change": 2.15,
      "changePercent": 1.24
    }
  ]
}
```

#### Add Symbol to Watchlist
```http
POST /api/users/watchlist/{symbol}
```

#### Remove Symbol from Watchlist
```http
DELETE /api/users/watchlist/{symbol}
```

### Alert Management Endpoints

#### Get User Alerts
```http
GET /api/users/alerts
```

**Response:**
```json
{
  "alerts": [
    {
      "id": 1,
      "symbol": "AAPL",
      "alertType": "PRICE_ABOVE",
      "thresholdValue": 180.00,
      "enabled": true,
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### Create Alert
```http
POST /api/users/alerts
Content-Type: application/json

{
  "symbol": "string",
  "alertType": "PRICE_ABOVE|PRICE_BELOW|VOLUME_SPIKE|NEWS_SENTIMENT",
  "thresholdValue": 0.0,
  "enabled": true
}
```

#### Update Alert
```http
PUT /api/users/alerts/{alertId}
Content-Type: application/json

{
  "thresholdValue": 0.0,
  "enabled": true
}
```

#### Delete Alert
```http
DELETE /api/users/alerts/{alertId}
```

### Notification Endpoints

#### Get Notifications
```http
GET /api/notifications?page=0&size=20&status=SENT
```

**Parameters:**
- `page`: Page number (0-based)
- `size`: Page size (max 100)
- `status`: PENDING, SENT, FAILED, CANCELLED

**Response:**
```json
{
  "notifications": [
    {
      "id": 1,
      "type": "EMAIL",
      "status": "SENT",
      "subject": "Price Alert: AAPL",
      "content": "Apple stock reached your target price",
      "sentAt": "2024-01-15T10:30:00Z",
      "createdAt": "2024-01-15T10:29:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

#### Update Notification Preferences
```http
POST /api/notifications/preferences
Content-Type: application/json

{
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true,
  "dailyDigest": true,
  "alertTypes": ["PRICE_ABOVE", "VOLUME_SPIKE"]
}
```

### News Endpoints

#### Get Latest News
```http
GET /api/news?symbols=AAPL,GOOGL&limit=20
```

**Parameters:**
- `symbols`: Comma-separated list of symbols
- `limit`: Number of articles (max 100)
- `category`: earnings, merger, ipo, etc.

**Response:**
```json
{
  "articles": [
    {
      "id": 1,
      "title": "Apple Reports Strong Q4 Earnings",
      "summary": "Apple exceeded expectations...",
      "url": "https://example.com/article",
      "timestamp": "2024-01-15T08:00:00Z",
      "source": "Financial News",
      "sentimentScore": 0.75,
      "symbols": ["AAPL"]
    }
  ]
}
```

### Analytics Endpoints

#### Get Market Summary
```http
GET /api/analytics/market-summary
```

**Response:**
```json
{
  "marketStatus": "OPEN",
  "timestamp": "2024-01-15T15:30:00Z",
  "indices": {
    "SPY": {
      "price": 485.25,
      "change": 5.75,
      "changePercent": 1.20
    }
  },
  "topGainers": [...],
  "topLosers": [...],
  "mostActive": [...]
}
```

#### Get Stock Statistics
```http
GET /api/analytics/stocks/{symbol}/stats?period=1M
```

**Parameters:**
- `period`: 1D, 1W, 1M, 3M, 1Y

**Response:**
```json
{
  "symbol": "AAPL",
  "period": "1M",
  "statistics": {
    "avgPrice": 172.45,
    "minPrice": 165.20,
    "maxPrice": 180.10,
    "totalVolume": 1250000000,
    "avgVolume": 42500000,
    "volatility": 0.025
  }
}
```

## WebSocket API

### Connection
```
ws://localhost:8082/ws
```

### Subscribe to Real-time Updates
```json
{
  "action": "subscribe",
  "topic": "stocks",
  "symbols": ["AAPL", "GOOGL"]
}
```

### Real-time Stock Updates
```json
{
  "type": "stock_update",
  "symbol": "AAPL",
  "price": 175.25,
  "volume": 45234567,
  "timestamp": "2024-01-15T15:30:00Z",
  "change": 2.15,
  "changePercent": 1.24
}
```

### News Updates
```json
{
  "type": "news_update",
  "title": "Breaking: Apple Announces New Product",
  "symbols": ["AAPL"],
  "sentimentScore": 0.85,
  "timestamp": "2024-01-15T15:30:00Z"
}
```

## Error Responses

### Standard Error Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": [
      {
        "field": "email",
        "message": "Email format is invalid"
      }
    ],
    "timestamp": "2024-01-15T15:30:00Z"
  }
}
```

### HTTP Status Codes
- `200` - Success
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `429` - Rate Limited
- `500` - Internal Server Error

## Rate Limiting

### Default Limits
- **Authentication**: 5 requests per minute
- **Stock Data**: 100 requests per minute
- **User Operations**: 50 requests per minute
- **WebSocket**: 1000 messages per minute

### Rate Limit Headers
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248000
```

## Pagination

### Request Parameters
```
page=0&size=20&sort=timestamp,desc
```

### Response Format
```json
{
  "content": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 500,
    "totalPages": 25,
    "first": true,
    "last": false
  }
}
```