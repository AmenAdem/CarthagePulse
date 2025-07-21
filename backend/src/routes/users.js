const express = require('express');
const pool = require('../config/database');
const logger = require('../utils/logger');

const router = express.Router();

// Get user profile
router.get('/profile', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT id, email, first_name, last_name, created_at FROM users WHERE id = $1',
      [req.user.userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.json(result.rows[0]);
  } catch (error) {
    logger.error('Error fetching user profile:', error);
    res.status(500).json({ error: 'Failed to fetch user profile' });
  }
});

// Get notification preferences
router.get('/preferences', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM user_notification_preferences WHERE user_id = $1',
      [req.user.userId]
    );

    if (result.rows.length === 0) {
      // Create default preferences if they don't exist
      const defaultPrefs = await pool.query(
        'INSERT INTO user_notification_preferences (user_id) VALUES ($1) RETURNING *',
        [req.user.userId]
      );
      return res.json(defaultPrefs.rows[0]);
    }

    res.json(result.rows[0]);
  } catch (error) {
    logger.error('Error fetching notification preferences:', error);
    res.status(500).json({ error: 'Failed to fetch notification preferences' });
  }
});

// Update notification preferences
router.put('/preferences', async (req, res) => {
  try {
    const {
      email_enabled,
      sms_enabled,
      push_enabled,
      price_change_threshold,
      phone_number
    } = req.body;

    const result = await pool.query(`
      UPDATE user_notification_preferences 
      SET email_enabled = $1, sms_enabled = $2, push_enabled = $3, 
          price_change_threshold = $4, phone_number = $5, updated_at = CURRENT_TIMESTAMP
      WHERE user_id = $6 
      RETURNING *
    `, [email_enabled, sms_enabled, push_enabled, price_change_threshold, phone_number, req.user.userId]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Preferences not found' });
    }

    logger.info(`User ${req.user.userId} updated notification preferences`);
    res.json(result.rows[0]);
  } catch (error) {
    logger.error('Error updating notification preferences:', error);
    res.status(500).json({ error: 'Failed to update notification preferences' });
  }
});

module.exports = router;