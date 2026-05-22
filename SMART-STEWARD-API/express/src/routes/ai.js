const express = require('express');
const router = express.Router();
const multer = require('multer');
const { generateCloudResponse } = require('../services/cloudAi');
const path = require('path');
const fs = require('fs');
const os = require('os');

// Configure Multer for file uploads (store temporarily in memory or temp dir)
const upload = multer({ 
  dest: os.tmpdir(), // Use system temp directory
  limits: {
    fileSize: 50 * 1024 * 1024, // 50MB limit
  }
});

router.post('/', upload.single('media'), async (req, res) => {
  try {
    const file = req.file;

    if (!file) {
      return res.status(400).json({ error: 'Media file is required.' });
    }

    const aiResponse = await generateCloudResponse(file);
    
    // Clean up the uploaded file if it exists
    if (file && fs.existsSync(file.path)) {
      fs.unlinkSync(file.path);
    }

    res.json(aiResponse);

  } catch (error) {
    console.error('AI Service Error:', error);
    
    // Clean up file on error
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }
    
    if (error.message === 'OPENROUTER_API_KEY is not configured') {
      return res.status(500).json({ error: 'Server configuration error: OpenRouter API Key missing. Please set OPENROUTER_API_KEY in your .env file.' });
    }
    
    res.status(500).json({ error: error.message || 'Failed to generate AI response. Please try again later.' });
  }
});

module.exports = router;
