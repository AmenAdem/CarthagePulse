package com.stocktracker.notification.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification Entity for tracking sent notifications
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @NotNull
    @Column(nullable = false)
    private String subject;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "recipient_device_token")
    private String recipientDeviceToken;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "template_data", columnDefinition = "TEXT")
    private String templateData; // JSON string

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public Notification() {}

    public Notification(Long userId, NotificationType type, String subject, String content) {
        this.userId = userId;
        this.type = type;
        this.subject = subject;
        this.content = content;
        this.status = NotificationStatus.PENDING;
    }

    // Enums
    public enum NotificationType {
        EMAIL,
        SMS,
        PUSH,
        IN_APP
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED,
        CANCELLED,
        SCHEDULED
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getRecipientDeviceToken() {
        return recipientDeviceToken;
    }

    public void setRecipientDeviceToken(String recipientDeviceToken) {
        this.recipientDeviceToken = recipientDeviceToken;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateData() {
        return templateData;
    }

    public void setTemplateData(String templateData) {
        this.templateData = templateData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", userId=" + userId +
                ", type=" + type +
                ", status=" + status +
                ", subject='" + subject + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}