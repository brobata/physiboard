#!/usr/bin/env bash
#
# scripts/verify-release-apk.sh <apk>
#
# Implements docs/release.md's cert-fingerprint check idea (adapted from spec app-shell.md
# SS24, which hardcodes 2.x's own known SHA-256 and subject). 3.0 has no established production
# signing key yet, so there is nothing to hardcode here: the expected digest instead lives in
# a checked-in file, release/expected-cert-sha256.txt, which the maintainer fills in after the
# FIRST real signed release.
#
# Why this matters (docs/release.md's own reasoning): :core:shell's update checker offers
# installed copies a new APK by URL alone. If a release were ever signed with the wrong key,
# Android's own package installer would refuse the update on-device ("INSTALL_FAILED..."
# signature mismatch) for every existing install, silently, long after the mistake shipped.
# This script exists to catch that BEFORE a release goes out, by comparing the built APK's
# actual signing certificate against the one on record.
#
# Usage:
#   scripts/verify-release-apk.sh <path-to-apk>
#   scripts/verify-release-apk.sh -h | --help
#
# Requires `apksigner` from the Android SDK build-tools, located via $ANDROID_HOME or
# $ANDROID_SDK_ROOT (falling back to build-tools/*/apksigner under whichever is set, and then
# to apksigner on PATH).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXPECTED_FILE="$ROOT_DIR/release/expected-cert-sha256.txt"

