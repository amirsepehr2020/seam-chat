@echo off
setlocal EnableExtensions EnableDelayedExpansion
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

rem Load a persistent local password secret, creating it automatically on first run.
if exist .env (
  for /f "tokens=1,* delims==" %%A in ('findstr /b "PASSWORD_PEPPER=" .env') do set "PASSWORD_PEPPER=%%B"
)
if "!PASSWORD_PEPPER!"=="" (
  for /f "delims=" %%A in ('node -e "console.log(require('node:crypto').randomBytes(48).toString('hex'))"') do set "PASSWORD_PEPPER=%%A"
  >.env echo PASSWORD_PEPPER=!PASSWORD_PEPPER!
  >>.env echo PORT=8787
  >>.env echo HOST=0.0.0.0
  >>.env echo CORS_ORIGIN=*
  >>.env echo SESSION_DAYS=30
  echo Created backend\.env with a secure local password secret.
)

if "!PORT!"=="" set "PORT=8787"
if "!HOST!"=="" set "HOST=0.0.0.0"

if "!PASSWORD_PEPPER!"=="change-this-before-production" (
  echo.
  echo ERROR: The password secret is still the insecure placeholder.
  echo Delete backend\.env and run this file again.
  pause
  exit /b 1
)

echo.
echo ========================================
echo          SEAM CHAT BACKEND
echo ========================================
echo.
echo Backend: http://localhost:%PORT%
echo Health:  http://localhost:%PORT%/health
echo.
echo Keep this window open while SEAM CHAT is online.
echo Close this window to stop the backend.
echo.
node server.js

echo.
echo SEAM CHAT backend stopped.
pause
