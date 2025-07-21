-- Initialize the stock tracker database

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- Companies table
CREATE TABLE IF NOT EXISTS companies (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    sector VARCHAR(100),
    market_cap BIGINT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User companies (tracking list)
CREATE TABLE IF NOT EXISTS user_companies (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    company_id INTEGER REFERENCES companies(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, company_id)
);

-- User notification preferences
CREATE TABLE IF NOT EXISTS user_notification_preferences (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    email_enabled BOOLEAN DEFAULT true,
    sms_enabled BOOLEAN DEFAULT false,
    push_enabled BOOLEAN DEFAULT true,
    price_change_threshold DECIMAL(5,2) DEFAULT 5.0,
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Stock prices (time series data)
CREATE TABLE IF NOT EXISTS stock_prices (
    id SERIAL PRIMARY KEY,
    company_id INTEGER REFERENCES companies(id) ON DELETE CASCADE,
    price DECIMAL(10,2) NOT NULL,
    volume BIGINT,
    market_cap BIGINT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(50) DEFAULT 'api'
);

-- News articles
CREATE TABLE IF NOT EXISTS news_articles (
    id SERIAL PRIMARY KEY,
    company_id INTEGER REFERENCES companies(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    url VARCHAR(1000),
    source VARCHAR(100),
    sentiment_score DECIMAL(3,2), -- -1 to 1
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Economic indicators
CREATE TABLE IF NOT EXISTS economic_indicators (
    id SERIAL PRIMARY KEY,
    indicator_name VARCHAR(100) NOT NULL,
    value DECIMAL(15,4),
    unit VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(100)
);

-- Notifications log
CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    company_id INTEGER REFERENCES companies(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- 'price_alert', 'news', 'earnings'
    channel VARCHAR(20) NOT NULL, -- 'email', 'sms', 'push'
    title VARCHAR(255),
    message TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'sent' -- 'sent', 'failed', 'pending'
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_stock_prices_company_timestamp ON stock_prices(company_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_news_articles_company_published ON news_articles(company_id, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_companies_user ON user_companies(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_timestamp ON notifications(user_id, sent_at DESC);

-- Insert sample companies
INSERT INTO companies (symbol, name, sector, description) VALUES
    ('AAPL', 'Apple Inc.', 'Technology', 'Consumer electronics and software company'),
    ('GOOGL', 'Alphabet Inc.', 'Technology', 'Internet search and advertising company'),
    ('MSFT', 'Microsoft Corporation', 'Technology', 'Software and cloud computing company'),
    ('AMZN', 'Amazon.com Inc.', 'Consumer Discretionary', 'E-commerce and cloud computing company'),
    ('TSLA', 'Tesla Inc.', 'Consumer Discretionary', 'Electric vehicle and energy company'),
    ('META', 'Meta Platforms Inc.', 'Technology', 'Social media and virtual reality company'),
    ('NVDA', 'NVIDIA Corporation', 'Technology', 'Graphics processing and AI company'),
    ('JPM', 'JPMorgan Chase & Co.', 'Financial Services', 'Investment banking and financial services'),
    ('JNJ', 'Johnson & Johnson', 'Healthcare', 'Pharmaceutical and medical devices company'),
    ('V', 'Visa Inc.', 'Financial Services', 'Payment processing company')
ON CONFLICT (symbol) DO NOTHING;

-- Create a sample user for testing
INSERT INTO users (email, password_hash, first_name, last_name) VALUES
    ('demo@example.com', '$2b$10$9...', 'Demo', 'User')
ON CONFLICT (email) DO NOTHING;