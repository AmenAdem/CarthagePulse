const { Kafka } = require('kafkajs');

const kafka = new Kafka({
  clientId: 'stock-tracker-backend',
  brokers: [process.env.KAFKA_BOOTSTRAP_SERVERS || 'localhost:9092'],
});

module.exports = kafka;