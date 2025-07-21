package com.stocktracker.notification.repository;

import com.stocktracker.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Notification entity
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications by user ID
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Find notifications by status
     */
    List<Notification> findByStatus(Notification.NotificationStatus status);

    /**
     * Find notifications by type and status
     */
    List<Notification> findByTypeAndStatus(
        Notification.NotificationType type, 
        Notification.NotificationStatus status
    );

    /**
     * Find failed notifications that can be retried
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<Notification> findFailedNotificationsForRetry();

    /**
     * Find scheduled notifications that are due
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'SCHEDULED' AND n.scheduledAt <= :now")
    List<Notification> findScheduledNotificationsDue(@Param("now") LocalDateTime now);

    /**
     * Count notifications by status
     */
    long countByStatus(Notification.NotificationStatus status);

    /**
     * Count notifications by user and status
     */
    long countByUserIdAndStatus(Long userId, Notification.NotificationStatus status);

    /**
     * Delete old notifications
     */
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffTime")
    int deleteOldNotifications(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find recent notifications for user
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotificationsByUser(
        @Param("userId") Long userId, 
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
}