# Run from repo root or double-click after placing images in raw-download/incoming
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = if ($env:ML_PYTHON) { $env:ML_PYTHON } else { "python" }
& $py (Join-Path $scriptDir "organize_dataset.py")
exit $LASTEXITCODE
