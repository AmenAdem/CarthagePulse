const { Kafka } = require('kafkajs');
const EmailService = require('./services/EmailService');
const SMSService = require('./services/SMSService');
const DatabaseService = require('./services/DatabaseService');
const logger = require('./utils/logger');
require('dotenv').config();

class NotificationService {
  constructor() {
    this.kafka = new Kafka({
      clientId: 'notification-service',
      brokers: [process.env.KAFKA_BOOTSTRAP_SERVERS || 'localhost:9092'],
    });

    this.consumer = this.kafka.consumer({ groupId: 'notification-service' });
    this.emailService = new EmailService();
    this.smsService = new SMSService();
    this.dbService = new DatabaseService();
  }

  async start() {
    try {
      await this.consumer.connect();
      await this.consumer.subscribe({ topics: ['stock-alerts'] });

      await this.consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
          try {
            const alertData = JSON.parse(message.value.toString());
            await this.processAlert(alertData);
          } catch (error) {
            logger.error('Error processing alert message:', error);
          }
        },
      });

      logger.info('Notification service started successfully');
    } catch (error) {
      logger.error('Failed to start notification service:', error);
      throw error;
    }
  }

  async processAlert(alertData) {
    try {
      logger.info(`Processing alert: ${alertData.type} for company ${alertData.symbol}`);

      // Get users tracking this company
      const users = await this.dbService.getUsersTrackingCompany(alertData.companyId);

      for (const user of users) {
        const preferences = await this.dbService.getUserPreferences(user.id);
        
        // Check if user should receive this alert
        if (this.shouldSendAlert(alertData, preferences)) {
          await this.sendNotifications(user, alertData, preferences);
        }
      }
    } catch (error) {
      logger.error('Error processing alert:', error);
    }
  }

  shouldSendAlert(alertData, preferences) {
    if (alertData.type === 'price_alert') {
      return Math.abs(alertData.priceChange) >= preferences.price_change_threshold;
    }
    return true; // Send all other alerts
  }

  async sendNotifications(user, alertData, preferences) {
    const notification = this.createNotificationMessage(alertData);

    // Send email notification
    if (preferences.email_enabled) {
      try {
        await this.emailService.sendNotification(user.email, notification);
        await this.dbService.logNotification(user.id, alertData.companyId, 'email', notification);
        logger.info(`Email sent to ${user.email} for ${alertData.symbol}`);
      } catch (error) {
        logger.error(`Failed to send email to ${user.email}:`, error);
      }
    }

    // Send SMS notification
    if (preferences.sms_enabled && preferences.phone_number) {
      try {
        await this.smsService.sendNotification(preferences.phone_number, notification.message);
        await this.dbService.logNotification(user.id, alertData.companyId, 'sms', notification);
        logger.info(`SMS sent to ${preferences.phone_number} for ${alertData.symbol}`);
      } catch (error) {
        logger.error(`Failed to send SMS to ${preferences.phone_number}:`, error);
      }
    }

    // Log push notification (would be sent via WebSocket in real implementation)
    if (preferences.push_enabled) {
      await this.dbService.logNotification(user.id, alertData.companyId, 'push', notification);
      logger.info(`Push notification logged for user ${user.id} for ${alertData.symbol}`);
    }
  }

  createNotificationMessage(alertData) {
    switch (alertData.type) {
      case 'price_alert':
        return {
          title: `${alertData.symbol} Price Alert`,
          message: `${alertData.symbol} has changed by ${alertData.priceChange.toFixed(2)}%. Current price: $${alertData.currentPrice.toFixed(2)}`
        };
      case 'news_alert':
        return {
          title: `${alertData.symbol} News Alert`,
          message: `${alertData.sentiment.charAt(0).toUpperCase() + alertData.sentiment.slice(1)} news for ${alertData.symbol}: ${alertData.title}`
        };
      default:
        return {
          title: `${alertData.symbol} Alert`,
          message: `New alert for ${alertData.symbol}`
        };
    }
  }

  async stop() {
    await this.consumer.disconnect();
    await this.dbService.close();
    logger.info('Notification service stopped');
  }
}

// Handle graceful shutdown
process.on('SIGTERM', async () => {
  logger.info('SIGTERM received, shutting down gracefully');
  await notificationService.stop();
  process.exit(0);
});

process.on('SIGINT', async () => {
  logger.info('SIGINT received, shutting down gracefully');
  await notificationService.stop();
  process.exit(0);
});

// Start the service
const notificationService = new NotificationService();
notificationService.start().catch((error) => {
  logger.error('Failed to start notification service:', error);
  process.exit(1);
});