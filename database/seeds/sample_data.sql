-- Sample data for testing the Stock Tracker application
-- This file contains test data for development and testing purposes

-- Insert sample users
INSERT INTO users (username, email, password, first_name, last_name, status, email_verified) VALUES
('john_doe', 'john.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7xDz8QzaC3e7qFkFceDxGcL0nrUQJDK', 'John', 'Doe', 'ACTIVE', true),
('jane_smith', 'jane.smith@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7xDz8QzaC3e7qFkFceDxGcL0nrUQJDK', 'Jane', 'Smith', 'ACTIVE', true),
('admin_user', 'admin@stocktracker.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7xDz8QzaC3e7qFkFceDxGcL0nrUQJDK', 'Admin', 'User', 'ACTIVE', true);

-- Insert user roles
INSERT INTO user_roles (user_id, role) VALUES
(1, 'USER'),
(2, 'PREMIUM_USER'),
(3, 'ADMIN'),
(3, 'USER');

-- Insert sample watchlists
INSERT INTO user_watchlist (user_id, symbol) VALUES
(1, 'AAPL'),
(1, 'GOOGL'),
(1, 'MSFT'),
(2, 'AAPL'),
(2, 'TSLA'),
(2, 'AMZN'),
(2, 'META');

-- Insert sample stock data (last 24 hours)
INSERT INTO stock_data (symbol, price, volume, timestamp, change, change_percent, source) VALUES
('AAPL', 175.25, 45234567, NOW() - INTERVAL '1 hour', 2.15, 1.24, 'alpha_vantage'),
('AAPL', 173.10, 42156789, NOW() - INTERVAL '2 hours', -0.90, -0.52, 'alpha_vantage'),
('AAPL', 174.00, 38945612, NOW() - INTERVAL '3 hours', 1.50, 0.87, 'alpha_vantage'),
('AAPL', 172.50, 41567890, NOW() - INTERVAL '4 hours', -1.25, -0.72, 'alpha_vantage'),

('GOOGL', 2845.67, 1234567, NOW() - INTERVAL '1 hour', 15.23, 0.54, 'alpha_vantage'),
('GOOGL', 2830.44, 1156789, NOW() - INTERVAL '2 hours', -8.90, -0.31, 'alpha_vantage'),
('GOOGL', 2839.34, 1345612, NOW() - INTERVAL '3 hours', 12.50, 0.44, 'alpha_vantage'),
('GOOGL', 2826.84, 1267890, NOW() - INTERVAL '4 hours', -5.25, -0.18, 'alpha_vantage'),

('MSFT', 378.92, 23456789, NOW() - INTERVAL '1 hour', 3.45, 0.92, 'alpha_vantage'),
('MSFT', 375.47, 21567890, NOW() - INTERVAL '2 hours', -2.10, -0.56, 'alpha_vantage'),
('MSFT', 377.57, 25234567, NOW() - INTERVAL '3 hours', 4.25, 1.14, 'alpha_vantage'),
('MSFT', 373.32, 22789012, NOW() - INTERVAL '4 hours', -1.85, -0.49, 'alpha_vantage'),

('TSLA', 245.67, 78901234, NOW() - INTERVAL '1 hour', 8.23, 3.46, 'alpha_vantage'),
('TSLA', 237.44, 82345678, NOW() - INTERVAL '2 hours', -5.90, -2.42, 'alpha_vantage'),
('TSLA', 243.34, 76543210, NOW() - INTERVAL '3 hours', 7.50, 3.18, 'alpha_vantage'),
('TSLA', 235.84, 79876543, NOW() - INTERVAL '4 hours', -3.25, -1.36, 'alpha_vantage'),

('AMZN', 152.34, 34567890, NOW() - INTERVAL '1 hour', 1.89, 1.26, 'alpha_vantage'),
('AMZN', 150.45, 32109876, NOW() - INTERVAL '2 hours', -0.95, -0.63, 'alpha_vantage'),
('AMZN', 151.40, 35432109, NOW() - INTERVAL '3 hours', 2.15, 1.44, 'alpha_vantage'),
('AMZN', 149.25, 33876540, NOW() - INTERVAL '4 hours', -1.10, -0.73, 'alpha_vantage');

-- Insert sample news data
INSERT INTO news_data (title, content, url, timestamp, source, sentiment_score, category) VALUES
('Apple Reports Strong Q4 Earnings', 'Apple Inc. exceeded expectations with strong iPhone sales and services revenue growth in Q4 2024.', 'https://example.com/apple-earnings', NOW() - INTERVAL '2 hours', 'Financial News', 0.75, 'earnings'),
('Tesla Announces New Gigafactory', 'Tesla reveals plans for a new Gigafactory in Texas, expected to boost production capacity significantly.', 'https://example.com/tesla-gigafactory', NOW() - INTERVAL '4 hours', 'Tech News', 0.65, 'business'),
('Market Volatility Concerns Rising', 'Analysts warn of increased market volatility due to economic uncertainties and geopolitical tensions.', 'https://example.com/market-volatility', NOW() - INTERVAL '6 hours', 'Market Analysis', -0.45, 'market'),
('Google Launches New AI Platform', 'Google unveils advanced AI platform for enterprise customers, targeting the growing AI market.', 'https://example.com/google-ai', NOW() - INTERVAL '8 hours', 'Tech News', 0.55, 'technology');

