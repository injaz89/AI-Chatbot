# ─────────────────────────────────────────────────────────────────────────────
# run-dev.ps1  –  Start the Spring Boot backend with secrets from .env
#
# Usage (from the /backend directory):
#   .\run-dev.ps1
#
# What it does:
#   1. Reads every KEY=VALUE line from .env
#   2. Sets each pair as a process-scoped environment variable
#   3. Runs: mvn spring-boot:run
# ─────────────────────────────────────────────────────────────────────────────

$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "ERROR: .env file not found at '$envFile'."
    Write-Error "Create it from .env.example and fill in your real values."
    exit 1
}

Write-Host "Loading environment variables from .env ..." -ForegroundColor Cyan

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    # Skip blank lines and comments
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $eqIndex = $line.IndexOf("=")
    if ($eqIndex -lt 1) { return }

    $key   = $line.Substring(0, $eqIndex).Trim()
    $value = $line.Substring($eqIndex + 1).Trim()

    # Remove surrounding quotes if present (handles KEY="value" or KEY='value')
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
    Write-Host "  SET $key=***" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Starting Spring Boot..." -ForegroundColor Green
Write-Host "Endpoint: http://localhost:8080" -ForegroundColor Green
Write-Host ""

mvn spring-boot:run
