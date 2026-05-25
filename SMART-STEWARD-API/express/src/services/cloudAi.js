const fs = require('fs');
const path = require('path');
const { OpenAI } = require('openai');
const dotenv = require('dotenv');

// Import the official Google Generative AI SDK for native video processing
const { GoogleGenerativeAI } = require('@google/generative-ai');
const { GoogleAIFileManager } = require('@google/generative-ai/server');

dotenv.config();

// Initialize OpenRouter client (For Images)
const openrouter = new OpenAI({
  apiKey: process.env.OPENROUTER_API_KEY,
  baseURL: 'https://openrouter.ai/api/v1',
});

// Initialize Native Gemini clients (For direct video uploads)
let genAI = null;
let fileManager = null;

if (process.env.GEMINI_API_KEY) {
  genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
  fileManager = new GoogleAIFileManager(process.env.GEMINI_API_KEY);
}

// Load system prompt for AI guidance
let systemPrompt = '';
try {
  systemPrompt = fs.readFileSync(path.join(__dirname, '../skills/SKILLS.md'), 'utf8');
} catch (err) {
  console.error('Error reading SKILLS.md:', err);
  systemPrompt = 'You are a helpful AI assistant for Smart Steward.';
}

const OPENROUTER_MODEL = 'google/gemini-3-flash-preview';
const NATIVE_GEMINI_MODEL = 'gemini-3-flash-preview';

/**
 * Generate response routing images through OpenRouter and uploading video directly to Gemini
 */
const generateCloudResponse = async (mediaFile) => {
  if (!mediaFile) {
    throw new Error('No media file provided.');
  }

  const mimeType = mediaFile.mimetype;
  let uploadResponseFile = null;

  try {
    // ==========================================
    // 1. IMAGE ROUTE (OpenRouter)
    // ==========================================
    if (mimeType.startsWith('image/')) {
      if (!process.env.OPENROUTER_API_KEY) {
        throw new Error('OPENROUTER_API_KEY is not configured. Please set it in your .env file.');
      }

      const imageBuffer = fs.readFileSync(mediaFile.path);
      const base64Image = imageBuffer.toString('base64');

      const contentArray = [
        { type: 'text', text: 'Analyze this media and classify the incident.' },
        {
          type: 'image_url',
          image_url: {
            url: `data:${mimeType};base64,${base64Image}`,
            detail: 'auto'
          }
        }
      ];

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
        file: 'image',
      });
    }

    // ==========================================
    // 2. VIDEO ROUTE (Native Gemini Cloud Upload)
    // ==========================================
    if (mimeType.startsWith('video/')) {
      if (!process.env.GEMINI_API_KEY) {
        throw new Error('GEMINI_API_KEY is not configured. Please set it in your .env file.');
      }

      console.log(`Starting cloud video upload for: ${mediaFile.path}`);

      // Upload raw video directly using Gemini's File Manager API
      const uploadResult = await fileManager.uploadFile(mediaFile.path, {
        mimeType: mimeType,
        displayName: mediaFile.filename || 'uploaded-incident-video',
      });

      uploadResponseFile = uploadResult.file;
      console.log(`Video uploaded successfully to Gemini cloud: ${uploadResponseFile.uri}`);

      // Poll until the file transitions from PROCESSING to ACTIVE (needed for large files)
      let currentFile = await fileManager.getFile(uploadResponseFile.name);
      let pollAttempts = 0;
      const maxPollAttempts = 30; // 30 retries * 5s = 2.5 mins limit

      while (currentFile.state === 'PROCESSING' && pollAttempts < maxPollAttempts) {
        console.log(`Video is still processing in Google's cloud pipeline...`);
        await new Promise((resolve) => setTimeout(resolve, 5000));
        currentFile = await fileManager.getFile(uploadResponseFile.name);
        pollAttempts++;
      }

      if (currentFile.state !== 'ACTIVE') {
        throw new Error(`Google Video processing failed or timed out. State: ${currentFile.state}`);
      }

      console.log('Video analysis ready. Sending payload to Gemini models...');

      // Configure native model schema instructions
      const model = genAI.getGenerativeModel({
        model: NATIVE_GEMINI_MODEL,
        systemInstruction: systemPrompt,
        generationConfig: {
          responseMimeType: 'application/json',
        },
      });

      // Simple instruction is all that's needed because SKILLS.md handles the strict rules and schema!
      const result = await model.generateContent([
        {
          fileData: {
            mimeType: uploadResponseFile.mimeType,
            fileUri: uploadResponseFile.uri,
          },
        },
        {
          text: 'Analyze this video and classify the incident according to your system instructions.'
        }
      ]);

      const responseText = result.response.text();
      const parsed = JSON.parse(responseText);

      return enrichReportableFlag({
        ...parsed,
        file: 'video',
      });
    }

    throw new Error('Unsupported media type. Please upload an image or video.');

  } finally {
    // Dynamic Cloud Cleanup: Remove the temporary video from Gemini cloud hosting to conserve space
    if (uploadResponseFile) {
      try {
        console.log(`Deleting video from Gemini cloud hosting: ${uploadResponseFile.name}`);
        await fileManager.deleteFile(uploadResponseFile.name);
      } catch (cleanupError) {
        console.error('Failed to clear video from Google cloud storage:', cleanupError);
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
  enrichReportableFlag,
};