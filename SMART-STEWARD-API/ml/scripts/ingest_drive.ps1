# Ingest Google Drive images without Windows "Path too long" errors.
# Usage: .\ingest_drive.ps1 -Source "C:\ss\incidents"
param(
    [Parameter(Mandatory = $true)]
    [string]$Source
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = if ($env:ML_PYTHON) { $env:ML_PYTHON } else { "python" }
& $py (Join-Path $scriptDir "ingest_from_source.py") --source $Source
exit $LASTEXITCODE
