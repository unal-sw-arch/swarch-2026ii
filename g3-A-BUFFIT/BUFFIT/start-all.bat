@echo off
TITLE BUFFIT - Startup Script (Optimizado)

echo =========================================
echo Iniciando Bases de Datos en Docker...
echo =========================================
docker compose up -d

timeout /t 2 >nul

echo =========================================
echo Lanzando Servicios y Portal...
echo =========================================

:: 1. Auth Service
start "Auth Service (8001)" cmd /k "cd /d "%~dp0apps\auth-service" && (if not exist venv (py -m venv venv && call venv\Scripts\activate && pip install -r requirements.txt) else call venv\Scripts\activate) && python app.py"

:: 2. Inventario Service
start "Inventario Service (8003)" cmd /k "cd /d "%~dp0apps\gestor-inventario" && (if not exist venv (py -m venv venv && call venv\Scripts\activate && pip install -r requirements.txt) else call venv\Scripts\activate) && uvicorn app.main:app --reload --port 8003"

:: 3. API Gateway
start "API Gateway (8000)" cmd /k "cd /d "%~dp0apps\api-gateway" && (if not exist venv (py -m venv venv && call venv\Scripts\activate && pip install -r requirements.txt) else call venv\Scripts\activate) && uvicorn app.main:app --reload --port 8000"

:: 4. Portal (Frontend)
start "Portal Frontend" cmd /k "cd /d "%~dp0apps\portal" && (if not exist node_modules npm install -D @vitejs/plugin-react && npm install axios react-router-dom) && npm run dev"

:: 5. Usuarios Service
start "Usuarios Service (8002)" cmd /k "cd /d "%~dp0apps\gestor-usuarios" && (if not exist venv (py -m venv venv && call venv\Scripts\activate && pip install -r requirements.txt) else call venv\Scripts\activate) && python app.py"

:: 6. Pedidos Service
start "Pedidos Service (8004)" cmd /k "cd /d "%~dp0apps\gestor-pedidos" && (if not exist venv (py -m venv venv && call venv\Scripts\activate && pip install -r requirements.txt) else call venv\Scripts\activate) && python app.py"

echo.
echo =========================================
echo ¡Todos los servicios iniciados al instante!
echo =========================================
pause