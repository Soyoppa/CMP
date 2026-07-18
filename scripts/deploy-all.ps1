# Deploys tracker_1 and tracker_2 to their respective Firebase Hosting targets.
#
# Flow per schema: rewrite SHEET_SCHEMA in local.properties, build wasm, deploy.
# The original SHEET_SCHEMA value is restored at the end so local dev state
# isn't disturbed.
#
# Usage (from repo root):
#   .\scripts\deploy-all.ps1                  # deploys both
#   .\scripts\deploy-all.ps1 -Only tracker_1  # deploys just one

[CmdletBinding()]
param(
    [ValidateSet('tracker_1', 'tracker_2', 'both')]
    [string]$Only = 'both'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$LocalProps = Join-Path $RepoRoot 'local.properties'

if (-not (Test-Path $LocalProps)) {
    throw "local.properties not found at $LocalProps"
}

function Get-SchemaValue {
    $line = Select-String -Path $LocalProps -Pattern '^\s*SHEET_SCHEMA\s*=' | Select-Object -First 1
    if (-not $line) { throw "SHEET_SCHEMA not found in local.properties" }
    return ($line.Line -split '=', 2)[1].Trim()
}

function Set-SchemaValue([string]$Schema) {
    $content = Get-Content $LocalProps -Raw
    $updated = [regex]::Replace($content, '(?m)^\s*SHEET_SCHEMA\s*=.*$', "SHEET_SCHEMA=$Schema")
    Set-Content -Path $LocalProps -Value $updated -Encoding utf8 -NoNewline
}

function Deploy-Schema([string]$Schema, [string]$Target) {
    Write-Host ""
    Write-Host "=== Deploying $Schema -> hosting:$Target ===" -ForegroundColor Cyan

    Set-SchemaValue $Schema
    Write-Host "  local.properties: SHEET_SCHEMA=$Schema"

    Write-Host "  Building wasm..."
    & "$RepoRoot\gradlew.bat" ":composeApp:wasmJsBrowserDistribution"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed for $Schema" }

    Write-Host "  Deploying to Firebase target '$Target'..."
    & npx -y firebase-tools@latest deploy --only "hosting:$Target"
    if ($LASTEXITCODE -ne 0) { throw "Firebase deploy failed for $Target" }

    Write-Host "  Done: $Schema" -ForegroundColor Green
}

$original = Get-SchemaValue
Write-Host "Original SHEET_SCHEMA: $original (will be restored at end)"

try {
    if ($Only -eq 'both' -or $Only -eq 'tracker_1') {
        Deploy-Schema -Schema 'tracker_1' -Target 'tracker1'
    }
    if ($Only -eq 'both' -or $Only -eq 'tracker_2') {
        Deploy-Schema -Schema 'tracker_2' -Target 'tracker2'
    }
}
finally {
    Set-SchemaValue $original
    Write-Host ""
    Write-Host "Restored SHEET_SCHEMA=$original in local.properties" -ForegroundColor Yellow
}
