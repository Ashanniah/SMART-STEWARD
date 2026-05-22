#!/usr/bin/env python3
"""
Copy images from ml/raw-download/incoming into train/val class folders.
Uses labels/filename-rules.json to guess class from filename.
"""
from __future__ import annotations

import json
import random
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INCOMING = ROOT / "raw-download" / "incoming"
TRAIN_ROOT = ROOT / "datasets" / "incident-images" / "train"
VAL_ROOT = ROOT / "datasets" / "incident-images" / "val"
RULES_PATH = ROOT / "labels" / "filename-rules.json"
FOLDER_RULES_PATH = ROOT / "labels" / "folder-rules.json"
CLASS_MAP_PATH = ROOT / "labels" / "class-map.json"
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}
VAL_RATIO = 0.2
RANDOM_SEED = 42


def load_rules(path: Path = RULES_PATH):
    rules = json.loads(path.read_text(encoding="utf-8"))
    compiled = []
    for item in rules:
        compiled.append((re.compile(item["pattern"], re.I), item["class"]))
    return compiled


def load_valid_classes():
    data = json.loads(CLASS_MAP_PATH.read_text(encoding="utf-8"))
    return set(data.keys())


def guess_class(
    filename: str,
    rules,
    valid_classes: set[str],
    *,
    path_hint: str = "",
    parent_class: str | None = None,
) -> str | None:
    if parent_class and parent_class in valid_classes:
        return parent_class
    text = f"{path_hint} {Path(filename).stem}".strip()
    for pattern, cls in rules:
        if pattern.search(text):
            if cls in valid_classes:
                return cls
    return None


def iter_images(folder: Path):
    for path in folder.rglob("*"):
        if path.is_file() and path.suffix.lower() in IMAGE_EXT:
            yield path


def split_train_val(paths: list[Path]) -> tuple[list[Path], list[Path]]:
    """Ensure val is non-empty only when the class has 2+ images."""
    n = len(paths)
    if n <= 1:
        return paths, []
    if n == 2:
        return [paths[0]], [paths[1]]
    split_at = max(1, min(n - 1, int(n * (1 - VAL_RATIO))))
    return paths[:split_at], paths[split_at:]


def unique_dest(dest_dir: Path, name: str) -> Path:
    dest = dest_dir / name
    if not dest.exists():
        return dest
    stem, suffix = Path(name).stem, Path(name).suffix
    n = 1
    while True:
        candidate = dest_dir / f"{stem}_{n}{suffix}"
        if not candidate.exists():
            return candidate
        n += 1


def main():
    if not INCOMING.exists():
        INCOMING.mkdir(parents=True, exist_ok=True)
        print(f"Created {INCOMING}")
        print("Copy your Google Drive images here, then run this script again.")
        return 1

    rules = load_rules()
    folder_rules = load_rules(FOLDER_RULES_PATH)
    valid_classes = load_valid_classes()
    images = list(iter_images(INCOMING))
    if not images:
        print(f"No images found under {INCOMING}")
        print("Download from Google Drive and copy files into that folder.")
        return 1

    random.seed(RANDOM_SEED)
    by_class: dict[str, list[Path]] = {}
    skipped = []

    for path in images:
        try:
            rel = path.relative_to(INCOMING)
            path_hint = " / ".join(rel.parts[:-1])
            parent_class = rel.parts[0] if len(rel.parts) > 1 else None
        except ValueError:
            path_hint = ""
            parent_class = None
        cls = guess_class(
            path.name,
            rules,
            valid_classes,
            path_hint=path_hint,
            parent_class=parent_class,
        )
        if not cls:
            cls = guess_class(
                path.name,
                folder_rules,
                valid_classes,
                path_hint=path_hint,
            )
        if not cls:
            skipped.append(path.name)
            continue
        by_class.setdefault(cls, []).append(path)

    if not by_class:
        print("No files matched filename-rules.json. Add rules or rename files.")
        return 1

    counts = {"train": 0, "val": 0}
    for cls, paths in sorted(by_class.items()):
        random.shuffle(paths)
        train_files, val_files = split_train_val(paths)

        for subset, files in (("train", train_files), ("val", val_files)):
            if not files:
                continue
            dest_root = TRAIN_ROOT if subset == "train" else VAL_ROOT
            dest_dir = dest_root / cls
            dest_dir.mkdir(parents=True, exist_ok=True)
            for src in files:
                dest = unique_dest(dest_dir, src.name)
                shutil.copy2(src, dest)
                counts[subset] += 1

    print("Done.")
    print(f"  train: {counts['train']} images -> {TRAIN_ROOT}")
    print(f"  val:   {counts['val']} images -> {VAL_ROOT}")
    print(f"  classes: {', '.join(sorted(by_class.keys()))}")
    if skipped:
        print(f"  skipped (no rule match): {len(skipped)}")
        for name in skipped[:10]:
            print(f"    - {name}")
        if len(skipped) > 10:
            print(f"    ... and {len(skipped) - 10} more")
        print("  Tip: add patterns to labels/filename-rules.json or sort manually.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
