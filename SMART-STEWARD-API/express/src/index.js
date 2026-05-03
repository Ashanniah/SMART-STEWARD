const express = require('express');
const dotenv = require('dotenv');
const aiRoutes = require('./routes/ai');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

app.use(express.json());

app.use('/ai', aiRoutes);

app.listen(PORT, HOST, () => {
  console.log(`Smart Steward API running at http://${HOST}:${PORT}`);
});
