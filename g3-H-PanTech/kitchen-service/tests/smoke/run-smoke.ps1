# Levanta el stack mínimo (postgres, redis, rabbitmq, kitchen-service y api-gateway), corre el
# smoke y las pruebas de seguridad y lo baja. Identity, Order y Reporting no existen aún, así
# que el Gateway se levanta con --no-deps.
#
#   .\kitchen-service\tests\smoke\run-smoke.ps1                  # todo
#   .\kitchen-service\tests\smoke\run-smoke.ps1 -Suite security  # solo seguridad
#   .\kitchen-service\tests\smoke\run-smoke.ps1 -KeepUp          # no bajar el stack al terminar
#
# Usa el proyecto de Compose "panaderia-smoke" (volúmenes propios; no toca los de desarrollo)
# pero los contenedores tienen nombre fijo: baje el stack de desarrollo antes de correrlo.
param(
    [ValidateSet("all", "smoke", "security")] [string] $Suite = "all",
    [switch] $KeepUp
)

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$override = Join-Path $PSScriptRoot "docker-compose.smoke.yml"
Set-Location $root

$env:NETWORK_NAME = "panaderia-smoke-net"
$compose = @("compose", "-p", "panaderia-smoke", "-f", "docker-compose.p2.yml", "-f", $override, "--env-file", ".env.p2.example")

$code = 1
try {
    docker @compose up -d --build postgres rabbitmq rabbitmq-init redis-smoke kitchen-service
    if ($LASTEXITCODE -ne 0) { throw "no se pudo levantar el stack" }
    docker @compose up -d --no-deps --build api-gateway
    if ($LASTEXITCODE -ne 0) { throw "no se pudo levantar el Gateway" }

    Push-Location (Join-Path $root "kitchen-service")
    try {
        go run ./tests/smoke -suite $Suite
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
