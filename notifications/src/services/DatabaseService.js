const { Pool } = require('pg');
const logger = require('../utils/logger');

class DatabaseService {
  constructor() {
    this.pool = new Pool({
      connectionString: process.env.DATABASE_URL || 'postgresql://stockuser:stockpass@localhost:5432/stocktracker',
      max: 10,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 2000,
    });
  }

  async getUsersTrackingCompany(companyId) {
    try {
      const query = `
        SELECT DISTINCT u.id, u.email, u.first_name, u.last_name
        FROM users u
        JOIN user_companies uc ON u.id = uc.user_id
        WHERE uc.company_id = $1 AND u.is_active = true
      `;
      
      const result = await this.pool.query(query, [companyId]);
      return result.rows;
    } catch (error) {
      logger.error('Error getting users tracking company:', error);
      throw error;
    }
  }

  async getUserPreferences(userId) {
    try {
      const query = `
        SELECT email_enabled, sms_enabled, push_enabled, price_change_threshold, phone_number
        FROM user_notification_preferences
        WHERE user_id = $1
      `;
      
      const result = await this.pool.query(query, [userId]);
      return result.rows[0] || {
        email_enabled: true,
        sms_enabled: false,
        push_enabled: true,
        price_change_threshold: 5.0,
        phone_number: null
      };
    } catch (error) {
      logger.error('Error getting user preferences:', error);
      throw error;
    }
  }

  async logNotification(userId, companyId, channel, notification) {
    try {
      const query = `
        INSERT INTO notifications (user_id, company_id, type, channel, title, message, status)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
      `;
      
      await this.pool.query(query, [
        userId,
        companyId,
        'alert',
        channel,
        notification.title,
        notification.message,
        'sent'
      ]);
    } catch (error) {
      logger.error('Error logging notification:', error);
      // Don't throw here as we don't want to fail the notification process
    }
  }

  async close() {
    await this.pool.end();
  }
}

module.exports = DatabaseService;