#!/usr/bin/env bash
set -euo pipefail

VERSION_NAME="${1:-}"
VERSION_CODE="${2:-}"

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "Usage: $0 <version-name> <version-code>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/stable/release/app-stable-release.apk"
SHA_PATH="${APK_PATH}.sha256"

cd "$ROOT_DIR"

./gradlew :app:testStableDebugUnitTest \
  -PPASTIERA_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_VERSION_NAME="$VERSION_NAME" \
  -PPASTIERA_FDROID_BUILD=true

./gradlew :app:assembleStableRelease \
  -PPASTIERA_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_VERSION_NAME="$VERSION_NAME" \
  -PPASTIERA_FDROID_BUILD=true

sha256sum "$APK_PATH" | tee "$SHA_PATH"

printf 'version_name=%s\n' "$VERSION_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'apk=%s\n' "$APK_PATH"
printf 'sha256=%s\n' "$SHA_PATH"
