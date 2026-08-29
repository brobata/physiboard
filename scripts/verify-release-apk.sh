#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-}"

if [ -z "$APK_PATH" ]; then
  echo "Usage: $0 <path-to-release-apk>" >&2
  exit 1
fi

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ]; then
  APKSIGNER="$(find "${ANDROID_HOME:-$HOME/android-sdk}/build-tools" -mindepth 2 -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1)"
fi

if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "apksigner not found. Set APKSIGNER or ANDROID_HOME." >&2
  exit 1
fi

EXPECTED_SUBJECT="CN=PhysiBoard, O=Brobata, C=US"
EXPECTED_SHA256="89a050fcb37aa14a16d77c737c70caaebdc4c7f10156f9dc8933adb3499c3261"

VERIFY_OUTPUT="$("$APKSIGNER" verify --print-certs "$APK_PATH")"
ACTUAL_SUBJECT="$(printf '%s\n' "$VERIFY_OUTPUT" | awk -F': ' '/certificate DN/{print $2; exit}')"
ACTUAL_SHA256="$(printf '%s\n' "$VERIFY_OUTPUT" | awk -F': ' '/certificate SHA-256 digest/{print tolower($2); exit}')"
APK_SHA256="$(sha256sum "$APK_PATH" | awk '{print $1}')"

printf 'APK: %s\n' "$APK_PATH"
printf 'APK SHA-256: %s\n' "$APK_SHA256"
printf 'Expected subject: %s\n' "$EXPECTED_SUBJECT"
printf 'Actual subject:   %s\n' "$ACTUAL_SUBJECT"
printf 'Expected cert SHA-256: %s\n' "$EXPECTED_SHA256"
printf 'Actual cert SHA-256:   %s\n' "$ACTUAL_SHA256"

if [ "$ACTUAL_SUBJECT" = "$EXPECTED_SUBJECT" ] && [ "$ACTUAL_SHA256" = "$EXPECTED_SHA256" ]; then
  echo "OK: APK matches the official Pastiera release signing certificate."
else
  echo "NOT OK: APK does not match the official Pastiera release signing certificate." >&2
  exit 1
fi
