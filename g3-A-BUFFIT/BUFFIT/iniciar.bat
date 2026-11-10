@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title BUFF/T - Inicio

:: Uso:
::   iniciar.bat          -> construye y levanta todo (bases, servicios y portal)
::   iniciar.bat stop     -> detiene todo (conserva los datos)
::   iniciar.bat reset    -> detiene todo y BORRA usuarios, pedidos e inventario
::   iniciar.bat logs     -> muestra los logs en vivo (Ctrl+C para salir)
:: Requisitos: Docker Desktop instalado y abierto. Nada mas.

where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker no esta instalado. Instala Docker Desktop: https://www.docker.com/products/docker-desktop
    goto :error
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Desktop no esta corriendo. Abrelo, espera a que inicie y vuelve a ejecutar este archivo.
    goto :error
)

if /i "%~1"=="stop" (
    echo Deteniendo BUFF/T...
    docker compose down
    goto :fin
)

if /i "%~1"=="logs" (
    docker compose logs -f
    goto :fin
)

if /i "%~1"=="reset" (
    echo ATENCION: se borraran usuarios, pedidos e inventario.
    if not "%NOPAUSE%"=="1" (
        choice /m "Continuar"
        if errorlevel 2 goto :fin
    )
    docker compose down -v
)

echo =========================================
echo Construyendo y levantando BUFF/T...
echo (la primera vez tarda varios minutos)
echo =========================================
docker compose up --build -d
if errorlevel 1 (
    echo [ERROR] docker compose fallo. Revisa el mensaje de arriba.
    echo Si dice "port is already allocated", otro programa usa uno de estos puertos:
    echo 3000, 8000, 8002, 8003, 8004, 8005, 50051, 5432, 5433, 5434, 5672, 15672, 27018
    goto :error
)

echo Esperando a que los servicios respondan...
set /a intentos=0
:esperar
curl -s -o nul -f http://localhost:8000/health && curl -s -o nul http://localhost:3000 && goto :listo
set /a intentos+=1
if !intentos! geq 45 (
    echo [ERROR] Los servicios no respondieron a tiempo. Revisa con:  iniciar.bat logs
    docker compose ps
    goto :error
)
ping -n 3 127.0.0.1 >nul
goto :esperar

:listo
echo.
echo =========================================
echo BUFF/T esta listo:  http://localhost:3000
echo =========================================
docker compose ps --format "table {{.Name}}\t{{.Status}}"
if not "%NOPAUSE%"=="1" start "" http://localhost:3000
goto :fin

:error
if not "%NOPAUSE%"=="1" pause
exit /b 1

:fin
if not "%NOPAUSE%"=="1" pause
exit /b 0
