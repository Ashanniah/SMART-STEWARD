const fs = require('fs');
const path = require('path');
const { OpenAI } = require('openai');
const dotenv = require('dotenv');
const ffmpeg = require('fluent-ffmpeg');
const ffmpegInstaller = require('@ffmpeg-installer/ffmpeg');
const ffprobeInstaller = require('@ffprobe-installer/ffprobe');
const os = require('os');

dotenv.config();

// Bundled binaries so video frame extraction works without a system FFmpeg install.
ffmpeg.setFfmpegPath(ffmpegInstaller.path);
ffmpeg.setFfprobePath(ffprobeInstaller.path);

// OpenRouter configuration
const openrouter = new OpenAI({
  apiKey: process.env.OPENROUTER_API_KEY,
  baseURL: 'https://openrouter.ai/api/v1',
});

// Load system prompt for AI guidance
let systemPrompt = '';
try {
  systemPrompt = fs.readFileSync(path.join(__dirname, '../skills/SKILLS.md'), 'utf8');
} catch (err) {
  console.error('Error reading SKILLS.md:', err);
  systemPrompt = 'You are a helpful AI assistant for Smart Steward.';
}

// OpenRouter model - Gemini 3 Flash Preview (no fallbacks)
const OPENROUTER_MODEL = 'google/gemini-3-flash-preview';

/**
 * Extract multiple frames evenly distributed across the video
 */
const extractFramesFromVideo = (videoPath, frameCount = 6) => {
  return new Promise((resolve, reject) => {
    const outputDir = os.tmpdir();
    const timestamp = Date.now();
    const frames = [];

    ffmpeg(videoPath)
      .on('filenames', (filenames) => {
        frames.push(...filenames);
      })
      .on('end', () => {
        resolve(frames.map(f => path.join(outputDir, f)));
      })
      .on('error', (err) => {
        console.error('FFmpeg extraction error:', err);
        reject(err);
      })
      .screenshots({
        count: frameCount, // Automatically evenly spaces frames
        filename: `frame-${timestamp}-%02d.jpg`,
        folder: outputDir,
        size: '640x360'
      });
  });
};

/**
 * Generate response using Gemini 3 Flash Preview via OpenRouter
 */
const generateCloudResponse = async (mediaFile) => {
  if (!process.env.OPENROUTER_API_KEY) {
    throw new Error('OPENROUTER_API_KEY is not configured. Please set it in your .env file.');
  }

  const contentArray = [];
  
  contentArray.push({ type: 'text', text: 'Analyze this media and classify the incident.' });

  const tempFramePaths = [];

  try {
    if (mediaFile) {
      const mimeType = mediaFile.mimetype;

      if (mimeType.startsWith('image/')) {
        // Handle Image
        const imageBuffer = fs.readFileSync(mediaFile.path);
        const base64Image = imageBuffer.toString('base64');
        
        contentArray.push({
          type: 'image_url',
          image_url: {
            url: `data:${mimeType};base64,${base64Image}`,
            detail: 'auto'
          }
        });

      } else if (mimeType.startsWith('video/')) {
        // Handle Video - Extract multiple frames for better analysis
        console.log(`Extracting frames from video: ${mediaFile.path}`);
        
        const frames = await extractFramesFromVideo(mediaFile.path, 6);
        tempFramePaths.push(...frames);

        // Add video-specific preprompt for temporal comparison
        contentArray.push({
          type: 'text',
          text: 'Analyze the physical kinetics, object trajectories, and structural changes across these sequential frames. Do not assume any intent or illegality. First, provide a strictly objective description of what physically happens (e.g., rapid vehicle movement, collisions, airborne debris, or atmospheric changes). Only after detailing the physical sequence, classify the event based purely on the visible evidence.'
        });

        // Add all extracted frames with specific labels
        for (let i = 0; i < frames.length; i++) {
          const framePath = frames[i];
          if (fs.existsSync(framePath)) {
            const imageBuffer = fs.readFileSync(framePath);
            const base64Image = imageBuffer.toString('base64');
            
            // Add a text label before each image to force sequential reasoning
            contentArray.push({
              type: 'text',
              text: `Frame ${i + 1}:`
            });
            
            contentArray.push({
              type: 'image_url',
              image_url: {
                url: `data:image/jpeg;base64,${base64Image}`,
                detail: 'auto'
              }
            });
          }
        }
      } else {
        throw new Error('Unsupported media type. Please upload an image or video.');
      }
    }

    const response = await openrouter.chat.completions.create({
      model: OPENROUTER_MODEL,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: contentArray },
      ],
    });

    const parsed = JSON.parse(response.choices[0].message.content);
    return enrichReportableFlag({
      ...parsed,
      file: mediaFile?.mimetype?.startsWith('video/') ? 'video' : 'image',
    });
  } finally {
    // Clean up extracted video frames
    for (const framePath of tempFramePaths) {
      if (framePath && fs.existsSync(framePath)) {
        try {
          fs.unlinkSync(framePath);
        } catch (e) {
          console.error('Failed to clean up temp frame:', e);
        }
      }
    }
  }
};

/**
 * Ensures clients can branch on `reportable` without string-matching category/agency.
 */
function enrichReportableFlag(parsed) {
  if (!parsed || typeof parsed !== 'object') return parsed;
  const category = String(parsed.category || '').trim().toLowerCase();
  const agency = String(parsed.assignedAgency || '').trim().toUpperCase();
  const nonIncident =
    category === 'not a valid incident' || agency === 'N/A';
  if (nonIncident) {
    parsed.reportable = false;
  } else if (parsed.reportable === undefined) {
    parsed.reportable = true;
  }
  return parsed;
}

module.exports = {
  generateCloudResponse,
  extractFramesFromVideo,
  enrichReportableFlag,
};
