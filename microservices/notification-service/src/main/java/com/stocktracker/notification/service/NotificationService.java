package com.stocktracker.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktracker.notification.entity.Notification;
import com.stocktracker.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Central notification service that handles all types of notifications
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // Metrics
    private final Counter notificationsProcessedCounter;
    private final Counter notificationsFailedCounter;
    private final Timer processingTimer;

    @Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            EmailService emailService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;

        // Initialize metrics
        this.notificationsProcessedCounter = Counter.builder("notifications.processed")
                .description("Number of notifications processed")
                .register(meterRegistry);

        this.notificationsFailedCounter = Counter.builder("notifications.failed")
                .description("Number of notifications failed")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("notifications.processing.time")
                .description("Time taken to process notifications")
                .register(meterRegistry);
    }

    /**
     * Kafka listener for stock alerts
     */
    @KafkaListener(
            topics = "${app.kafka.topics.stock-alerts}",
            groupId = "${spring.kafka.consumer.group-id}-alerts"
    )
    public void handleStockAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();

        try {
            logger.debug("Received stock alert from topic: {}", topic);

            Map<String, Object> alertData = objectMapper.readValue(message, Map.class);
            processStockAlert(alertData);

            acknowledgment.acknowledge();
            notificationsProcessedCounter.increment();

        } catch (Exception e) {
            logger.error("Error processing stock alert: {}", message, e);
            notificationsFailedCounter.increment();
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Kafka listener for price change alerts
     */
    @KafkaListener(
            topics = "${app.kafka.topics.price-alerts}",
            groupId = "${spring.kafka.consumer.group-id}-price"
    )
    public void handlePriceAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        try {
            logger.debug("Received price alert from topic: {}", topic);

            Map<String, Object> priceData = objectMapper.readValue(message, Map.class);
            processPriceAlert(priceData);

            acknowledgment.acknowledge();
            notificationsProcessedCounter.increment();

        } catch (Exception e) {
            logger.error("Error processing price alert: {}", message, e);
            notificationsFailedCounter.increment();
        }
    }

    /**
     * Kafka listener for news alerts
     */
    @KafkaListener(
            topics = "${app.kafka.topics.news-alerts}",
            groupId = "${spring.kafka.consumer.group-id}-news"
    )
    public void handleNewsAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        try {
            logger.debug("Received news alert from topic: {}", topic);

            Map<String, Object> newsData = objectMapper.readValue(message, Map.class);
            processNewsAlert(newsData);

            acknowledgment.acknowledge();
            notificationsProcessedCounter.increment();

        } catch (Exception e) {
            logger.error("Error processing news alert: {}", message, e);
            notificationsFailedCounter.increment();
        }
    }

    /**
     * Process stock alert data
     */
    private void processStockAlert(Map<String, Object> alertData) {
        String userId = alertData.get("userId").toString();
        String email = (String) alertData.get("email");
        String symbol = (String) alertData.get("symbol");
        String alertType = (String) alertData.get("alertType");
        Map<String, Object> stockData = (Map<String, Object>) alertData.get("stockData");

        // Create notification record
        Notification notification = new Notification(
                Long.parseLong(userId),
                Notification.NotificationType.EMAIL,
                String.format("Stock Alert: %s - %s", symbol, alertType),
                String.format("Alert for %s: %s", symbol, alertType)
        );
        notification.setRecipientEmail(email);
        notification.setTemplateName("stock-alert");
        notification.setTemplateData(objectMapper.writeValueAsString(alertData));

        // Save and send notification
        processNotificationAsync(notification, stockData);
    }

    /**
     * Process price alert data
     */
    private void processPriceAlert(Map<String, Object> priceData) {
        String userId = priceData.get("userId").toString();
        String email = (String) priceData.get("email");
        String symbol = (String) priceData.get("symbol");
        Double oldPrice = Double.valueOf(priceData.get("oldPrice").toString());
        Double newPrice = Double.valueOf(priceData.get("newPrice").toString());
        Double changePercent = Double.valueOf(priceData.get("changePercent").toString());

        // Create notification record
        String alertType = changePercent > 0 ? "Price Increase" : "Price Decrease";
        Notification notification = new Notification(
                Long.parseLong(userId),
                Notification.NotificationType.EMAIL,
                String.format("Price Alert: %s %s %.2f%%", symbol, alertType, Math.abs(changePercent)),
                String.format("Price change alert for %s", symbol)
        );
        notification.setRecipientEmail(email);
        notification.setTemplateName("price-change-alert");
        notification.setTemplateData(objectMapper.writeValueAsString(priceData));

        // Save and send notification
        notificationRepository.save(notification);
        sendEmailNotificationAsync(notification);
    }

    /**
     * Process news alert data
     */
    private void processNewsAlert(Map<String, Object> newsData) {
        String userId = newsData.get("userId").toString();
        String email = (String) newsData.get("email");
        String headline = (String) newsData.get("headline");
        String summary = (String) newsData.get("summary");
        String url = (String) newsData.get("url");

        // Create notification record
        Notification notification = new Notification(
                Long.parseLong(userId),
                Notification.NotificationType.EMAIL,
                "News Alert: " + headline,
                summary
        );
        notification.setRecipientEmail(email);
        notification.setTemplateName("news-alert");
        notification.setTemplateData(objectMapper.writeValueAsString(newsData));

        // Save and send notification
        notificationRepository.save(notification);
        sendEmailNotificationAsync(notification);
    }

    /**
     * Process notification asynchronously
     */
    @Async
    public void processNotificationAsync(Notification notification, Map<String, Object> data) {
        try {
            Notification savedNotification = notificationRepository.save(notification);
            
            boolean sent = false;
            switch (notification.getType()) {
                case EMAIL:
                    sent = emailService.sendNotification(savedNotification);
                    break;
                case SMS:
                    // Implement SMS sending
                    sent = false; // TODO: Implement SMS service
                    break;
                case PUSH:
                    // Implement push notification sending
                    sent = false; // TODO: Implement push service
                    break;
                default:
                    logger.warn("Unsupported notification type: {}", notification.getType());
            }

            // Update notification status
            if (sent) {
                savedNotification.setStatus(Notification.NotificationStatus.SENT);
                savedNotification.setSentAt(LocalDateTime.now());
            } else {
                savedNotification.setStatus(Notification.NotificationStatus.FAILED);
                savedNotification.setErrorMessage("Failed to send notification");
            }
            
            notificationRepository.save(savedNotification);
            
        } catch (Exception e) {
            logger.error("Error processing notification async", e);
            notification.setStatus(Notification.NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
        }
    }

    /**
     * Send email notification asynchronously
     */
    @Async
    public CompletableFuture<Boolean> sendEmailNotificationAsync(Notification notification) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean sent = emailService.sendNotification(notification);
                
                if (sent) {
                    notification.setStatus(Notification.NotificationStatus.SENT);
                    notification.setSentAt(LocalDateTime.now());
                } else {
                    notification.setStatus(Notification.NotificationStatus.FAILED);
                    notification.setErrorMessage("Failed to send email");
                }
                
                notificationRepository.save(notification);
                return sent;
                
            } catch (Exception e) {
                logger.error("Error sending email notification", e);
                notification.setStatus(Notification.NotificationStatus.FAILED);
                notification.setErrorMessage(e.getMessage());
                notificationRepository.save(notification);
                return false;
            }
        });
    }

    /**
     * Create and send custom notification
     */
    public Notification createNotification(
            Long userId,
            Notification.NotificationType type,
            String subject,
            String content,
            String recipient) {
        
        Notification notification = new Notification(userId, type, subject, content);
        
        switch (type) {
            case EMAIL:
                notification.setRecipientEmail(recipient);
                break;
            case SMS:
                notification.setRecipientPhone(recipient);
                break;
            case PUSH:
                notification.setRecipientDeviceToken(recipient);
                break;
        }
        
        Notification savedNotification = notificationRepository.save(notification);
        processNotificationAsync(savedNotification, Map.of());
        
        return savedNotification;
    }

    /**
     * Retry failed notifications
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void retryFailedNotifications() {
        try {
            List<Notification> failedNotifications = notificationRepository.findFailedNotificationsForRetry();
            
            logger.info("Found {} failed notifications to retry", failedNotifications.size());
            
            for (Notification notification : failedNotifications) {
                if (notification.canRetry()) {
                    notification.incrementRetryCount();
                    notification.setStatus(Notification.NotificationStatus.PENDING);
                    notificationRepository.save(notification);
                    
                    processNotificationAsync(notification, Map.of());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during retry of failed notifications", e);
        }
    }

    /**
     * Send scheduled notifications
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void sendScheduledNotifications() {
        try {
            List<Notification> scheduledNotifications = 
                notificationRepository.findScheduledNotificationsDue(LocalDateTime.now());
            
            logger.debug("Found {} scheduled notifications to send", scheduledNotifications.size());
            
            for (Notification notification : scheduledNotifications) {
                notification.setStatus(Notification.NotificationStatus.PENDING);
                notificationRepository.save(notification);
                
                processNotificationAsync(notification, Map.of());
            }
            
        } catch (Exception e) {
            logger.error("Error sending scheduled notifications", e);
        }
    }

    /**
     * Clean up old notifications
     */
    @Scheduled(cron = "0 0 1 * * ?") // Daily at 1 AM
    public void cleanupOldNotifications() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
            int deletedCount = notificationRepository.deleteOldNotifications(cutoffTime);
            logger.info("Cleaned up {} old notifications", deletedCount);
        } catch (Exception e) {
            logger.error("Error during notification cleanup", e);
        }
    }

    /**
     * Get notification statistics
     */
    public Map<String, Object> getNotificationStats() {
        return Map.of(
                "totalNotifications", notificationRepository.count(),
                "sentNotifications", notificationRepository.countByStatus(Notification.NotificationStatus.SENT),
                "failedNotifications", notificationRepository.countByStatus(Notification.NotificationStatus.FAILED),
                "pendingNotifications", notificationRepository.countByStatus(Notification.NotificationStatus.PENDING)
        );
    }
}