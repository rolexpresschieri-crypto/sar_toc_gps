@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   TOC SAR - Anagrafica
echo   Apri nel browser: http://localhost:3000
echo   Login: NVANSMI / ADMIN_RR / 123456
echo ============================================
echo.

if not exist ".env.local" (
  echo ATTENZIONE: manca .env.local
  echo   copy .env.example .env.local
  echo   e inserisci URL + anon key del Supabase TOC SAR
  echo   ^(stessi valori di supabase-config.local.json^)
  echo.
)

if not exist "node_modules" (
  echo Prima installazione: npm install
  echo.
  call npm install
  echo.
)

call npm run dev

endlocal
