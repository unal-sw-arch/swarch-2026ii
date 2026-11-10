# Espera a que los servicios publicados por docker-compose.p2.yml respondan.
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts/wait_for_services.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/wait_for_services.ps1 -TimeoutSeconds 300

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$WebUrl = "http://localhost:3000",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$targets = @(
    [pscustomobject]@{ Name = "api-gateway"; Url = "$GatewayUrl/health" },
    [pscustomobject]@{ Name = "web-ssr";     Url = "$WebUrl/health" }
)

function Test-Health {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300)
    }
    catch {
        return $false
    }
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
Write-Host "Esperando servicios (maximo $TimeoutSeconds s)..."

while ((Get-Date) -lt $deadline) {
    $pending = @()
    foreach ($target in $targets) {
        if (Test-Health -Url $target.Url) {
            Write-Host ("  [OK] {0} -> {1}" -f $target.Name, $target.Url) -ForegroundColor Green
        }
        else {
            $pending += $target
        }
    }

    if ($pending.Count -eq 0) {
        Write-Host "Todos los servicios publicados estan listos." -ForegroundColor Green
        exit 0
    }

    Start-Sleep -Seconds 3
}

$missing = ($targets | Where-Object { -not (Test-Health -Url $_.Url) } | ForEach-Object { $_.Name }) -join ", "
Write-Error "Tiempo agotado. Servicios sin responder: $missing"
exit 1
