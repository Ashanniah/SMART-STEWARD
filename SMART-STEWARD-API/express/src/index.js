const express = require('express');
const dotenv = require('dotenv');
const aiRoutes = require('./routes/ai');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.use('/ai', aiRoutes);

app.listen(PORT, () => {
  console.log(`Smart Steward API running on port ${PORT}`);
});