usage() {
    cat <<'EOF'
Usage: scripts/verify-release-apk.sh <path-to-apk>

Prints the APK's signing certificate SHA-256 digest (via `apksigner verify --print-certs`)
and compares it against the value checked into release/expected-cert-sha256.txt.

  - If that file has no real digest recorded yet (the case until the first production
    release is cut), this script prints the APK's actual digest, warns loudly that nothing
    is on record, and exits non-zero so CI treats this as "not yet configured" rather than
    a silent pass.
  - If the file has a real 64-hex-character digest, this script exits 0 on a match and
    exits non-zero with a loud error on a mismatch.

Also prints the certificate subject (DN) apksigner reports, for visibility; 3.0 has no
confirmed subject on record, so nothing is asserted about it (see app/build.gradle.kts for
the signing config; no subject is declared there yet).

Options:
  -h, --help    Show this help.

Requires the Android SDK build-tools' apksigner, found via $ANDROID_HOME or
$ANDROID_SDK_ROOT.
EOF
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

if [ "$#" -eq 0 ]; then
    usage
    exit 1
fi

case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
esac

APK_PATH="$1"

if [ ! -f "$APK_PATH" ]; then
    fail "no such file: $APK_PATH"
fi

# ---------------------------------------------------------------------------
# Locate apksigner
# ---------------------------------------------------------------------------

find_apksigner() {
    local sdk_candidates=()
    [ -n "${ANDROID_HOME:-}" ] && sdk_candidates+=("$ANDROID_HOME")
    [ -n "${ANDROID_SDK_ROOT:-}" ] && sdk_candidates+=("$ANDROID_SDK_ROOT")

    local sdk newest
    for sdk in "${sdk_candidates[@]}"; do
        [ -d "$sdk/build-tools" ] || continue
        newest="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1)"
        if [ -n "$newest" ] && [ -x "$newest/apksigner" ]; then
            echo "$newest/apksigner"
            return 0
        fi
    done

    if command -v apksigner >/dev/null 2>&1; then
        command -v apksigner
        return 0
    fi

    return 1
}

APKSIGNER="$(find_apksigner)" || fail "apksigner not found. Set \$ANDROID_HOME or \$ANDROID_SDK_ROOT to an Android SDK install with build-tools/<version>/apksigner, or put apksigner on PATH."

echo "Using apksigner: $APKSIGNER"

# ---------------------------------------------------------------------------
# Run apksigner and pull out the digest and subject
# ---------------------------------------------------------------------------

VERIFY_OUTPUT="$("$APKSIGNER" verify --print-certs --verbose "$APK_PATH" 2>&1)" || {
    echo "$VERIFY_OUTPUT" >&2
    fail "apksigner verify failed for $APK_PATH (see output above); the APK may be unsigned or corrupt."
}

echo "$VERIFY_OUTPUT"

ACTUAL_DIGEST="$(printf '%s\n' "$VERIFY_OUTPUT" | grep -i 'certificate SHA-256 digest' | head -n1 | sed -E 's/.*digest:[[:space:]]*//' | tr 'A-Z' 'a-z' | tr -d '[:space:]')"
ACTUAL_SUBJECT="$(printf '%s\n' "$VERIFY_OUTPUT" | grep -i 'certificate DN' | head -n1 | sed -E 's/.*DN:[[:space:]]*//')"

[ -n "$ACTUAL_DIGEST" ] || fail "could not find a 'certificate SHA-256 digest' line in apksigner's output; apksigner's output format may have changed."

echo ""
echo "Signing certificate SHA-256 digest: $ACTUAL_DIGEST"
if [ -n "$ACTUAL_SUBJECT" ]; then
    echo "Signing certificate subject (DN):   $ACTUAL_SUBJECT"
else
    echo "Signing certificate subject (DN):   (not reported by this apksigner build)"
fi

# ---------------------------------------------------------------------------
# Compare against release/expected-cert-sha256.txt
# ---------------------------------------------------------------------------

if [ ! -f "$EXPECTED_FILE" ]; then
    mkdir -p "$(dirname "$EXPECTED_FILE")"
    cat > "$EXPECTED_FILE" <<'EOF'
# release/expected-cert-sha256.txt
#
# Expected SHA-256 digest of PhysiBoard's release signing certificate (docs/release.md
# "Signing"; app-shell.md SS24). scripts/verify-release-apk.sh compares a freshly-built
# release APK's actual signing certificate against this value, so that a release ever signed
# with the wrong key is caught here rather than by every installed copy silently refusing the
# update (docs/release.md's own reasoning about the update checker).
#
# NO PRODUCTION KEY HAS CUT A RELEASE YET. The maintainer must fill in the real 64-character
# hex digest below (lowercase or uppercase, no colons) after the FIRST real signed release,
# then commit this file. Until a real digest is present, verify-release-apk.sh treats this
# file as a placeholder: it prints the APK's actual digest, warns, and exits non-zero so CI
# reports "not yet configured" rather than a silent pass.
#
# Fill in below:
EOF
    echo "" >&2
    echo "NOTE: created placeholder $EXPECTED_FILE (it did not exist)." >&2
fi

read_expected_fingerprint() {
    # Under `set -o pipefail`, grep exits 1 when it selects zero lines (e.g. the file is only
    # comments, the placeholder case); `|| true` on the whole pipeline keeps that from tripping
    # `set -e` before the "not configured yet" warning below gets a chance to print.
    grep -v '^[[:space:]]*#' "$EXPECTED_FILE" | grep -v '^[[:space:]]*$' | tail -n1 | tr -d '[:space:]' | tr 'A-Z' 'a-z' || true
}

EXPECTED_DIGEST="$(read_expected_fingerprint)"

if [ -z "$EXPECTED_DIGEST" ]; then
    {
        echo ""
        echo "WARNING: no expected certificate fingerprint is on record yet"
        echo "($EXPECTED_FILE has no digest filled in). This is expected until the first real"
        echo "production release is signed and cut; after that, the maintainer must paste the"
        echo "digest printed above into that file and commit it. Until then this check cannot"
        echo "pass, by design, so a release is never silently accepted as 'verified'."
    } >&2
    exit 2
fi

if ! printf '%s' "$EXPECTED_DIGEST" | grep -qE '^[0-9a-f]{64}$'; then
    fail "$EXPECTED_FILE contains something that is not a 64-hex-character digest: '$EXPECTED_DIGEST'"
fi

if [ "$ACTUAL_DIGEST" = "$EXPECTED_DIGEST" ]; then
    echo ""
    echo "OK: signing certificate matches release/expected-cert-sha256.txt."
    exit 0
else
    {
        echo ""
        echo "ERROR: signing certificate MISMATCH."
        echo "  expected: $EXPECTED_DIGEST"
        echo "  actual:   $ACTUAL_DIGEST"
        echo "This APK was not signed with the key on record. Installed copies would refuse"
        echo "this as an update (docs/release.md). Do not publish it."
    } >&2
    exit 1
fi
