const twilio = require('twilio');
const logger = require('../utils/logger');

class SMSService {
  constructor() {
    this.client = twilio(
      process.env.TWILIO_SID,
      process.env.TWILIO_TOKEN
    );
  }

  async sendNotification(phoneNumber, message) {
    try {
      if (!process.env.TWILIO_SID || !process.env.TWILIO_TOKEN) {
        logger.warn('Twilio credentials not configured, skipping SMS');
        return;
      }

      await this.client.messages.create({
        body: message,
        from: process.env.TWILIO_PHONE,
        to: phoneNumber,
      });

      logger.info(`SMS sent successfully to ${phoneNumber}`);
    } catch (error) {
      logger.error(`Failed to send SMS to ${phoneNumber}:`, error);
      throw error;
    }
  }
}

module.exports = SMSService;