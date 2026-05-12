#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PROPS_FILE="${1:-$ROOT_DIR/release/keystore.properties}"
RELEASE_DIR="$ROOT_DIR/release"

if [ ! -f "$KEYSTORE_PROPS_FILE" ]; then
  echo "keystore.properties not found: $KEYSTORE_PROPS_FILE" >&2
  exit 0
fi

read_prop() {
  local key="$1"
  awk -v target="$key" '
    $0 ~ "^[[:space:]]*"target"=" {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      sub("^[^=]*=", "", line)
      print line
      exit
    }
  ' "$KEYSTORE_PROPS_FILE"
}

first_non_empty_prop() {
  local key
  local value=""
  for key in "$@"; do
    value="$(read_prop "$key")"
    if [ -n "$value" ]; then
      printf '%s\n' "$value"
      return 0
    fi
  done
  return 1
}

resolve_store_path() {
  local store_file="$1"
  if [ -z "$store_file" ]; then
    return
  fi
  if [ "${store_file#/}" != "$store_file" ]; then
    printf '%s\n' "$store_file"
  else
    printf '%s\n' "$RELEASE_DIR/$store_file"
  fi
}

materialize_b64_keystore() {
  local store_key="$1"
  local legacy_store_key="$2"
  local b64_key="$3"
  local legacy_b64_key="$4"
  local env_b64_key="$5"
  local fallback_file="$6"
  local store_file b64_value resolved_path

  store_file="$(first_non_empty_prop "$store_key" "$legacy_store_key" || true)"
  b64_value="$(first_non_empty_prop "$b64_key" "$legacy_b64_key" || true)"
  if [ -z "$b64_value" ] && [ -n "${!env_b64_key:-}" ]; then
    b64_value="${!env_b64_key}"
  fi

  if [ -z "$store_file" ]; then
    store_file="$fallback_file"
  fi

  if [ -z "$b64_value" ]; then
    return
  fi

  resolved_path="$(resolve_store_path "$store_file")"
  if [ -f "$resolved_path" ]; then
    return
  fi
  mkdir -p "$(dirname "$resolved_path")"
  printf '%s' "$b64_value" | base64 -d > "$resolved_path"
  printf 'materialized=%s\n' "$resolved_path"
}

materialize_b64_keystore \
  "storeFile" "PASTIERA_KEYSTORE_FILE" \
  "storeFileB64" "PASTIERA_KEYSTORE_B64" "PASTIERA_KEYSTORE_B64" \
  "pastiera-release.jks"
materialize_b64_keystore \
  "nightlyStoreFile" "NIGHTLY_KEYSTORE_FILE" \
  "nightlyStoreFileB64" "PASTIERA_NIGHTLY_KEYSTORE_B64" "PASTIERA_NIGHTLY_KEYSTORE_B64" \
  "pastiera-nightly.jks"
