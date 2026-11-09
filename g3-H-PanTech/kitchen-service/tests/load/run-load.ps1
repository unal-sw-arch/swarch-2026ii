# Levanta Kitchen con sus dependencias, encola 50 pedidos y mide GET /queue con 10 clientes en
# paralelo (500 consultas). Reutiliza el override de las pruebas de humo (Redis con contraseña y
# Kitchen publicado en 127.0.0.1:8001).
#
#   .\kitchen-service\tests\load\run-load.ps1
#   .\kitchen-service\tests\load\run-load.ps1 -Orders 50 -Requests 1000 -Concurrency 20
param(
    [int] $Orders = 50,
    [int] $Requests = 500,
    [int] $Concurrency = 10,
    [switch] $KeepUp
)

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$override = Join-Path $PSScriptRoot "..\smoke\docker-compose.smoke.yml"
Set-Location $root

$env:NETWORK_NAME = "panaderia-smoke-net"
$compose = @("compose", "-p", "panaderia-smoke", "-f", "docker-compose.p2.yml", "-f", $override, "--env-file", ".env.p2.example")

$code = 1
try {
    docker @compose up -d --build postgres rabbitmq rabbitmq-init redis-smoke kitchen-service
    if ($LASTEXITCODE -ne 0) { throw "no se pudo levantar el stack" }

    Write-Host "Esperando a Kitchen..."
    for ($i = 0; $i -lt 60; $i++) {
        try { if ((Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8001/ready).StatusCode -eq 200) { break } } catch { Start-Sleep -Seconds 2 }
    }

    Push-Location (Join-Path $root "kitchen-service")
    try {
        go run ./tests/load -orders $Orders -requests $Requests -concurrency $Concurrency
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    if (-not $KeepUp) {
        docker @compose down -v | Out-Null
    }
}
exit $code
