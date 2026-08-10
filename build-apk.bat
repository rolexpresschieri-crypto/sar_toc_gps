@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   TOC SAR (KMP) - APK Release
echo   Cartella: %cd%
echo ============================================
echo.

if not exist "%cd%\gradlew.bat" (
  echo ERRORE: manca gradlew.bat in questa cartella.
  pause
  exit /b 1
)

echo Build release...
call "%cd%\gradlew.bat" :composeApp:assembleRelease --no-daemon
if errorlevel 1 (
  echo BUILD FALLITO.
  pause
  exit /b 1
)

echo.
echo ============================================
echo   APK generato ^(release^):
echo   %cd%\composeApp\build\outputs\apk\release\composeApp-release.apk
echo ============================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0copy-apk-toc-name.ps1"
if errorlevel 1 (
  echo Copia con nome toc_sar_KMP_x.y.zz fallita.
  pause
  exit /b 1
)
echo.
echo Copia il file toc_sar_KMP_*.apk sul telefono e installa ^(origini sconosciute se richiesto^).
echo Per la prossima versione: alza versionName/versionCode in composeApp\build.gradle.kts
echo   es. 1.0.02 / 10002
echo.
pause
endlocal
