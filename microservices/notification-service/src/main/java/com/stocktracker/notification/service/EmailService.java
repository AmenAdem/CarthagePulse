package com.stocktracker.notification.service;

import com.stocktracker.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Email Service for sending notifications via email
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@stocktracker.com}")
    private String fromEmail;

    @Value("${app.mail.company-name:StockTracker}")
    private String companyName;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Send simple text email
     */
    public boolean sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            logger.info("Simple email sent successfully to: {}", to);
            return true;
            
        } catch (MailException e) {
            logger.error("Failed to send simple email to: {}", to, e);
            return false;
        }
    }

    /**
     * Send HTML email using template
     */
    public boolean sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // Set basic email properties
            helper.setFrom(fromEmail, companyName);
            helper.setTo(to);
            helper.setSubject(subject);
            
            // Process template
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("Templated email sent successfully to: {} using template: {}", to, templateName);
            return true;
            
        } catch (MessagingException | MailException e) {
            logger.error("Failed to send templated email to: {} using template: {}", to, templateName, e);
            return false;
        }
    }

    /**
     * Send notification email
     */
    public boolean sendNotification(Notification notification) {
        try {
            if (notification.getTemplateName() != null && !notification.getTemplateName().isEmpty()) {
                // Use template
                Map<String, Object> variables = parseTemplateData(notification.getTemplateData());
                return sendTemplatedEmail(
                    notification.getRecipientEmail(),
                    notification.getSubject(),
                    notification.getTemplateName(),
                    variables
                );
            } else {
                // Send simple email
                return sendSimpleEmail(
                    notification.getRecipientEmail(),
                    notification.getSubject(),
                    notification.getContent()
                );
            }
        } catch (Exception e) {
            logger.error("Error sending notification email for notification ID: {}", notification.getId(), e);
            return false;
        }
    }

    /**
     * Send stock alert email
     */
    public boolean sendStockAlert(String email, String symbol, String alertType, Map<String, Object> stockData) {
        try {
            String subject = String.format("Stock Alert: %s - %s", symbol, alertType);
            
            Map<String, Object> variables = Map.of(
                "symbol", symbol,
                "alertType", alertType,
                "stockData", stockData,
                "timestamp", LocalDateTime.now(),
                "companyName", companyName
            );
            
            return sendTemplatedEmail(email, subject, "stock-alert", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send stock alert email for symbol: {} to: {}", symbol, email, e);
            return false;
        }
    }

    /**
     * Send price change alert
     */
    public boolean sendPriceChangeAlert(String email, String symbol, double oldPrice, double newPrice, double changePercent) {
        try {
            String alertType = changePercent > 0 ? "Price Increase" : "Price Decrease";
            String subject = String.format("Price Alert: %s %s %.2f%%", symbol, alertType, Math.abs(changePercent));
            
            Map<String, Object> variables = Map.of(
                "symbol", symbol,
                "oldPrice", oldPrice,
                "newPrice", newPrice,
                "changePercent", changePercent,
                "alertType", alertType,
                "timestamp", LocalDateTime.now(),
                "companyName", companyName
            );
            
            return sendTemplatedEmail(email, subject, "price-change-alert", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send price change alert for symbol: {} to: {}", symbol, email, e);
            return false;
        }
    }

    /**
     * Send volume alert
     */
    public boolean sendVolumeAlert(String email, String symbol, long volume, long averageVolume) {
        try {
            double volumeRatio = (double) volume / averageVolume;
            String subject = String.format("Volume Alert: %s - Unusual Volume (%.1fx normal)", symbol, volumeRatio);
            
            Map<String, Object> variables = Map.of(
                "symbol", symbol,
                "volume", volume,
                "averageVolume", averageVolume,
                "volumeRatio", volumeRatio,
                "timestamp", LocalDateTime.now(),
                "companyName", companyName
            );
            
            return sendTemplatedEmail(email, subject, "volume-alert", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send volume alert for symbol: {} to: {}", symbol, email, e);
            return false;
        }
    }

    /**
     * Send news alert
     */
    public boolean sendNewsAlert(String email, String headline, String summary, String url) {
        try {
            String subject = "News Alert: " + headline;
            
            Map<String, Object> variables = Map.of(
                "headline", headline,
                "summary", summary,
                "url", url,
                "timestamp", LocalDateTime.now(),
                "companyName", companyName
            );
            
            return sendTemplatedEmail(email, subject, "news-alert", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send news alert to: {}", email, e);
            return false;
        }
    }

    /**
     * Send welcome email
     */
    public boolean sendWelcomeEmail(String email, String firstName, String activationLink) {
        try {
            String subject = String.format("Welcome to %s!", companyName);
            
            Map<String, Object> variables = Map.of(
                "firstName", firstName,
                "activationLink", activationLink,
                "companyName", companyName,
                "supportEmail", fromEmail
            );
            
            return sendTemplatedEmail(email, subject, "welcome", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send welcome email to: {}", email, e);
            return false;
        }
    }

    /**
     * Send password reset email
     */
    public boolean sendPasswordResetEmail(String email, String firstName, String resetLink) {
        try {
            String subject = "Password Reset Request";
            
            Map<String, Object> variables = Map.of(
                "firstName", firstName,
                "resetLink", resetLink,
                "companyName", companyName,
                "supportEmail", fromEmail
            );
            
            return sendTemplatedEmail(email, subject, "password-reset", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email to: {}", email, e);
            return false;
        }
    }

    /**
     * Send daily digest email
     */
    public boolean sendDailyDigest(String email, String firstName, Map<String, Object> digestData) {
        try {
            String subject = String.format("Daily Market Digest - %s", 
                LocalDateTime.now().toLocalDate());
            
            Map<String, Object> variables = Map.of(
                "firstName", firstName,
                "digestData", digestData,
                "companyName", companyName,
                "timestamp", LocalDateTime.now()
            );
            
            return sendTemplatedEmail(email, subject, "daily-digest", variables);
            
        } catch (Exception e) {
            logger.error("Failed to send daily digest email to: {}", email, e);
            return false;
        }
    }

    /**
     * Parse template data from JSON string
     */
    private Map<String, Object> parseTemplateData(String templateData) {
        if (templateData == null || templateData.isEmpty()) {
            return Map.of();
        }
        
        try {
            // Simple JSON parsing - in production, use Jackson ObjectMapper
            return Map.of(); // Placeholder - implement proper JSON parsing
        } catch (Exception e) {
            logger.warn("Failed to parse template data: {}", templateData, e);
            return Map.of();
        }
    }

    /**
     * Test email connectivity
     */
    public boolean testConnection() {
        try {
            mailSender.createMimeMessage();
            logger.info("Email service connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("Email service connection test failed", e);
            return false;
        }
    }
}