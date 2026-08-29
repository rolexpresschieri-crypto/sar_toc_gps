#!/usr/bin/env bash
# Compila ComposeApp.framework e lo copia dove Xcode lo cerca.
set -euo pipefail

KMP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$KMP_ROOT"
chmod +x ./gradlew

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for jhome in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" \
    "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [[ -n "$jhome" && -x "$jhome/bin/java" ]]; then
      export JAVA_HOME="$jhome"
      break
    fi
  done
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo "ERRORE: Java non trovato per Gradle. Apri Android Studio una volta oppure installa JDK 17." >&2
  exit 1
fi

if [[ "YES" == "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" ]]; then
  echo "Skipping Gradle build (OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES)"
  exit 0
fi

CONFIGURATION="${CONFIGURATION:-Debug}"
SDK_NAME="${SDK_NAME:-iphonesimulator}"

case "$CONFIGURATION" in
  Release|release) BUILD_TYPE="release" ;;
  *) BUILD_TYPE="debug" ;;
esac

OUT_DIR="$KMP_ROOT/composeApp/build/xcode-frameworks/$CONFIGURATION/$SDK_NAME"
DEST="$OUT_DIR/ComposeApp.framework"
mkdir -p "$OUT_DIR"

if [[ "$SDK_NAME" == *simulator* ]]; then
  if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_TASK=":composeApp:linkReleaseFrameworkIosSimulatorArm64"
    SRC="$KMP_ROOT/composeApp/build/bin/iosSimulatorArm64/releaseFramework/ComposeApp.framework"
  else
    GRADLE_TASK=":composeApp:linkDebugFrameworkIosSimulatorArm64"
    SRC="$KMP_ROOT/composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework"
  fi
else
  if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_TASK=":composeApp:linkReleaseFrameworkIosArm64"
    SRC="$KMP_ROOT/composeApp/build/bin/iosArm64/releaseFramework/ComposeApp.framework"
  else
    GRADLE_TASK=":composeApp:linkDebugFrameworkIosArm64"
    SRC="$KMP_ROOT/composeApp/build/bin/iosArm64/debugFramework/ComposeApp.framework"
  fi
fi

echo "Kotlin framework:"
echo "  CONFIGURATION=$CONFIGURATION"
echo "  SDK_NAME=$SDK_NAME"
echo "  GRADLE_TASK=$GRADLE_TASK"
echo "  DEST=$DEST"

./gradlew "$GRADLE_TASK" --no-daemon

if [[ ! -d "$SRC" ]]; then
  echo "ERRORE: framework non trovato in $SRC" >&2
  exit 1
fi

rm -rf "$DEST"
cp -R "$SRC" "$DEST"
echo "OK: $DEST"

# Compose 1.8 su iOS legge:
#   <app>/compose-resources/composeResources/tocsar.composeapp.generated.resources/...
# Senza quel path lo splash crasha e l'icona "rimbalza".
if [[ -n "${TARGET_BUILD_DIR:-}" ]]; then
  RES_SRC="$KMP_ROOT/composeApp/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"
  if [[ ! -d "$RES_SRC" ]]; then
    echo "ERRORE: composeResources non trovate in $RES_SRC" >&2
    exit 1
  fi
  APP_RES="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH:-}"
  NESTED="$APP_RES/compose-resources/composeResources/tocsar.composeapp.generated.resources"
  mkdir -p "$NESTED"
  rsync -a "$RES_SRC/" "$NESTED/"
  echo "OK: compose-resources -> $NESTED"
  ls "$NESTED/drawable" "$NESTED/font" >/dev/null
else
  echo "AVVISO: TARGET_BUILD_DIR assente — risorse Compose non copiate nel .app"
fi

