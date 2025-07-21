const express = require('express');
const pool = require('../config/database');
const logger = require('../utils/logger');

const router = express.Router();

// Get user's notifications
router.get('/', async (req, res) => {
  try {
    const { limit = 50, offset = 0 } = req.query;

    const result = await pool.query(`
      SELECT n.id, n.type, n.channel, n.title, n.message, n.sent_at, n.status,
             c.symbol as company_symbol, c.name as company_name
      FROM notifications n
      LEFT JOIN companies c ON n.company_id = c.id
      WHERE n.user_id = $1
      ORDER BY n.sent_at DESC
      LIMIT $2 OFFSET $3
    `, [req.user.userId, limit, offset]);

    res.json(result.rows);
  } catch (error) {
    logger.error('Error fetching notifications:', error);
    res.status(500).json({ error: 'Failed to fetch notifications' });
  }
});

// Mark notifications as read
router.put('/read', async (req, res) => {
  try {
    const { notificationIds } = req.body;

    if (!Array.isArray(notificationIds) || notificationIds.length === 0) {
      return res.status(400).json({ error: 'Invalid notification IDs' });
    }

    const placeholders = notificationIds.map((_, index) => `$${index + 2}`).join(',');
    
    await pool.query(
      `UPDATE notifications SET status = 'read' WHERE user_id = $1 AND id IN (${placeholders})`,
      [req.user.userId, ...notificationIds]
    );

    res.json({ message: 'Notifications marked as read' });
  } catch (error) {
    logger.error('Error marking notifications as read:', error);
    res.status(500).json({ error: 'Failed to mark notifications as read' });
  }
});

module.exports = router;