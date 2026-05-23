#!/usr/bin/env bash
set -euo pipefail

VERSION_NAME="${1:-}"
VERSION_CODE="${2:-}"
PUBLISH="${3:-}"

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "Usage: $0 <version-name> <version-code> [--publish]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PROPS_FILE="$ROOT_DIR/release/keystore.properties"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/stable/release/app-stable-release.apk"
SHA_PATH="${APK_PATH}.sha256"
TAG_NAME="v${VERSION_NAME}"

resolve_android_sdk_root() {
  local candidate

  for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate/build-tools" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

resolve_apksigner() {
  if [ -n "${APKSIGNER_PATH:-}" ] && [ -x "${APKSIGNER_PATH}" ]; then
    printf '%s\n' "${APKSIGNER_PATH}"
    return 0
  fi

  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi

  local sdk_root
  sdk_root="$(resolve_android_sdk_root || true)"
  if [ -n "$sdk_root" ]; then
    local apksigner
    apksigner="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)"
    if [ -n "$apksigner" ] && [ -x "$apksigner" ]; then
      printf '%s\n' "$apksigner"
      return 0
    fi
  fi

  return 1
}

read_prop() {
  local key="$1"
  local file="$2"
  awk -v target="$key" '
    $0 ~ "^[[:space:]]*"target"=" {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      sub("^[^=]*=", "", line)
      print line
      exit
    }
  ' "$file"
}

first_non_empty_prop() {
  local key
  local value=""
  for key in "$@"; do
    value="$(read_prop "$key" "$KEYSTORE_PROPS_FILE")"
    if [ -n "$value" ]; then
      printf '%s\n' "$value"
      return 0
    fi
  done
  return 1
}

configure_release_signing_env() {
  if [ ! -f "$KEYSTORE_PROPS_FILE" ]; then
    return
  fi

  if [ -z "${PASTIERA_KEYSTORE_PATH:-}" ]; then
    local store_file
    store_file="$(first_non_empty_prop "storeFile" "PASTIERA_KEYSTORE_FILE" || true)"
    if [ -n "$store_file" ]; then
      if [ "${store_file#/}" != "$store_file" ]; then
        export PASTIERA_KEYSTORE_PATH="$store_file"
      else
        export PASTIERA_KEYSTORE_PATH="$ROOT_DIR/release/$store_file"
      fi
    fi
  fi

  [ -n "${PASTIERA_KEYSTORE_PASSWORD:-}" ] || export PASTIERA_KEYSTORE_PASSWORD="$(first_non_empty_prop "storePassword" "PASTIERA_KEYSTORE_PASSWORD" "PASTIERA_KEY_PASSWORD" || true)"
  [ -n "${PASTIERA_KEY_ALIAS:-}" ] || export PASTIERA_KEY_ALIAS="$(first_non_empty_prop "keyAlias" "PASTIERA_KEY_ALIAS" || true)"
  [ -n "${PASTIERA_KEY_PASSWORD:-}" ] || export PASTIERA_KEY_PASSWORD="$(first_non_empty_prop "keyPassword" "PASTIERA_KEY_PASSWORD" || true)"
}

cd "$ROOT_DIR"
bash "$ROOT_DIR/scripts/materialize-signing-keystores.sh" "$KEYSTORE_PROPS_FILE" >/dev/null || true
configure_release_signing_env

./gradlew :app:testStableDebugUnitTest \
  -PPASTIERA_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_VERSION_NAME="$VERSION_NAME"

./gradlew :app:assembleStableRelease \
  -PPASTIERA_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_VERSION_NAME="$VERSION_NAME"

sha256sum "$APK_PATH" | tee "$SHA_PATH"

APKSIGNER="$(resolve_apksigner || true)"
if [ -n "$APKSIGNER" ]; then
  "$APKSIGNER" verify --print-certs "$APK_PATH"
else
  echo "Warning: apksigner not found; skipping signing certificate verification." >&2
  echo "Set APKSIGNER_PATH, ANDROID_SDK_ROOT, or ANDROID_HOME to enable it." >&2
fi

if [ "$PUBLISH" = "--publish" ]; then
  gh release create "$TAG_NAME" "$APK_PATH" "$SHA_PATH" \
    --title "Pastiera v${VERSION_NAME}" \
    --generate-notes
fi

printf 'version_name=%s\n' "$VERSION_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'tag_name=%s\n' "$TAG_NAME"
printf 'apk=%s\n' "$APK_PATH"
printf 'sha256=%s\n' "$SHA_PATH"
