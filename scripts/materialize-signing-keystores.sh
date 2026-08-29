#!/usr/bin/env bash
# Writes the release keystore to disk from a base64 value when the file itself is absent — the
# path CI takes, where the keystore arrives as a secret rather than a file.
#
#   scripts/materialize-signing-keystores.sh [release/keystore.properties]
#
# Reads storeFile / storeFileB64 from the properties file, or PHYSIBOARD_KEYSTORE_B64 from the
# environment. Does nothing when the keystore already exists.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PROPS_FILE="${1:-$ROOT_DIR/release/keystore.properties}"
RELEASE_DIR="$ROOT_DIR/release"

read_prop() {
  [ -f "$KEYSTORE_PROPS_FILE" ] || return 0
  awk -v target="$1" '
    $0 ~ "^[[:space:]]*"target"=" {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      sub("^[^=]*=", "", line)
      print line
      exit
    }
  ' "$KEYSTORE_PROPS_FILE"
}

store_file="$(read_prop storeFile)"
store_file="${store_file:-physiboard-release.jks}"
b64_value="$(read_prop storeFileB64)"
b64_value="${b64_value:-${PHYSIBOARD_KEYSTORE_B64:-}}"

case "$store_file" in
  /*) resolved_path="$store_file" ;;
  *) resolved_path="$RELEASE_DIR/$store_file" ;;
esac

if [ -f "$resolved_path" ] || [ -z "$b64_value" ]; then
  exit 0
fi

mkdir -p "$(dirname "$resolved_path")"
printf '%s' "$b64_value" | base64 -d > "$resolved_path"
printf 'materialized=%s\n' "$resolved_path"
