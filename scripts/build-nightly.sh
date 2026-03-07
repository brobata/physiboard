#!/usr/bin/env bash
set -euo pipefail

BASE_VERSION="${1:-}"
PUBLISH="${2:-}"
BUILD_MODE="${3:-}"

if [ -z "$BASE_VERSION" ]; then
  echo "Usage: $0 <base-version> [--publish] [--fdroid]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_INFO="$("$ROOT_DIR/scripts/nightly-version.sh" "$BASE_VERSION")"
TIMESTAMP="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^timestamp=/{print $2}')"
FULL_VERSION="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^full_version=/{print $2}')"
TAG_NAME="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^tag_name=/{print $2}')"
VERSION_CODE="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^version_code=/{print $2}')"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/nightly/release/app-nightly-release.apk"
SHA_PATH="${APK_PATH}.sha256"
NOTES_PATH="$ROOT_DIR/.github/release-templates/debug-prerelease.md"
GRADLE_ARGS=(
  -PPASTIERA_VERSION_NAME="$BASE_VERSION"
  -PPASTIERA_NIGHTLY_VERSION_CODE="$VERSION_CODE"
  -PPASTIERA_NIGHTLY_VERSION_SUFFIX="-nightly.${TIMESTAMP}"
)

if [ "$PUBLISH" = "--fdroid" ] || [ "$BUILD_MODE" = "--fdroid" ]; then
  GRADLE_ARGS+=(-PPASTIERA_FDROID_BUILD=true)
fi

cd "$ROOT_DIR"

./gradlew :app:testNightlyReleaseUnitTest "${GRADLE_ARGS[@]}"

./gradlew :app:assembleNightlyRelease "${GRADLE_ARGS[@]}"

sha256sum "$APK_PATH" | tee "$SHA_PATH"

if [ "$PUBLISH" = "--publish" ]; then
  gh release create "$TAG_NAME" "$APK_PATH" "$SHA_PATH" \
    --prerelease \
    --title "Pastiera Nightly v${FULL_VERSION}" \
    --notes "$(cat "$NOTES_PATH")" \
    --generate-notes
fi

printf 'full_version=%s\n' "$FULL_VERSION"
printf 'tag_name=%s\n' "$TAG_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'apk=%s\n' "$APK_PATH"
printf 'sha256=%s\n' "$SHA_PATH"
