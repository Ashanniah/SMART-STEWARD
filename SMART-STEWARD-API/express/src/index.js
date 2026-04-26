const express = require('express');
const dotenv = require('dotenv');
const aiRoutes = require('./routes/ai');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;
const API_KEY = process.env.API_KEY;

app.use(express.json());

// Simple API Key Authentication Middleware
const authenticateKey = (req, res, next) => {
  const reqApiKey = req.header('x-api-key');

  if (!API_KEY) {
    console.warn('Warning: API_KEY is not set in environment variables.');
  }

  if (API_KEY && reqApiKey !== API_KEY) {
    return res.status(401).json({ error: 'Unauthorized: Invalid API Key' });
  }

  next();
};

app.use(authenticateKey);

app.use('/ai', aiRoutes);

app.listen(PORT, () => {
  console.log(`Smart Steward API running on port ${PORT}`);
});
