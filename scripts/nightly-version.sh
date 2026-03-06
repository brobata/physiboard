#!/usr/bin/env bash
set -euo pipefail

BASE_VERSION="${1:-}"
if [ -z "$BASE_VERSION" ]; then
  echo "Usage: $0 <base-version> [sequence]" >&2
  exit 1
fi

TZ_NAME="${PASTIERA_NIGHTLY_TZ:-Europe/Brussels}"
TS="${PASTIERA_NIGHTLY_TIMESTAMP:-$(TZ="$TZ_NAME" date +'%Y%m%d.%H%M%S')}"
FULL_VERSION="${BASE_VERSION}-nightly.${TS}"
TAG_NAME="nightly/v${FULL_VERSION}"

printf 'timestamp=%s\n' "$TS"
printf 'full_version=%s\n' "$FULL_VERSION"
printf 'tag_name=%s\n' "$TAG_NAME"
