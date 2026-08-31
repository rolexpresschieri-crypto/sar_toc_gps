#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFINES="$ROOT/supabase-config.local.json"
EXAMPLE="$ROOT/supabase-config.example.json"
OUT="$ROOT/iosApp/Configuration/Config.xcconfig"
JSON_OUT="$ROOT/iosApp/iosApp/supabase-config.json"
GRADLE="$ROOT/composeApp/build.gradle.kts"

if [[ -f "$DEFINES" ]]; then
  SRC="$DEFINES"
else
  echo "AVVISO: manca $DEFINES — uso $EXAMPLE (login backend non funzionerà)."
  SRC="$EXAMPLE"
fi

if [[ ! -f "$SRC" ]]; then
  echo "ERRORE: manca $SRC"
  exit 1
fi

if [[ ! -f "$GRADLE" ]]; then
  echo "ERRORE: manca $GRADLE (versione iOS allineata ad Android)"
  exit 1
fi

mkdir -p "$(dirname "$OUT")" "$(dirname "$JSON_OUT")"

python3 - "$SRC" "$OUT" "$JSON_OUT" "$GRADLE" <<'PY'
import json
import re
import sys

defines_path, out_path, json_out_path, gradle_path = sys.argv[1:5]
with open(defines_path, encoding="utf-8-sig") as f:
    data = json.load(f)

url = data.get("SUPABASE_URL", "").strip()
key = data.get("SUPABASE_ANON_KEY", "").strip()
toc_backend = data.get("TOC_BACKEND_URL", "").strip()

with open(gradle_path, encoding="utf-8") as f:
    gradle = f.read()
marketing_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
build_match = re.search(r'versionCode\s*=\s*(\d+)', gradle)
if not marketing_match or not build_match:
    raise SystemExit(f"versionName/versionCode non trovati in {gradle_path}")
marketing_version = marketing_match.group(1)
current_project_version = build_match.group(1)

def xc_quote(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'

content = f"""// Generato da sync-config.sh — non modificare a mano
SUPABASE_URL = {xc_quote(url)}
SUPABASE_ANON_KEY = {xc_quote(key)}
TOC_BACKEND_URL = {xc_quote(toc_backend)}
PRODUCT_BUNDLE_IDENTIFIER = it.ansmi.tocsar
MARKETING_VERSION = {marketing_version}
CURRENT_PROJECT_VERSION = {current_project_version}

#include? "Signing.xcconfig"
"""
with open(out_path, "w", encoding="utf-8") as f:
    f.write(content)

with open(json_out_path, "w", encoding="utf-8") as f:
    json.dump(
        {
            "SUPABASE_URL": url,
            "SUPABASE_ANON_KEY": key,
            "TOC_BACKEND_URL": toc_backend,
        },
        f,
        indent=2,
        ensure_ascii=False,
    )
    f.write("\n")

print(f"OK: {out_path}")
print(f"OK: {json_out_path}")
print(f"    SUPABASE_URL = {url}")
print(f"    MARKETING_VERSION = {marketing_version} ({current_project_version})")
PY
