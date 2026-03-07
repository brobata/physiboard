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
TMP_NOTES_PATH="$(mktemp)"
GRADLE_ARGS=(
  -PPASTIERA_VERSION_NAME="$BASE_VERSION"
  -PPASTIERA_NIGHTLY_VERSION_CODE="$VERSION_CODE"
  -PPASTIERA_NIGHTLY_VERSION_SUFFIX="-nightly.${TIMESTAMP}"
)

if [ "$PUBLISH" = "--fdroid" ] || [ "$BUILD_MODE" = "--fdroid" ]; then
  GRADLE_ARGS+=(-PPASTIERA_FDROID_BUILD=true)
fi

cleanup() {
  rm -f "$TMP_NOTES_PATH"
}

trap cleanup EXIT

build_nightly_notes() {
  local previous_tag
  previous_tag="$(git tag --list 'nightly/*' --sort=-creatordate | head -n 1)"

  cat "$NOTES_PATH" > "$TMP_NOTES_PATH"
  {
    printf '\n## Build Metadata\n\n'
    printf -- '- Version: `%s`\n' "$FULL_VERSION"
    printf -- '- Version code: `%s`\n' "$VERSION_CODE"
    printf -- '- Tag: `%s`\n' "$TAG_NAME"
  } >> "$TMP_NOTES_PATH"

  if [ -n "$previous_tag" ]; then
    printf '\n## Changes Since `%s`\n\n' "$previous_tag" >> "$TMP_NOTES_PATH"
    git log "${previous_tag}..HEAD" --format='- `%h` %s' >> "$TMP_NOTES_PATH"
  else
    printf '\n## Changes\n\n- No previous nightly tag found.\n' >> "$TMP_NOTES_PATH"
  fi
}

cd "$ROOT_DIR"

./gradlew :app:testNightlyReleaseUnitTest "${GRADLE_ARGS[@]}"

./gradlew :app:assembleNightlyRelease "${GRADLE_ARGS[@]}"

sha256sum "$APK_PATH" | tee "$SHA_PATH"

build_nightly_notes

if [ "$PUBLISH" = "--publish" ]; then
  gh release create "$TAG_NAME" "$APK_PATH" "$SHA_PATH" \
    --prerelease \
    --title "Pastiera Nightly v${FULL_VERSION}" \
    --notes-file "$TMP_NOTES_PATH"
fi

printf 'full_version=%s\n' "$FULL_VERSION"
printf 'tag_name=%s\n' "$TAG_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'apk=%s\n' "$APK_PATH"
printf 'sha256=%s\n' "$SHA_PATH"
printf 'notes=%s\n' "$TMP_NOTES_PATH"
