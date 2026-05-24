#!/usr/bin/env python3
"""
Fine-tune MobileNetV3 on datasets/incident-images (train/val folders).

DEPRECATED as of May 2026 - Replaced by cloud-based AI (Gemini 2.0 Flash via OpenRouter).
This script is kept for reference only and will be removed in a future version.
"""
from __future__ import annotations

import json
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import datasets, models, transforms

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "datasets" / "incident-images"
MODELS_DIR = ROOT / "models"
EPOCHS = 8
BATCH_SIZE = 16
LR = 1e-4
MIN_IMAGES_WARN = 10


def remove_empty_class_dirs(root: Path) -> int:
    removed = 0
    if not root.exists():
        return removed
    for class_dir in root.iterdir():
        if class_dir.is_dir() and not any(class_dir.iterdir()):
            class_dir.rmdir()
            removed += 1
    return removed


def build_val_loader(train_ds, val_dir: Path, batch_size: int):
    """Val set may omit rare classes; remap labels to match train class indices."""
    remove_empty_class_dirs(val_dir)
    if not val_dir.exists() or not any(val_dir.iterdir()):
        return None

    val_ds = datasets.ImageFolder(
        str(val_dir),
        transform=transforms.Compose(
            [
                transforms.Resize((224, 224)),
                transforms.ToTensor(),
                transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
            ]
        ),
    )
    class_to_idx = train_ds.class_to_idx
    remapped = []
    for path, label in val_ds.samples:
        cls_name = val_ds.classes[label]
        if cls_name not in class_to_idx:
            continue
        remapped.append((path, class_to_idx[cls_name]))
    if not remapped:
        return None

    val_ds.samples = remapped
    val_ds.targets = [label for _, label in remapped]
    val_ds.classes = train_ds.classes
    val_ds.class_to_idx = class_to_idx
    return DataLoader(val_ds, batch_size=batch_size, shuffle=False)


def main():
    train_dir = DATA_DIR / "train"
    val_dir = DATA_DIR / "val"
    if not train_dir.exists():
        print(f"Missing {train_dir}. Run scripts/organize_dataset.py first.")
        return 1

    train_ds = datasets.ImageFolder(
        str(train_dir),
        transform=transforms.Compose(
            [
                transforms.Resize((224, 224)),
                transforms.RandomHorizontalFlip(),
                transforms.ToTensor(),
                transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
            ]
        ),
    )
    if len(train_ds) < MIN_IMAGES_WARN:
        print(f"Only {len(train_ds)} training images. Add more data per class, then retry.")
        return 1

    removed = remove_empty_class_dirs(val_dir)
    if removed:
        print(f"Removed {removed} empty val class folder(s).")

    val_loader = build_val_loader(train_ds, val_dir, BATCH_SIZE)
    if val_loader is None and val_dir.exists():
        print("No validation images (OK for small datasets). Training without val_acc.")

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True, num_workers=0)
    num_classes = len(train_ds.classes)
    weights = models.MobileNet_V3_Small_Weights.DEFAULT
    model = models.mobilenet_v3_small(weights=weights)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, num_classes)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = model.to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=LR)

    print(f"Training on {len(train_ds)} images, {num_classes} classes, device={device}")

    for epoch in range(EPOCHS):
        model.train()
        running_loss = 0.0
        correct = 0
        total = 0
        for images, labels in train_loader:
            images, labels = images.to(device), labels.to(device)
            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            running_loss += loss.item() * images.size(0)
            correct += (outputs.argmax(1) == labels).sum().item()
            total += labels.size(0)
        train_acc = correct / max(total, 1)
        msg = f"Epoch {epoch + 1}/{EPOCHS} loss={running_loss / max(total, 1):.4f} acc={train_acc:.3f}"

        if val_loader:
            model.eval()
            v_correct, v_total = 0, 0
            with torch.no_grad():
                for images, labels in val_loader:
                    images, labels = images.to(device), labels.to(device)
                    outputs = model(images)
                    v_correct += (outputs.argmax(1) == labels).sum().item()
                    v_total += labels.size(0)
            msg += f" val_acc={v_correct / max(v_total, 1):.3f}"
        print(msg)

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    ckpt_path = MODELS_DIR / "incident-classifier.pt"
    torch.save(
        {
            "model_state": model.state_dict(),
            "classes": train_ds.classes,
            "arch": "mobilenet_v3_small",
        },
        ckpt_path,
    )
    index_path = MODELS_DIR / "class-index.json"
    index_path.write_text(
        json.dumps({"classes": train_ds.classes}, indent=2),
        encoding="utf-8",
    )
    print(f"Saved {ckpt_path}")
    print(f"Saved {index_path}")
    print("Set ML_ENABLED=true in express/.env and restart the API.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
