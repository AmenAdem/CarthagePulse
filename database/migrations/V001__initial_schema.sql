-- Initial schema for Stock Tracker application
-- PostgreSQL with TimescaleDB extension for time-series data

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(120) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    last_login TIMESTAMP,
    login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,
    email_verification_token VARCHAR(255),
    email_verification_expires TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User roles table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN', 'PREMIUM_USER')),
    PRIMARY KEY (user_id, role)
);

-- User watchlist table
CREATE TABLE IF NOT EXISTS user_watchlist (
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(10) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, symbol)
);

-- Stock data table (hypertable for time-series)
CREATE TABLE IF NOT EXISTS stock_data (
    id BIGSERIAL,
    symbol VARCHAR(10) NOT NULL,
    price DECIMAL(19,4) NOT NULL,
    volume BIGINT NOT NULL DEFAULT 0,
    timestamp TIMESTAMP NOT NULL,
    change DECIMAL(19,4),
    change_percent DECIMAL(10,4),
    market_cap DECIMAL(19,2),
    pe_ratio DECIMAL(10,2),
    source VARCHAR(50) NOT NULL,
    day_high DECIMAL(19,4),
    day_low DECIMAL(19,4),
    fifty_two_week_high DECIMAL(19,4),
    fifty_two_week_low DECIMAL(19,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, timestamp)
);

-- Convert stock_data to hypertable
SELECT create_hypertable('stock_data', 'timestamp', if_not_exists => TRUE);

-- News data table
CREATE TABLE IF NOT EXISTS news_data (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT,
    url TEXT,
    timestamp TIMESTAMP NOT NULL,
    source VARCHAR(100) NOT NULL,
    sentiment_score DECIMAL(5,4),
    category VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- News symbols mapping table
CREATE TABLE IF NOT EXISTS news_symbols (
    news_id BIGINT REFERENCES news_data(id) ON DELETE CASCADE,
    symbol VARCHAR(10) NOT NULL,
    PRIMARY KEY (news_id, symbol)
);

-- Economic data table
CREATE TABLE IF NOT EXISTS economic_data (
    id BIGSERIAL PRIMARY KEY,
    indicator VARCHAR(50) NOT NULL,
    value DECIMAL(19,4) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    country VARCHAR(10) NOT NULL,
    source VARCHAR(50) NOT NULL,
    unit VARCHAR(20),
    period VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('EMAIL', 'SMS', 'PUSH', 'IN_APP')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED', 'SCHEDULED')),
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    recipient_email VARCHAR(100),
    recipient_phone VARCHAR(20),
    recipient_device_token VARCHAR(255),
    template_name VARCHAR(100),
    template_data TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User alerts configuration table
CREATE TABLE IF NOT EXISTS user_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(10) NOT NULL,
    alert_type VARCHAR(50) NOT NULL CHECK (alert_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'VOLUME_SPIKE', 'NEWS_SENTIMENT', 'TECHNICAL_INDICATOR')),
    threshold_value DECIMAL(19,4),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Stock data processed table (for Flink output)
CREATE TABLE IF NOT EXISTS stock_data_processed (
    id BIGSERIAL,
    symbol VARCHAR(10) NOT NULL,
    price DECIMAL(19,4) NOT NULL,
    volume BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    change_percent DECIMAL(10,4),
    avg_price_5min DECIMAL(19,4),
    avg_price_1h DECIMAL(19,4),
    volume_ratio DECIMAL(10,4),
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, timestamp),
    UNIQUE (symbol, timestamp)
);

-- Convert processed data to hypertable
SELECT create_hypertable('stock_data_processed', 'timestamp', if_not_exists => TRUE);

-- Trading signals table
CREATE TABLE IF NOT EXISTS trading_signals (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    signal_type VARCHAR(10) NOT NULL CHECK (signal_type IN ('BUY', 'SELL', 'HOLD')),
    confidence DECIMAL(5,4),
    reason TEXT,
    source VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

CREATE INDEX IF NOT EXISTS idx_stock_data_symbol ON stock_data(symbol);
CREATE INDEX IF NOT EXISTS idx_stock_data_symbol_timestamp ON stock_data(symbol, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_stock_data_timestamp ON stock_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_news_data_timestamp ON news_data(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_news_data_source ON news_data(source);
CREATE INDEX IF NOT EXISTS idx_news_symbols_symbol ON news_symbols(symbol);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications(type);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_alerts_user_id ON user_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_user_alerts_symbol ON user_alerts(symbol);
CREATE INDEX IF NOT EXISTS idx_user_alerts_enabled ON user_alerts(enabled);

CREATE INDEX IF NOT EXISTS idx_stock_data_processed_symbol ON stock_data_processed(symbol);
CREATE INDEX IF NOT EXISTS idx_stock_data_processed_timestamp ON stock_data_processed(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_trading_signals_symbol ON trading_signals(symbol);
CREATE INDEX IF NOT EXISTS idx_trading_signals_timestamp ON trading_signals(timestamp DESC);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply updated_at triggers to relevant tables
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_stock_data_updated_at BEFORE UPDATE ON stock_data FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_notifications_updated_at BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_alerts_updated_at BEFORE UPDATE ON user_alerts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO stocktracker;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO stocktracker;