# Smart Steward ML — setup checklist

Follow these steps in order. **You** must download images from Google Drive (the agent cannot access your Drive).

## Step 1 — Download from Google Drive

1. Open **Shared with me → INCIDENTS → BARANGAY** (or your full INCIDENTS tree).
2. Select image folders/files → **Download**.
3. If you get a `.zip`, unzip it.

## Step 2 — Get images into the project

### If Windows says **“Path too long”** (Error 0x80010135)

Do **not** drag-and-drop from Downloads. Use the ingest script instead:

1. Unzip the Drive download to a **short** folder, e.g. `C:\ss\incidents`
2. From `SMART-STEWARD-API/express`:

```powershell
npm run ml:ingest -- --source "C:\ss\incidents"
```

This copies images into `ml/raw-download/incoming/` with short names (folder names like `BFP`, `garbage`, `mahjong` are used to pick the class).

### Otherwise

Copy images into `SMART-STEWARD-API/ml/raw-download/incoming/` (PDFs are skipped automatically).

## Step 3 — Auto-sort into train / val

From `SMART-STEWARD-API/express`:

```powershell
cd express
npm run ml:organize
```

Or manually:

```powershell
cd SMART-STEWARD-API\ml\scripts
python organize_dataset.py
```

This reads filenames, assigns classes (see `labels/filename-rules.json`), and copies into:

`datasets/incident-images/train/` and `val/` (80% / 20% split).

**Review** folders — rename or fix any mis-sorted images.

## Step 4 — Edit labels

- `labels/class-map.json` — agency + reportable per class (used by API).
- `labels/filename-rules.json` — how organize script maps file names → class folders.

Add any new class folders you created.

## Step 5 — Train the model (Python 3.10+)

```powershell
cd SMART-STEWARD-API\ml\training
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python train_mobilenet.py
```

Needs **at least ~20 images per class** in `train/` (more is better).

Outputs:

- `models/incident-classifier.pt`
- `models/class-index.json`

## Step 6 — Enable hybrid API

In `express/.env`:

```env
ML_ENABLED=true
ML_CONFIDENCE_THRESHOLD=0.75
ML_PYTHON=python
```

Restart API:

```powershell
cd express
npm run dev
```

Hybrid flow: **local model first** (if trained) → **OpenAI** if low confidence → **merge rules** (dataset wins agency/reportable on conflict).

## Step 7 — Test on phone

Rebuild the Android app, capture a photo, confirm review / submit still works.

---

## Quick commands

| Task | Command |
|------|---------|
| Organize Drive download | `npm run ml:organize` (from `express/`) |
| Train | `python train_mobilenet.py` (from `ml/training/`) |
| Test one image | `python predict.py path\to\image.jpg` |

## Troubleshooting

| Problem | Fix |
|---------|-----|
| **Path too long** when copying | Unzip to `C:\ss\incidents`, run `npm run ml:ingest -- --source "C:\ss\incidents"` |
| `No images found in incoming` | Run ingest or copy files into `ml/raw-download/incoming/` |
| Training fails "not enough images" | Add more photos per class folder |
| API still OpenAI-only | Set `ML_ENABLED=true` and ensure `models/incident-classifier.pt` exists |
| Python not found | Install Python 3.10+ and set `ML_PYTHON` to full path |
