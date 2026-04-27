const express = require('express');
const router = express.Router();
const multer = require('multer');
const { generateResponse } = require('../services/openai');
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
    const { message } = req.body;
    const file = req.file;

    if (!message && !file) {
      return res.status(400).json({ error: 'Message or media file is required.' });
    }

    const aiResponse = await generateResponse(message, file);
    
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
    
    if (error.message === 'OPENAI_API_KEY is not configured') {
      return res.status(500).json({ error: 'Server configuration error: OpenAI API Key missing.' });
    }
    
    res.status(500).json({ error: error.message || 'Failed to generate AI response. Please try again later.' });
  }
});

module.exports = router;
