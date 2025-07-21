const nodemailer = require('nodemailer');
const logger = require('../utils/logger');

class EmailService {
  constructor() {
    this.transporter = nodemailer.createTransporter({
      host: process.env.SMTP_HOST || 'smtp.gmail.com',
      port: 587,
      secure: false,
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
      },
    });
  }

  async sendNotification(email, notification) {
    try {
      const mailOptions = {
        from: process.env.SMTP_USER,
        to: email,
        subject: notification.title,
        html: `
          <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <h2 style="color: #1976d2;">${notification.title}</h2>
            <p style="font-size: 16px; line-height: 1.5;">${notification.message}</p>
            <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
            <p style="font-size: 12px; color: #666;">
              This is an automated notification from Stock Tracker. 
              You can manage your notification preferences in your account settings.
            </p>
          </div>
        `,
      };

      await this.transporter.sendMail(mailOptions);
      logger.info(`Email sent successfully to ${email}`);
    } catch (error) {
      logger.error(`Failed to send email to ${email}:`, error);
      throw error;
    }
  }
}

module.exports = EmailService;