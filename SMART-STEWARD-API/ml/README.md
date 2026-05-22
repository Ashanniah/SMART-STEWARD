# Smart Steward — ML dataset & models

**Start here:** [SETUP.md](./SETUP.md) (step-by-step checklist).

Put your **labeled images** here (not in the Android or web apps).

## Where to copy your files

```
SMART-STEWARD-API/ml/datasets/incident-images/
  train/
    fire/
    flooding/
    garbage/
    illegal_gambling/
    not_reportable/
    ... one folder per class ...
  val/
    fire/
    flooding/
    ...
```

- **`train/`** — most images (~80%)
- **`val/`** — hold-out test images (~20%), same folder names as `train/`

Use **lowercase folder names with underscores** (e.g. `illegal_gambling`, not `Illegal Gambling`).

## Routing rules (small file — safe to commit)

Edit `labels/class-map.json`: maps each class folder name → API JSON fields (`category`, `assignedAgency`, `reportable`).

## Trained model exports

After fine-tuning (MobileNet, ResNet, etc.), save exports under:

```
SMART-STEWARD-API/ml/models/
  incident-classifier.onnx   # or .tflite for on-device
  labels.json                # copy of class index → name
```

Express `/ai` will call the model from here later (hybrid with OpenAI).

## Do not put dataset here

| Wrong place | Why |
|-------------|-----|
| `SMART-STEWARD-MOBILE/` | Bloates the app; images are not shipped to phones |
| `SMART-STEWARD-WEB/` | Agency dashboard does not train models |
| `express/src/` | Source code only; keep data separate |

## Git

Image folders are **gitignored** by default. Commit `labels/class-map.json` and training scripts; store large datasets on Google Drive, Kaggle, or Git LFS if the team needs sharing.
