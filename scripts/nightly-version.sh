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

to_epoch_seconds() {
  local timestamp="$1"

  if date -u -d "1970-01-01 00:00:00" +%s >/dev/null 2>&1; then
    TZ=UTC date -d "${timestamp:0:8} ${timestamp:9:2}:${timestamp:11:2}:${timestamp:13:2}" +%s
  else
    TZ=UTC date -j -f '%Y%m%d.%H%M%S' "$timestamp" +%s
  fi
}

VERSION_CODE="$(to_epoch_seconds "$TS")"

printf 'timestamp=%s\n' "$TS"
printf 'full_version=%s\n' "$FULL_VERSION"
printf 'tag_name=%s\n' "$TAG_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
