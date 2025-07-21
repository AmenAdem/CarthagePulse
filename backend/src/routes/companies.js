const express = require('express');
const pool = require('../config/database');
const logger = require('../utils/logger');

const router = express.Router();

// Get all companies
router.get('/', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT id, symbol, name, sector, market_cap, description FROM companies ORDER BY name'
    );
    res.json(result.rows);
  } catch (error) {
    logger.error('Error fetching companies:', error);
    res.status(500).json({ error: 'Failed to fetch companies' });
  }
});

// Get user's tracked companies
router.get('/tracked', async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT c.id, c.symbol, c.name, c.sector, c.market_cap, c.description,
             uc.created_at as tracked_since
      FROM companies c
      JOIN user_companies uc ON c.id = uc.company_id
      WHERE uc.user_id = $1
      ORDER BY c.name
    `, [req.user.userId]);
    
    res.json(result.rows);
  } catch (error) {
    logger.error('Error fetching tracked companies:', error);
    res.status(500).json({ error: 'Failed to fetch tracked companies' });
  }
});

// Add company to user's tracking list
router.post('/track/:companyId', async (req, res) => {
  try {
    const { companyId } = req.params;
    const userId = req.user.userId;

    // Check if company exists
    const companyResult = await pool.query('SELECT id FROM companies WHERE id = $1', [companyId]);
    if (companyResult.rows.length === 0) {
      return res.status(404).json({ error: 'Company not found' });
    }

    // Check if already tracking
    const existingResult = await pool.query(
      'SELECT id FROM user_companies WHERE user_id = $1 AND company_id = $2',
      [userId, companyId]
    );

    if (existingResult.rows.length > 0) {
      return res.status(400).json({ error: 'Company already being tracked' });
    }

    // Add to tracking list
    await pool.query(
      'INSERT INTO user_companies (user_id, company_id) VALUES ($1, $2)',
      [userId, companyId]
    );

    logger.info(`User ${userId} started tracking company ${companyId}`);
    res.status(201).json({ message: 'Company added to tracking list' });
  } catch (error) {
    logger.error('Error adding company to tracking list:', error);
    res.status(500).json({ error: 'Failed to add company to tracking list' });
  }
});

// Remove company from user's tracking list
router.delete('/track/:companyId', async (req, res) => {
  try {
    const { companyId } = req.params;
    const userId = req.user.userId;

    const result = await pool.query(
      'DELETE FROM user_companies WHERE user_id = $1 AND company_id = $2 RETURNING id',
      [userId, companyId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Company not found in tracking list' });
    }

    logger.info(`User ${userId} stopped tracking company ${companyId}`);
    res.json({ message: 'Company removed from tracking list' });
  } catch (error) {
    logger.error('Error removing company from tracking list:', error);
    res.status(500).json({ error: 'Failed to remove company from tracking list' });
  }
});

// Get company stock prices
router.get('/:companyId/prices', async (req, res) => {
  try {
    const { companyId } = req.params;
    const { from, to, limit = 100 } = req.query;

    let query = `
      SELECT price, volume, market_cap, timestamp, source
      FROM stock_prices 
      WHERE company_id = $1
    `;
    const params = [companyId];

    if (from) {
      params.push(from);
      query += ` AND timestamp >= $${params.length}`;
    }

    if (to) {
      params.push(to);
      query += ` AND timestamp <= $${params.length}`;
    }

    query += ` ORDER BY timestamp DESC LIMIT $${params.length + 1}`;
    params.push(limit);

    const result = await pool.query(query, params);
    res.json(result.rows);
  } catch (error) {
    logger.error('Error fetching stock prices:', error);
    res.status(500).json({ error: 'Failed to fetch stock prices' });
  }
});

// Get company news
router.get('/:companyId/news', async (req, res) => {
  try {
    const { companyId } = req.params;
    const { limit = 20 } = req.query;

    const result = await pool.query(`
      SELECT id, title, content, url, source, sentiment_score, published_at
      FROM news_articles
      WHERE company_id = $1
      ORDER BY published_at DESC
      LIMIT $2
    `, [companyId, limit]);

    res.json(result.rows);
  } catch (error) {
    logger.error('Error fetching company news:', error);
    res.status(500).json({ error: 'Failed to fetch company news' });
  }
});

module.exports = router;