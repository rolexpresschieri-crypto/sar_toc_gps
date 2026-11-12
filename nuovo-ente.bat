@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================
echo   TOC SAR - Nuovo Ente + ADMIN
echo ============================================
echo.
echo  Crea organizations + toc_admins ^(ADMIN_^).
echo  Credenziali: backend_toc\.env.local
echo.

if not exist "backend_toc\.env.local" (
  echo ERRORE: manca backend_toc\.env.local
  echo   copy backend_toc\.env.example backend_toc\.env.local
  echo   e inserisci NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY
  echo.
  pause
  exit /b 1
)

set "TOC_ORG_CODE="
set "TOC_ORG_NAME="
set "TOC_ADMIN_CODE="
set "TOC_ADMIN_NAME="
set "TOC_ADMIN_PWD="

set /p "TOC_ORG_CODE=Codice ente (es. CRITORINO): "
set /p "TOC_ORG_NAME=Nome ente (es. CRI Torino): "
set /p "TOC_ADMIN_CODE=Codice admin (es. ADMIN_RT): "
set /p "TOC_ADMIN_NAME=Nome admin (es. Roberto): "
set /p "TOC_ADMIN_PWD=Password TOC: "

if "%TOC_ORG_CODE%"=="" (
  echo ERRORE: codice ente obbligatorio.
  pause
  exit /b 1
)
if "%TOC_ORG_NAME%"=="" set "TOC_ORG_NAME=%TOC_ORG_CODE%"
if "%TOC_ADMIN_CODE%"=="" (
  echo ERRORE: codice admin obbligatorio.
  pause
  exit /b 1
)
if "%TOC_ADMIN_NAME%"=="" set "TOC_ADMIN_NAME=%TOC_ADMIN_CODE%"
if "%TOC_ADMIN_PWD%"=="" (
  echo ERRORE: password obbligatoria.
  pause
  exit /b 1
)

echo.
echo Inserimento in corso...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0nuovo-ente.ps1" -EnvFile "%~dp0backend_toc\.env.local"

set "RC=%ERRORLEVEL%"
echo.
if not "%RC%"=="0" (
  echo Operazione NON riuscita ^(codice %RC%^).
) else (
  echo Fatto. Login TOC: ente / ADMIN_... / password inserita.
)
echo.
pause
exit /b %RC%
