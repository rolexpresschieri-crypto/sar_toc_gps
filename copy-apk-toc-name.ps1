# Copia composeApp\build\outputs\apk\release\composeApp-release.apk -> toc_sar_KMP_<version>.apk
# Legge versionName da composeApp\build.gradle.kts (es. 1.0.06).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

$gradle = Join-Path $root 'composeApp\build.gradle.kts'
$content = Get-Content -LiteralPath $gradle -Raw
$m = [regex]::Match($content, 'versionName\s*=\s*"(?<ver>\d+\.\d+\.\d+)"')
if (-not $m.Success) {
  Write-Host 'Avviso: versionName non trovato in composeApp\build.gradle.kts, salto toc_sar_KMP_x.y.zz.apk'
  exit 0
}

$v = $m.Groups['ver'].Value
$src = Join-Path $root 'composeApp\build\outputs\apk\release\composeApp-release.apk'
$dstDir = Split-Path $src -Parent
$dst = Join-Path $dstDir "toc_sar_KMP_$v.apk"

if (-not (Test-Path -LiteralPath $src)) {
  Write-Host "ERRORE: manca $src"
  exit 1
}

Copy-Item -LiteralPath $src -Destination $dst -Force
Write-Host "Copia con nome progetto: $dst"
