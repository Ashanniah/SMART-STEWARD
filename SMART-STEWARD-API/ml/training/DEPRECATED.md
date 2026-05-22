# DEPRECATED - Local ML Training Pipeline

> **Status:** Deprecated as of May 2026
> **Reason:** Replaced by cloud-based AI (Gemini 2.0 Flash via OpenRouter)

## What Was Here

- `train_mobilenet.py` - PyTorch training script for MobileNetV3-Small
- `predict.py` - Local inference script for incident classification
- `requirements.txt` - Python dependencies (PyTorch, torchvision)

## Why Deprecated

1. **Limited capability** - MobileNetV3 is an image classifier, not an object detector
2. **Single frame analysis** - Could only analyze one frame from videos
3. **Narrow categories** - Only 14 hardcoded incident classes
4. **No real-time processing** - Purely reactive, user-submitted media only

## Migration

All AI inference now uses **Gemini 2.0 Flash** via **OpenRouter**:

```
User uploads media → Express API → OpenRouter (Gemini) → Response
```

## Files

The Python training scripts remain in place for reference but are no longer used.
They may be removed in a future version once the Gemini implementation is fully validated.

## Environment Changes

| Old (Local ML) | New (Cloud AI) |
|----------------|----------------|
| `ML_ENABLED=true` | `ML_ENABLED=false` (or remove) |
| `OPENAI_API_KEY` | `OPENROUTER_API_KEY` |
| `ML_PYTHON=python` | Not needed |
| `ML_CONFIDENCE_THRESHOLD=0.75` | Not needed |

## Support

For questions about the new architecture, contact the development team.