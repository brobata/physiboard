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
  APKSIGNER="$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -mindepth 2 -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1)"
fi

if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "apksigner not found. Set APKSIGNER or ANDROID_HOME." >&2
  exit 1
fi

EXPECTED_SUBJECT="CN=Pastiera, OU=Development, O=PalSoftware, L=Italy, ST=Italy, C=IT"
EXPECTED_SHA256="d5c018b9c33e0cda7cef0b006ed739d4c6304c4ef4a0c4d9454a5302e7c4c3e7"

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