-- Insert news-symbol mappings
INSERT INTO news_symbols (news_id, symbol) VALUES
(1, 'AAPL'),
(2, 'TSLA'),
(3, 'AAPL'),
(3, 'GOOGL'),
(3, 'MSFT'),
(4, 'GOOGL');

-- Insert sample economic data
INSERT INTO economic_data (indicator, value, timestamp, country, source, unit, period) VALUES
('GDP', 4.2, NOW() - INTERVAL '1 day', 'US', 'fred', 'Percent', 'quarterly'),
('UNRATE', 3.7, NOW() - INTERVAL '1 day', 'US', 'fred', 'Percent', 'monthly'),
('CPIAUCSL', 3.2, NOW() - INTERVAL '1 day', 'US', 'fred', 'Index', 'monthly'),
('FEDFUNDS', 5.25, NOW() - INTERVAL '1 day', 'US', 'fred', 'Percent', 'monthly');

-- Insert sample user alerts
INSERT INTO user_alerts (user_id, symbol, alert_type, threshold_value, enabled) VALUES
(1, 'AAPL', 'PRICE_ABOVE', 180.00, true),
(1, 'GOOGL', 'PRICE_BELOW', 2800.00, true),
(1, 'MSFT', 'VOLUME_SPIKE', 50000000, true),
(2, 'TSLA', 'PRICE_ABOVE', 250.00, true),
(2, 'AMZN', 'PRICE_BELOW', 145.00, true);

-- Insert sample notifications
INSERT INTO notifications (user_id, type, status, subject, content, recipient_email, template_name) VALUES
(1, 'EMAIL', 'SENT', 'Price Alert: AAPL', 'Apple stock has reached your target price of $180.00', 'john.doe@example.com', 'price-alert'),
(1, 'EMAIL', 'PENDING', 'Daily Market Digest', 'Your daily summary of market activities', 'john.doe@example.com', 'daily-digest'),
(2, 'EMAIL', 'SENT', 'Welcome to StockTracker', 'Welcome to our platform! Here is how to get started.', 'jane.smith@example.com', 'welcome'),
(2, 'EMAIL', 'FAILED', 'Volume Alert: TSLA', 'Tesla showing unusual volume activity', 'jane.smith@example.com', 'volume-alert');

-- Insert sample trading signals
INSERT INTO trading_signals (symbol, signal_type, confidence, reason, source) VALUES
('AAPL', 'BUY', 0.85, 'Strong earnings report and positive analyst sentiment', 'news-analysis'),
('TSLA', 'HOLD', 0.65, 'Mixed signals from recent news and technical indicators', 'technical-analysis'),
('GOOGL', 'BUY', 0.78, 'AI platform launch expected to drive revenue growth', 'news-analysis'),
('MSFT', 'HOLD', 0.72, 'Stable performance with moderate growth outlook', 'fundamental-analysis'),
('AMZN', 'SELL', 0.60, 'Concerns about retail segment performance', 'analyst-downgrade');

-- Update timestamps for more realistic data distribution
UPDATE stock_data SET timestamp = timestamp - INTERVAL '1 day' * (RANDOM() * 7);
UPDATE news_data SET timestamp = timestamp - INTERVAL '1 hour' * (RANDOM() * 24);
UPDATE trading_signals SET timestamp = timestamp - INTERVAL '1 hour' * (RANDOM() * 12);

-- Add some processed stock data for Flink output testing
INSERT INTO stock_data_processed (symbol, price, volume, timestamp, change_percent, avg_price_5min, source) VALUES
('AAPL', 175.25, 45234567, NOW() - INTERVAL '5 minutes', 1.24, 174.85, 'flink-processor'),
('GOOGL', 2845.67, 1234567, NOW() - INTERVAL '5 minutes', 0.54, 2838.45, 'flink-processor'),
('MSFT', 378.92, 23456789, NOW() - INTERVAL '5 minutes', 0.92, 377.23, 'flink-processor'),
('TSLA', 245.67, 78901234, NOW() - INTERVAL '5 minutes', 3.46, 241.34, 'flink-processor');

-- Refresh sequences to ensure proper ID generation
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('stock_data_id_seq', (SELECT MAX(id) FROM stock_data));
SELECT setval('news_data_id_seq', (SELECT MAX(id) FROM news_data));
SELECT setval('economic_data_id_seq', (SELECT MAX(id) FROM economic_data));
SELECT setval('notifications_id_seq', (SELECT MAX(id) FROM notifications));
SELECT setval('user_alerts_id_seq', (SELECT MAX(id) FROM user_alerts));
SELECT setval('trading_signals_id_seq', (SELECT MAX(id) FROM trading_signals));

-- Display sample data summary
SELECT 'Sample Data Loaded Successfully!' as status;
SELECT COUNT(*) as user_count FROM users;
SELECT COUNT(*) as stock_data_count FROM stock_data;
SELECT COUNT(*) as news_count FROM news_data;
SELECT COUNT(*) as alert_count FROM user_alerts;
SELECT COUNT(*) as notification_count FROM notifications;