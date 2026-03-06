#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_FILE="${1:-$ROOT_DIR/release/nightly-secrets.env}"
RELEASE_DIR="$ROOT_DIR/release"
KEYSTORE_PATH="$RELEASE_DIR/pastiera-nightly.jks"
KEYSTORE_PROPS_PATH="$RELEASE_DIR/keystore.properties"

if [ ! -f "$SECRETS_FILE" ]; then
  echo "Secrets file not found: $SECRETS_FILE" >&2
  exit 1
fi

set -a
source "$SECRETS_FILE"
set +a

required_vars=(
  PASTIERA_NIGHTLY_KEYSTORE_B64
  PASTIERA_NIGHTLY_KEYSTORE_PASSWORD
  PASTIERA_NIGHTLY_KEY_ALIAS
  PASTIERA_NIGHTLY_KEY_PASSWORD
)

for var_name in "${required_vars[@]}"; do
  if [ -z "${!var_name:-}" ]; then
    echo "Missing value for $var_name in $SECRETS_FILE" >&2
    exit 1
  fi
done

mkdir -p "$RELEASE_DIR"

printf '%s' "$PASTIERA_NIGHTLY_KEYSTORE_B64" | base64 -d > "$KEYSTORE_PATH"

cat > "$KEYSTORE_PROPS_PATH" <<EOF
nightlyStoreFile=$(basename "$KEYSTORE_PATH")
nightlyStorePassword=$PASTIERA_NIGHTLY_KEYSTORE_PASSWORD
nightlyKeyAlias=$PASTIERA_NIGHTLY_KEY_ALIAS
nightlyKeyPassword=$PASTIERA_NIGHTLY_KEY_PASSWORD
EOF

printf 'keystore=%s\n' "$KEYSTORE_PATH"
printf 'keystore_properties=%s\n' "$KEYSTORE_PROPS_PATH"
