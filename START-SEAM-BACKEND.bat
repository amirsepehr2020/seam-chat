@echo off
setlocal
cd /d "%~dp0backend"
if not exist node_modules\express (
  echo Installing backend dependencies...
  call npm install
  if errorlevel 1 (
    echo.
    echo npm install failed. Make sure Node.js is installed.
    pause
    exit /b 1
  )
)
if "%PASSWORD_PEPPER%"=="" set "PASSWORD_PEPPER=change-this-before-production"
if "%PORT%"=="" set "PORT=8787"
echo.
echo Starting SEAM CHAT backend...
echo Health check: http://localhost:%PORT%/health
echo Keep this window open while SEAM CHAT is online.
echo.
node server.js
pause
