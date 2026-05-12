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
NIGHTLY_SECRETS_FILE="${NIGHTLY_SECRETS_FILE:-$ROOT_DIR/release/nightly-secrets.env}"
KEYSTORE_PROPS_FILE="$ROOT_DIR/release/keystore.properties"
VERSION_INFO="$("$ROOT_DIR/scripts/nightly-version.sh" "$BASE_VERSION")"
TIMESTAMP="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^timestamp=/{print $2}')"
FULL_VERSION="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^full_version=/{print $2}')"
TAG_NAME="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^tag_name=/{print $2}')"
VERSION_CODE="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^version_code=/{print $2}')"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/nightly/release/app-nightly-release.apk"
SHA_PATH="${APK_PATH}.sha256"
NOTES_PATH="$ROOT_DIR/.github/release-templates/debug-prerelease.md"
TMP_NOTES_PATH="$(mktemp)"
EXPECTED_NIGHTLY_CERT_SHA256="${EXPECTED_NIGHTLY_CERT_SHA256:-8c5dce860a65a7a3c3befcb7f7f35a1f3523c1d01462271d6ae03f4df402e685}"
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

configure_nightly_signing_env() {
  if [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PATH:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PASSWORD:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEY_ALIAS:-}" ] &&
    [ -n "${PASTIERA_NIGHTLY_KEY_PASSWORD:-}" ]; then
    return
  fi

  if [ -f "$NIGHTLY_SECRETS_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$NIGHTLY_SECRETS_FILE"
    set +a
  fi

  bash "$ROOT_DIR/scripts/materialize-signing-keystores.sh" "$KEYSTORE_PROPS_FILE" >/dev/null || true

  if [ -f "$KEYSTORE_PROPS_FILE" ]; then
    if [ -z "${PASTIERA_NIGHTLY_KEYSTORE_PATH:-}" ]; then
      local nightly_store_file
      nightly_store_file="$(first_non_empty_prop "nightlyStoreFile" "NIGHTLY_KEYSTORE_FILE" || true)"
      if [ -n "$nightly_store_file" ]; then
        if [ "${nightly_store_file#/}" != "$nightly_store_file" ]; then
          export PASTIERA_NIGHTLY_KEYSTORE_PATH="$nightly_store_file"
        else
          export PASTIERA_NIGHTLY_KEYSTORE_PATH="$ROOT_DIR/release/$nightly_store_file"
        fi
      fi
    fi
    [ -n "${PASTIERA_NIGHTLY_KEYSTORE_PASSWORD:-}" ] || export PASTIERA_NIGHTLY_KEYSTORE_PASSWORD="$(first_non_empty_prop "nightlyStorePassword" "PASTIERA_NIGHTLY_KEYSTORE_PASSWORD" || true)"
    [ -n "${PASTIERA_NIGHTLY_KEY_ALIAS:-}" ] || export PASTIERA_NIGHTLY_KEY_ALIAS="$(first_non_empty_prop "nightlyKeyAlias" "PASTIERA_NIGHTLY_KEY_ALIAS" || true)"
    [ -n "${PASTIERA_NIGHTLY_KEY_PASSWORD:-}" ] || export PASTIERA_NIGHTLY_KEY_PASSWORD="$(first_non_empty_prop "nightlyKeyPassword" "PASTIERA_NIGHTLY_KEY_PASSWORD" || true)"
  fi
}

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
configure_nightly_signing_env

./gradlew :app:testNightlyReleaseUnitTest "${GRADLE_ARGS[@]}"

./gradlew :app:assembleNightlyRelease "${GRADLE_ARGS[@]}"

sha256sum "$APK_PATH" | tee "$SHA_PATH"

build_nightly_notes

verify_nightly_signing_cert() {
  local apksigner_bin="${APKSIGNER_BIN:-apksigner}"
  if ! command -v "$apksigner_bin" >/dev/null 2>&1; then
    local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    local latest_build_tools
    latest_build_tools="$(ls -1d "$sdk_root"/build-tools/* 2>/dev/null | sort -V | tail -n 1 || true)"
    if [ -n "$latest_build_tools" ] && [ -x "$latest_build_tools/apksigner" ]; then
      apksigner_bin="$latest_build_tools/apksigner"
    else
      echo "apksigner not found. Install Android build-tools or set APKSIGNER_BIN." >&2
      exit 1
    fi
  fi

  local actual_sha
  actual_sha="$(
    "$apksigner_bin" verify --print-certs "$APK_PATH" |
      awk -F': ' '/certificate SHA-256 digest/ { print tolower($2); exit }'
  )"

  if [ -z "$actual_sha" ]; then
    echo "Could not read signing certificate digest from APK." >&2
    exit 1
  fi

  if [ "$actual_sha" != "$EXPECTED_NIGHTLY_CERT_SHA256" ]; then
    echo "Nightly signing certificate mismatch." >&2
    echo "Expected: $EXPECTED_NIGHTLY_CERT_SHA256" >&2
    echo "Actual:   $actual_sha" >&2
    exit 1
  fi
}

verify_nightly_signing_cert

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
