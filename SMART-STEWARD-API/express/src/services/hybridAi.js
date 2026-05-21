const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { generateOpenAiResponse, extractFrameFromVideo } = require('./openai');

const ML_ROOT = path.join(__dirname, '../../../ml');
const CLASS_MAP_PATH = path.join(ML_ROOT, 'labels/class-map.json');
const PREDICT_SCRIPT = path.join(ML_ROOT, 'training/predict.py');
const MODEL_CKPT = path.join(ML_ROOT, 'models/incident-classifier.pt');

function isMlEnabled() {
  return process.env.ML_ENABLED === 'true' && fs.existsSync(MODEL_CKPT);
}

function mlConfidenceThreshold() {
  const t = parseFloat(process.env.ML_CONFIDENCE_THRESHOLD || '0.75', 10);
  return Number.isFinite(t) ? t : 0.75;
}

function pythonBin() {
  return process.env.ML_PYTHON || 'python';
}

function runLocalPredict(imagePath) {
  return new Promise((resolve, reject) => {
    const py = pythonBin();
    const child = spawn(py, [PREDICT_SCRIPT, imagePath], {
      cwd: path.join(ML_ROOT, 'training'),
      windowsHide: true,
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => {
      stdout += d.toString();
    });
    child.stderr.on('data', (d) => {
      stderr += d.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => {
      try {
        const line = stdout.trim().split('\n').filter(Boolean).pop() || '{}';
        const parsed = JSON.parse(line);
        if (parsed.error) {
          reject(new Error(parsed.error));
          return;
        }
        if (code !== 0) {
          reject(new Error(stderr || `predict.py exited ${code}`));
          return;
        }
        resolve(parsed);
      } catch (e) {
        reject(new Error(stderr || e.message || 'Invalid predict.py output'));
      }
    });
  });
}

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

function localToResponse(local) {
  return enrichReportableFlag({
    type: local.type || 'incident',
    category: local.category,
    assignedAgency: local.assignedAgency,
    summary: local.summary,
    severity: local.severity || 'Medium',
    reportable: local.reportable,
  });
}

/**
 * Merge: dataset/local wins agency + category on conflict; safer reportable (OR).
 */
function mergeLocalAndOpenAi(local, openAi) {
  const llm = enrichReportableFlag({ ...openAi });
  const loc = localToResponse(local);
  const threshold = mlConfidenceThreshold();
  const conf = local.confidence ?? 0;

  const merged = { ...llm };
  if (conf >= threshold) {
    merged.type = loc.type;
    merged.category = loc.category;
    merged.assignedAgency = loc.assignedAgency;
    merged.severity = loc.severity;
    merged.reportable = loc.reportable;
    if (loc.summary) {
      merged.summary = `${loc.summary} ${llm.summary || ''}`.trim();
    }
  } else if (loc.reportable && !llm.reportable && conf >= 0.55) {
    merged.reportable = true;
    merged.category = loc.category;
    merged.assignedAgency = loc.assignedAgency;
  }

  merged._meta = {
    source: 'hybrid',
    localClass: local.classKey,
    localConfidence: conf,
  };
  return merged;
}

/**
 * Resolve path to a still image for the local model (JPEG/PNG).
 */
async function resolveImagePathForMl(mediaFile, extractFrameFromVideo) {
  if (!mediaFile) return null;
  const mime = mediaFile.mimetype || '';
  if (mime.startsWith('image/')) {
    return mediaFile.path;
  }
  if (mime.startsWith('video/')) {
    return extractFrameFromVideo(mediaFile.path);
  }
  return null;
}

async function generateHybridResponse(message, mediaFile) {
  const threshold = mlConfidenceThreshold();
  let imagePath = null;
  let tempFrame = null;

  try {
    if (isMlEnabled() && mediaFile) {
      imagePath = await resolveImagePathForMl(mediaFile, async (videoPath) => {
        tempFrame = await extractFrameFromVideo(videoPath);
        return tempFrame;
      });
    }

    let local = null;
    if (imagePath && fs.existsSync(imagePath)) {
      try {
        local = await runLocalPredict(imagePath);
      } catch (err) {
        console.warn('[hybridAi] Local model skipped:', err.message);
      }
    }

    const localConf = local?.confidence ?? 0;

    if (local && localConf >= threshold) {
      const response = localToResponse(local);
      response._meta = {
        source: 'local_model',
        localClass: local.classKey,
        localConfidence: localConf,
      };
      return {
        response,
        usage: null,
      };
    }

    const openAiResult = await generateOpenAiResponse(message, mediaFile);

    if (local && openAiResult?.response) {
      return {
        response: mergeLocalAndOpenAi(local, openAiResult.response),
        usage: openAiResult.usage,
      };
    }

    return openAiResult;
  } finally {
    if (tempFrame && fs.existsSync(tempFrame)) {
      try {
        fs.unlinkSync(tempFrame);
      } catch (_) {
        /* ignore */
      }
    }
  }
}

module.exports = {
  generateHybridResponse,
  isMlEnabled,
};
