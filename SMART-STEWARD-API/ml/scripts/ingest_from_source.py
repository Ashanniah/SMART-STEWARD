#!/usr/bin/env python3
"""
Copy images from a Google Drive extract (any path depth / long filenames) into
ml/raw-download/incoming/ using short destination names.

Windows Explorer often fails with "Path too long" (0x80010135); this script uses
extended-length paths on read and writes short names under incoming/.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
from pathlib import Path, PurePath

ROOT = Path(__file__).resolve().parents[1]
INCOMING = ROOT / "raw-download" / "incoming"
FOLDER_RULES_PATH = ROOT / "labels" / "folder-rules.json"
FILENAME_RULES_PATH = ROOT / "labels" / "filename-rules.json"
CLASS_MAP_PATH = ROOT / "labels" / "class-map.json"
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}
SKIP_DIR_NAMES = {"blurred images", "blur", "thumbs", "__macosx"}


def win_long_path(path: Path) -> str:
    """Return a path string safe for long paths on Windows."""
    s = str(path.resolve())
    if sys.platform != "win32":
        return s
    if s.startswith("\\\\?\\"):
        return s
    if s.startswith("\\\\"):
        return "\\\\?\\UNC\\" + s[2:]
    return "\\\\?\\" + s


def load_pattern_rules(path: Path) -> list[tuple[re.Pattern[str], str]]:
    items = json.loads(path.read_text(encoding="utf-8"))
    return [(re.compile(item["pattern"], re.I), item["class"]) for item in items]


def load_valid_classes() -> set[str]:
    data = json.loads(CLASS_MAP_PATH.read_text(encoding="utf-8"))
    return set(data.keys())


def guess_class_from_text(text: str, rules, valid_classes: set[str]) -> str | None:
    for pattern, cls in rules:
        if pattern.search(text):
            if cls in valid_classes:
                return cls
    return None


def should_skip_dir(name: str) -> bool:
    return name.strip().lower() in SKIP_DIR_NAMES


def iter_source_images(source: Path):
    for dirpath, dirnames, filenames in os.walk(source):
        dirnames[:] = [d for d in dirnames if not should_skip_dir(d)]
        base = Path(dirpath)
        for name in filenames:
            ext = PurePath(name).suffix.lower()
            if ext not in IMAGE_EXT:
                continue
            yield base / name


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Ingest Drive images into ml/raw-download/incoming (fixes path-too-long)."
    )
    parser.add_argument(
        "--source",
        required=True,
        help=r'Folder where you unzipped Drive, e.g. C:\ss\incidents',
    )
    parser.add_argument(
        "--clear-incoming",
        action="store_true",
        help="Delete existing files under incoming/ before copy",
    )
    args = parser.parse_args()

    source = Path(args.source).expanduser()
    if not source.is_dir():
        print(f"Source folder not found: {source}")
        print("Tip: unzip to a SHORT path like C:\\ss\\incidents then pass --source that path.")
        return 1

    folder_rules = load_pattern_rules(FOLDER_RULES_PATH)
    filename_rules = load_pattern_rules(FILENAME_RULES_PATH)
    valid_classes = load_valid_classes()
    INCOMING.mkdir(parents=True, exist_ok=True)

    if args.clear_incoming:
        for child in INCOMING.iterdir():
            if child.is_dir():
                shutil.rmtree(child, ignore_errors=True)
            elif child.is_file():
                child.unlink(missing_ok=True)

    copied = 0
    skipped = 0
    by_class: dict[str, int] = {}
    needs_review_dir = INCOMING / "_needs_review"
    counter = 0

    try:
        paths = list(iter_source_images(source))
    except OSError as e:
        print(f"Could not read source: {e}")
        return 1

    if not paths:
        print(f"No images found under {source}")
        return 1

    for src in paths:
        counter += 1
        rel = src.relative_to(source)
        path_text = " / ".join(rel.parts)
        cls = guess_class_from_text(path_text, folder_rules, valid_classes)
        if not cls:
            cls = guess_class_from_text(src.name, filename_rules, valid_classes)

        if cls:
            dest_dir = INCOMING / cls
        else:
            dest_dir = needs_review_dir

        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / f"img_{counter:06d}{src.suffix.lower()}"

        try:
            shutil.copy2(win_long_path(src), dest)
        except OSError:
            # Fallback without extended path (non-Windows or short paths)
            shutil.copy2(src, dest)

        copied += 1
        key = cls or "_needs_review"
        by_class[key] = by_class.get(key, 0) + 1

    print("Ingest done.")
    print(f"  source: {source}")
    print(f"  destination: {INCOMING}")
    print(f"  copied: {copied}")
    for key in sorted(by_class.keys()):
        print(f"    {key}: {by_class[key]}")
    if by_class.get("_needs_review"):
        print("  Some files need manual class — check incoming/_needs_review/")
        print("  Move them into a class subfolder, or add rules in labels/folder-rules.json")
    print("Next: cd express && npm run ml:organize")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
