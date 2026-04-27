const fs = require('fs');
const path = require('path');
const { OpenAI } = require('openai');
const dotenv = require('dotenv');
const ffmpeg = require('fluent-ffmpeg');
const ffmpegInstaller = require('@ffmpeg-installer/ffmpeg');
const os = require('os');

dotenv.config();

// Set ffmpeg path
ffmpeg.setFfmpegPath(ffmpegInstaller.path);

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

let systemPrompt = '';
try {
  systemPrompt = fs.readFileSync(path.join(__dirname, '../skills/SKILLS.md'), 'utf8');
} catch (err) {
  console.error('Error reading SKILLS.md:', err);
  systemPrompt = 'You are a helpful AI assistant for Smart Steward.'; // Fallback
}

// Helper function to extract a frame from a video
const extractFrameFromVideo = (videoPath) => {
  return new Promise((resolve, reject) => {
    const outputFileName = `frame-${Date.now()}.jpg`;
    const outputPath = path.join(os.tmpdir(), outputFileName);

    ffmpeg(videoPath)
      .screenshots({
        timestamps: ['50%'], // Take a screenshot halfway through the video
        filename: outputFileName,
        folder: os.tmpdir(),
        size: '1280x720' // Resize to a reasonable dimension for the API
      })
      .on('end', () => {
        resolve(outputPath);
      })
      .on('error', (err) => {
        reject(err);
      });
  });
};

const generateResponse = async (message, mediaFile) => {
  if (!process.env.OPENAI_API_KEY) {
    throw new Error('OPENAI_API_KEY is not configured');
  }

  const contentArray = [];
  
  if (message) {
    contentArray.push({ type: 'text', text: message });
  } else {
     contentArray.push({ type: 'text', text: 'Analyze this media and classify the incident.' });
  }

  let tempFramePath = null;

  try {
    if (mediaFile) {
      const mimeType = mediaFile.mimetype;
      let base64Image = '';

      if (mimeType.startsWith('image/')) {
        // Handle Image
        const imageBuffer = fs.readFileSync(mediaFile.path);
        base64Image = imageBuffer.toString('base64');
        
        contentArray.push({
          type: 'image_url',
          image_url: {
            url: `data:${mimeType};base64,${base64Image}`,
            detail: 'auto'
          }
        });

      } else if (mimeType.startsWith('video/')) {
        // Handle Video - Extract a frame
        console.log(`Extracting frame from video: ${mediaFile.path}`);
        tempFramePath = await extractFrameFromVideo(mediaFile.path);
        
        const imageBuffer = fs.readFileSync(tempFramePath);
        base64Image = imageBuffer.toString('base64');

        contentArray.push({
           type: 'text', text: '(Note: A single frame was extracted from the middle of the provided video for analysis.)'
        });

        contentArray.push({
          type: 'image_url',
          image_url: {
            url: `data:image/jpeg;base64,${base64Image}`,
            detail: 'auto'
          }
        });
      } else {
        throw new Error('Unsupported media type. Please upload an image or video.');
      }
    }

    const response = await openai.chat.completions.create({
      model: 'gpt-4o-mini',
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: contentArray },
      ],
    });

    return {
      response: JSON.parse(response.choices[0].message.content),
      usage: response.usage,
    };
  } finally {
     // Clean up extracted video frame if it exists
     if (tempFramePath && fs.existsSync(tempFramePath)) {
        try {
          fs.unlinkSync(tempFramePath);
        } catch (e) {
          console.error('Failed to clean up temp frame:', e);
        }
     }
  }
};

module.exports = {
  generateResponse,
};
