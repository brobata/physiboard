#!/usr/bin/env bash
# Build, verify and optionally publish a PhysiBoard release.
#
#   scripts/build-release.sh <version-name> [--publish]
#
# The version name must already be set in app/build.gradle.kts (defaultVersionName) and have a
# section in PHYSIBOARD_CHANGES.md; that section becomes the release notes. Signing comes from
# release/keystore.properties or the PHYSIBOARD_KEYSTORE_* environment variables.
set -euo pipefail

VERSION_NAME="${1:-}"
PUBLISH="${2:-}"

if [ -z "$VERSION_NAME" ]; then
  echo "Usage: $0 <version-name> [--publish]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
DIST_DIR="$ROOT_DIR/release/dist"
DIST_APK="$DIST_DIR/physiboard-${VERSION_NAME}.apk"
TAG_NAME="v${VERSION_NAME}"
EXPECTED_CERT_SHA256="89a050fcb37aa14a16d77c737c70caaebdc4c7f10156f9dc8933adb3499c3261"

cd "$ROOT_DIR"

if ! grep -q "defaultVersionName = \"$VERSION_NAME\"" app/build.gradle.kts; then
  echo "app/build.gradle.kts does not declare defaultVersionName = \"$VERSION_NAME\"" >&2
  exit 1
fi
if ! grep -q "^## $VERSION_NAME " PHYSIBOARD_CHANGES.md; then
  echo "PHYSIBOARD_CHANGES.md has no section for $VERSION_NAME" >&2
  exit 1
fi

./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

APKSIGNER="${APKSIGNER:-$(find "${ANDROID_HOME:-$HOME/android-sdk}/build-tools" -mindepth 2 -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1)}"
if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "apksigner not found; set APKSIGNER or ANDROID_HOME." >&2
  exit 1
fi
ACTUAL_CERT_SHA256="$("$APKSIGNER" verify --print-certs "$APK_PATH" | awk -F': ' '/certificate SHA-256 digest/{print tolower($2); exit}')"
if [ "$ACTUAL_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]; then
  echo "Signing certificate mismatch: $ACTUAL_CERT_SHA256" >&2
  echo "Installed copies of PhysiBoard would refuse this APK as an update." >&2
  exit 1
fi

mkdir -p "$DIST_DIR"
cp "$APK_PATH" "$DIST_APK"
(cd "$DIST_DIR" && sha256sum "$(basename "$DIST_APK")" > "$(basename "$DIST_APK").sha256")

NOTES_FILE="$(mktemp)"
awk -v v="$VERSION_NAME" '
  $0 ~ "^## " v " " { on = 1; next }
  on && /^## / { exit }
  on { print }
' PHYSIBOARD_CHANGES.md > "$NOTES_FILE"

echo "apk=$DIST_APK"
echo "sha256=$DIST_APK.sha256"
echo "tag=$TAG_NAME"

if [ "$PUBLISH" = "--publish" ]; then
  git tag -a "$TAG_NAME" -m "PhysiBoard $VERSION_NAME"
  git push origin main "$TAG_NAME"
  gh release create "$TAG_NAME" "$DIST_APK" "$DIST_APK.sha256" \
    --title "PhysiBoard $VERSION_NAME" \
    --notes-file "$NOTES_FILE" \
    --latest
fi
rm -f "$NOTES_FILE"
