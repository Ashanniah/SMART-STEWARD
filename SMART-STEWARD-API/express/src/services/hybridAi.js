/**
 * Cloud AI Service - Gemini 2.0 Flash via OpenRouter
 * 
 * DEPRECATED: This service is being migrated to use OpenRouter API.
 * Use cloudAi.js for the new implementation.
 * 
 * @deprecated Use cloudAi.js instead
 */
const openai = require('./openai');

/**
 * Generate response using cloud AI (OpenRouter/Gemini)
 * This is a backward-compatible wrapper around openai.js
 * 
 * @deprecated Use cloudAi.generateCloudResponse instead
 */
async function generateHybridResponse(message, mediaFile) {
  console.warn('[DEPRECATED] hybridAi.generateHybridResponse is deprecated. Use cloudAi.generateCloudResponse instead.');
  return openai.generateOpenAiResponse(message, mediaFile);
}

module.exports = {
  generateHybridResponse,
  isMlEnabled: () => false, // Always returns false - local ML is deprecated
};
