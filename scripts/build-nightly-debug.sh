#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: $0 <base-version> [--install] [--device <adb-serial>]

Examples:
  $0 0.86
  $0 0.86 --install
  $0 0.86 --install --device 192.168.178.190:46293
USAGE
}

BASE_VERSION="${1:-}"
INSTALL=false
DEVICE_SERIAL="${ADB_SERIAL:-}"

if [ -z "$BASE_VERSION" ] || [[ "$BASE_VERSION" == --* ]]; then
  echo "Missing required <base-version>" >&2
  usage
  exit 1
fi
shift

while [ $# -gt 0 ]; do
  case "$1" in
    --install)
      INSTALL=true
      shift
      ;;
    --device)
      if [ $# -lt 2 ]; then
        echo "Missing value for --device" >&2
        usage
        exit 1
      fi
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_INFO="$($ROOT_DIR/scripts/nightly-version.sh "$BASE_VERSION")"
TIMESTAMP="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^timestamp=/{print $2}')"
VERSION_CODE="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^version_code=/{print $2}')"
FULL_VERSION="$(printf '%s\n' "$VERSION_INFO" | awk -F= '/^full_version=/{print $2}')"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/nightly/debug/app-nightly-debug.apk"

resolve_adb() {
  local os
  os="$(uname -s 2>/dev/null || echo unknown)"

  if [ -n "${ADB_PATH:-}" ] && [ -x "${ADB_PATH}" ]; then
    printf '%s\n' "${ADB_PATH}"
    return 0
  fi

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [ -n "$sdk_root" ] && [ -x "$sdk_root/platform-tools/adb" ]; then
    printf '%s\n' "$sdk_root/platform-tools/adb"
    return 0
  fi

  case "$os" in
    Darwin)
      if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        printf '%s\n' "$HOME/Library/Android/sdk/platform-tools/adb"
        return 0
      fi
      ;;
    Linux)
      if [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
        printf '%s\n' "$HOME/Android/Sdk/platform-tools/adb"
        return 0
      fi
      ;;
  esac

  return 1
}

cd "$ROOT_DIR"

./gradlew :app:assembleNightlyDebug \
  -PPASTIERA_VERSION_NAME="$BASE_VERSION" \
  -PPASTIERA_NIGHTLY_VERSION_CODE="$VERSION_CODE" \
  -PPASTIERA_NIGHTLY_VERSION_SUFFIX="-nightly.${TIMESTAMP}"

if [ "$INSTALL" = true ]; then
  ADB_BIN="$(resolve_adb || true)"
  if [ -z "$ADB_BIN" ]; then
    echo "adb not found (set ADB_PATH or ANDROID_HOME/ANDROID_SDK_ROOT)." >&2
    exit 1
  fi

  if [ -n "$DEVICE_SERIAL" ]; then
    "$ADB_BIN" -s "$DEVICE_SERIAL" install -r "$APK_PATH"
  else
    device_count="$($ADB_BIN devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
    if [ "$device_count" -gt 1 ]; then
      echo "Multiple adb devices detected. Pass --device <serial> or set ADB_SERIAL." >&2
      "$ADB_BIN" devices -l
      exit 1
    fi
    "$ADB_BIN" install -r "$APK_PATH"
  fi
fi

printf 'full_version=%s\n' "$FULL_VERSION"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'apk=%s\n' "$APK_PATH"
