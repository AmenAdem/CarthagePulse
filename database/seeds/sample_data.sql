-- Sample data for development and testing
INSERT INTO users (username, email, password_hash, first_name, last_name, is_verified) VALUES
('admin', 'admin@investorpipeline.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye5YQk5yT8jT2Zoy0oKXJvC1lSJGOFmPK', 'Admin', 'User', true),
('john_doe', 'john.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye5YQk5yT8jT2Zoy0oKXJvC1lSJGOFmPK', 'John', 'Doe', true),
('jane_smith', 'jane.smith@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye5YQk5yT8jT2Zoy0oKXJvC1lSJGOFmPK', 'Jane', 'Smith', true);

-- Sample user preferences
INSERT INTO user_preferences (user_id, notification_email, notification_sms, alert_threshold_percent) VALUES
(1, true, false, 3.0),
(2, true, true, 5.0),
(3, true, false, 7.5);

-- Sample watchlist entries
INSERT INTO stock_watchlist (user_id, symbol) VALUES
(1, 'AAPL'),
(1, 'GOOGL'),
(1, 'MSFT'),
(1, 'TSLA'),
(2, 'AAPL'),
(2, 'AMZN'),
(2, 'NVDA'),
(3, 'MSFT'),
(3, 'META'),
(3, 'NFLX');

-- Sample stock data for testing
INSERT INTO stock_data (symbol, price, volume, timestamp, source, change_percent, market_cap) VALUES
('AAPL', 150.25, 1000000, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'yahoo_finance', 1.5, 2400000000000),
('AAPL', 149.80, 950000, CURRENT_TIMESTAMP - INTERVAL '2 hours', 'alpha_vantage', 1.2, 2395000000000),
('GOOGL', 2750.50, 800000, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'yahoo_finance', 2.1, 1800000000000),
('MSFT', 380.75, 1200000, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'alpha_vantage', 0.8, 2800000000000),
('TSLA', 220.30, 2000000, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'yahoo_finance', -1.2, 700000000000),
('AMZN', 140.60, 1500000, CURRENT_TIMESTAMP - INTERVAL '2 hours', 'alpha_vantage', 1.8, 1450000000000),
('NVDA', 450.80, 3000000, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'yahoo_finance', 3.5, 1100000000000);

-- Sample price alerts
INSERT INTO price_alerts (symbol, current_price, change_percent, volume, alert_type, severity, timestamp) VALUES
('NVDA', 450.80, 3.5, 3000000, 'PRICE_SURGE', 'MEDIUM', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
('TSLA', 220.30, -5.2, 2500000, 'PRICE_DROP', 'MEDIUM', CURRENT_TIMESTAMP - INTERVAL '45 minutes');

-- Sample moving averages
INSERT INTO moving_averages (symbol, average_price, average_volume, data_points, window_start, window_end, calculated_at) VALUES
('AAPL', 150.025, 975000, 2, CURRENT_TIMESTAMP - INTERVAL '5 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GOOGL', 2750.50, 800000, 1, CURRENT_TIMESTAMP - INTERVAL '5 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('MSFT', 380.75, 1200000, 1, CURRENT_TIMESTAMP - INTERVAL '5 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Sample notifications
INSERT INTO notifications (user_id, type, channel, subject, message, status) VALUES
(1, 'EMAIL', 'admin@investorpipeline.com', 'Price Alert: NVDA', 'NVDA has increased by 3.5% to $450.80', 'SENT'),
(2, 'EMAIL', 'john.doe@example.com', 'Price Alert: TSLA', 'TSLA has dropped by 5.2% to $220.30', 'PENDING'),
(3, 'PUSH', 'device_token_123', 'Market Update', 'Your watchlist has 2 active alerts', 'DELIVERED');