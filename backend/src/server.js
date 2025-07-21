const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
require('dotenv').config();

const authRoutes = require('./routes/auth');
const companyRoutes = require('./routes/companies');
const userRoutes = require('./routes/users');
const notificationRoutes = require('./routes/notifications');
const { authenticateToken } = require('./middleware/auth');
const logger = require('./utils/logger');
const kafkaService = require('./services/kafka');
const redisClient = require('./config/redis');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: process.env.FRONTEND_URL || "http://localhost:3002",
    methods: ["GET", "POST"]
  }
});

// Middleware
app.use(helmet());
app.use(compression());
app.use(cors({
  origin: process.env.FRONTEND_URL || "http://localhost:3002",
  credentials: true
}));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
});
app.use(limiter);

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/companies', authenticateToken, companyRoutes);
app.use('/api/users', authenticateToken, userRoutes);
app.use('/api/notifications', authenticateToken, notificationRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    memoryUsage: process.memoryUsage()
  });
});

// Socket.io connection handling
const connectedUsers = new Map();

io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  if (!token) {
    return next(new Error('Authentication error'));
  }
  
  const jwt = require('jsonwebtoken');
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'fallback-secret');
    socket.userId = decoded.userId;
    next();
  } catch (err) {
    next(new Error('Authentication error'));
  }
});

io.on('connection', (socket) => {
  logger.info(`User ${socket.userId} connected`);
  connectedUsers.set(socket.userId, socket.id);
  
  socket.on('subscribe-to-companies', (companyIds) => {
    companyIds.forEach(companyId => {
      socket.join(`company-${companyId}`);
    });
    logger.info(`User ${socket.userId} subscribed to companies: ${companyIds}`);
  });
  
  socket.on('disconnect', () => {
    logger.info(`User ${socket.userId} disconnected`);
    connectedUsers.delete(socket.userId);
  });
});

// Kafka consumer for real-time updates
const startKafkaConsumer = async () => {
  try {
    const consumer = kafkaService.consumer({ groupId: 'backend-realtime' });
    await consumer.connect();
    await consumer.subscribe({ topics: ['stock-prices', 'news-articles', 'economic-indicators'] });
    
    await consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        try {
          const data = JSON.parse(message.value.toString());
          
          switch (topic) {
            case 'stock-prices':
              io.to(`company-${data.companyId}`).emit('price-update', data);
              break;
            case 'news-articles':
              io.to(`company-${data.companyId}`).emit('news-update', data);
              break;
            case 'economic-indicators':
              io.emit('economic-update', data);
              break;
          }
        } catch (error) {
          logger.error('Error processing Kafka message:', error);
        }
      },
    });
    
    logger.info('Kafka consumer started successfully');
  } catch (error) {
    logger.error('Failed to start Kafka consumer:', error);
  }
};

// Error handling
app.use((err, req, res, next) => {
  logger.error('Unhandled error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

// Graceful shutdown
process.on('SIGTERM', async () => {
  logger.info('SIGTERM received, shutting down gracefully');
  server.close(() => {
    logger.info('Process terminated');
  });
});

const PORT = process.env.PORT || 3000;

server.listen(PORT, async () => {
  logger.info(`Server running on port ${PORT}`);
  
  // Initialize services
  try {
    await redisClient.connect();
    logger.info('Redis connected');
    
    await startKafkaConsumer();
  } catch (error) {
    logger.error('Failed to initialize services:', error);
  }
});

module.exports = { app, io };