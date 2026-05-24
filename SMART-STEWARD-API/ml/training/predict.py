#!/usr/bin/env python3
"""
Predict incident class for one image. Prints JSON to stdout for Express hybrid layer.

DEPRECATED as of May 2026 - Replaced by cloud-based AI (Gemini 2.0 Flash via OpenRouter).
This script is kept for reference only and will be removed in a future version.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import torch
import torch.nn as nn
from PIL import Image
from torchvision import models, transforms

ROOT = Path(__file__).resolve().parents[1]
MODELS_DIR = ROOT / "models"
CKPT = MODELS_DIR / "incident-classifier.pt"
CLASS_MAP = ROOT / "labels" / "class-map.json"

TRANSFORM = transforms.Compose(
    [
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ]
)


def load_model(device):
    if not CKPT.exists():
        raise FileNotFoundError(f"Missing {CKPT}. Run train_mobilenet.py first.")
    ckpt = torch.load(CKPT, map_location=device)
    classes = ckpt["classes"]
    model = models.mobilenet_v3_small(weights=None)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(classes))
    model.load_state_dict(ckpt["model_state"])
    model.eval()
    return model, classes


def predict(image_path: Path, device):
    model, classes = load_model(device)
    img = Image.open(image_path).convert("RGB")
    tensor = TRANSFORM(img).unsqueeze(0).to(device)
    with torch.no_grad():
        logits = model(tensor)
        probs = torch.softmax(logits, dim=1)[0]
    conf, idx = torch.max(probs, 0)
    return {
        "classKey": classes[idx.item()],
        "confidence": float(conf.item()),
        "probabilities": {classes[i]: float(probs[i].item()) for i in range(len(classes))},
    }


def to_api_payload(class_key: str, confidence: float, class_map: dict):
    meta = class_map.get(class_key, class_map.get("not_reportable", {}))
    summary = (
        f"Local model classified this as {meta.get('category', class_key)} "
        f"({confidence * 100:.0f}% confidence)."
    )
    return {
        "source": "local_model",
        "classKey": class_key,
        "confidence": confidence,
        "type": meta.get("type", "incident"),
        "category": meta.get("category", class_key),
        "assignedAgency": meta.get("assignedAgency", "Barangay"),
        "summary": summary,
        "severity": meta.get("severity", "Medium"),
        "reportable": bool(meta.get("reportable", True)),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path, help="Path to image file")
    args = parser.parse_args()
    if not args.image.exists():
        print(json.dumps({"error": f"Image not found: {args.image}"}))
        return 1

    class_map = json.loads(CLASS_MAP.read_text(encoding="utf-8"))
    device = torch.device("cpu")
    try:
        raw = predict(args.image, device)
        out = to_api_payload(raw["classKey"], raw["confidence"], class_map)
        out["probabilities"] = raw["probabilities"]
        print(json.dumps(out))
        return 0
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        return 1


if __name__ == "__main__":
    sys.exit(main())
