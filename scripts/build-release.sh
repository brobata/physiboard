#!/usr/bin/env bash
#
# scripts/build-release.sh <version> [--publish]
#
# Runs the automatable part of "Cutting a release" from docs/release.md (itself adapted from
# spec app-shell.md SS24, "The release process as documented"). Concretely, this covers
# docs/release.md steps 2 ("Build") and 3 ("Checksum"), plus copying the signed APK to a
# distribution path and printing/saving the release note for the version, extracted from
# CHANGELOG.md with the same rule app/build.gradle.kts's generateWhatsNewAsset task and
# brobata.physiboard.core.shell.ChangeRecordSection.extractCard use.
#
# What this script deliberately does NOT do, per docs/release.md's "repository memory rule"
# ("no version bump, tag or publish without an explicit go-ahead from the maintainer in that
# message"):
#   - It never sets versionCode/versionName. That is docs/release.md step 1, done by hand in
#     app/build.gradle.kts before this script runs.
#   - It never creates a git tag, pushes, or creates a GitHub release on its own. Those are
#     docs/release.md steps 4 and 5. A `--publish` flag exists below as a documented aspiration
#     for a HUMAN to invoke by hand after they already have a go-ahead; it refuses to run
#     unless I_UNDERSTAND_THIS_PUBLISHES=1 is exported AND the interactive prompt is answered.
#     No code path in this script sets that variable or answers that prompt itself.
#
# Usage:
#   scripts/build-release.sh <version>              build, checksum, stage the release
#   scripts/build-release.sh <version> --publish     also tag + push + create a GitHub release
#                                                      (human-only; see "Publishing" below)
#   scripts/build-release.sh -h | --help             show this help
#
# Requires the four signing environment variables from docs/release.md "Signing" to be
# exported: PASTIERA_KEYSTORE_PATH, PASTIERA_KEYSTORE_PASSWORD, PASTIERA_KEY_ALIAS,
# PASTIERA_KEY_PASSWORD. Unlike `:app:assembleRelease` on its own (which stays lenient by
# design so CI and contributors without a keystore still get an unsigned build), this script's
# whole job is to produce an installable, update-compatible release artifact, so it refuses to
# run at all when any of the four is missing.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_GRADLE="$ROOT_DIR/app/build.gradle.kts"
CHANGELOG="$ROOT_DIR/CHANGELOG.md"
DIST_DIR="$ROOT_DIR/release/dist"

CURRENT_STEP="startup"

usage() {
    cat <<'EOF'
Usage: scripts/build-release.sh <version> [--publish]

Builds, tests, signs and stages a PhysiBoard release APK (docs/release.md "Cutting a
release", app-shell.md SS24), computes its SHA-256, and extracts that version's release
note from CHANGELOG.md.

Arguments:
  <version>     The versionName this release must match (e.g. 3.0.0). Checked against the
                versionName currently declared in app/build.gradle.kts; the script refuses
                to proceed on a mismatch.

Options:
  --publish     Also tag, push and create a GitHub release (docs/release.md steps 4-5).
                Human-only: requires I_UNDERSTAND_THIS_PUBLISHES=1 exported AND an
                interactive "yes" at a confirmation prompt. Never invoke this from an
                agent or script; it exists for a human who already has an explicit
                go-ahead.
  -h, --help    Show this help.

Required environment (docs/release.md "Signing"):
  PASTIERA_KEYSTORE_PATH
  PASTIERA_KEYSTORE_PASSWORD
  PASTIERA_KEY_ALIAS
  PASTIERA_KEY_PASSWORD

Output:
  app/build/outputs/apk/release/app-release.apk(.sha256)   the build's own signed output
  release/dist/physiboard-<version>.apk(.sha256)           the staged distribution copy
  release/dist/physiboard-<version>-notes.md               the extracted release note
EOF
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

on_error() {
    echo "" >&2
    echo "ERROR: build-release.sh failed during step: ${CURRENT_STEP}" >&2
}
trap on_error ERR

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

if [ "$#" -eq 0 ]; then
    usage
    exit 1
fi

VERSION=""
PUBLISH=0

for arg in "$@"; do
    case "$arg" in
        -h|--help)
            usage
            exit 0
            ;;
    esac
done

for arg in "$@"; do
    case "$arg" in
        --publish)
            PUBLISH=1
            ;;
        -*)
            fail "unrecognized option: $arg (see --help)"
            ;;
        *)
            if [ -n "$VERSION" ]; then
                fail "unexpected extra argument: $arg (see --help)"
            fi
            VERSION="$arg"
            ;;
    esac
done

if [ -z "$VERSION" ]; then
    usage
    exit 1
fi

# ---------------------------------------------------------------------------
# Read the version app/build.gradle.kts actually declares (docs/release.md step 1 is the
# maintainer's own job; 3.0 has no PHYSIBOARD_VERSION_NAME/PHYSIBOARD_VERSION_CODE Gradle
# properties wired up, so versionName/versionCode are read directly out of defaultConfig).
# ---------------------------------------------------------------------------

CURRENT_STEP="reading versionName/versionCode from app/build.gradle.kts"

[ -f "$BUILD_GRADLE" ] || fail "cannot find $BUILD_GRADLE"

DECLARED_VERSION_NAME="$(grep -m1 -E '^\s*versionName\s*=\s*"' "$BUILD_GRADLE" | sed -E 's/.*versionName\s*=\s*"([^"]*)".*/\1/')"
DECLARED_VERSION_CODE="$(grep -m1 -E '^\s*versionCode\s*=\s*[0-9]+' "$BUILD_GRADLE" | sed -E 's/.*versionCode\s*=\s*([0-9]+).*/\1/')"

[ -n "$DECLARED_VERSION_NAME" ] || fail "could not parse versionName out of $BUILD_GRADLE"
[ -n "$DECLARED_VERSION_CODE" ] || fail "could not parse versionCode out of $BUILD_GRADLE"

if [ "$VERSION" != "$DECLARED_VERSION_NAME" ]; then
    fail "requested version '$VERSION' does not match versionName '$DECLARED_VERSION_NAME' declared in app/build.gradle.kts. docs/release.md step 1 (setting versionCode/versionName) is the maintainer's own step, done before this script runs; update app/build.gradle.kts or pass the matching version."
fi

echo "Building PhysiBoard $DECLARED_VERSION_NAME (versionCode $DECLARED_VERSION_CODE)"

# ---------------------------------------------------------------------------
# Signing environment (docs/release.md "Signing"). Required for THIS script, unlike
# :app:assembleRelease on its own, which stays lenient so CI/contributors without a keystore
# still get an unsigned build.
# ---------------------------------------------------------------------------

CURRENT_STEP="checking signing environment variables"

MISSING_VARS=()
for var in PASTIERA_KEYSTORE_PATH PASTIERA_KEYSTORE_PASSWORD PASTIERA_KEY_ALIAS PASTIERA_KEY_PASSWORD; do
    if [ -z "${!var:-}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ "${#MISSING_VARS[@]}" -gt 0 ]; then
    {
        echo "ERROR: missing required signing environment variable(s): ${MISSING_VARS[*]}"
        echo "docs/release.md 'Signing' requires all four of PASTIERA_KEYSTORE_PATH,"
        echo "PASTIERA_KEYSTORE_PASSWORD, PASTIERA_KEY_ALIAS and PASTIERA_KEY_PASSWORD to be"
        echo "exported before running this script. Note that :app:assembleRelease itself stays"
        echo "lenient and produces an unsigned APK when these are absent (by design, for CI and"
        echo "contributors without the keystore); this script refuses to do that because a"
        echo "release script's whole job is to produce something installable and"
        echo "update-compatible."
    } >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Build, test, assemble (docs/release.md step 2)
# ---------------------------------------------------------------------------

CURRENT_STEP="./gradlew build test :app:assembleRelease"

echo "Running: ./gradlew build test :app:assembleRelease"
(cd "$ROOT_DIR" && ./gradlew build test :app:assembleRelease)

APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"

CURRENT_STEP="locating the signed release APK"

if [ ! -f "$APK_PATH" ]; then
    {
        echo "ERROR: expected signed APK not found at $APK_PATH"
        echo "This usually means the four PASTIERA_* signing variables were not visible to"
        echo "Gradle (a separate shell/daemon than the one that exported them, for example),"
        echo "and the build produced app-release-unsigned.apk instead (docs/release.md"
        echo "'Signing')."
    } >&2
    exit 1
fi

echo "Signed APK: $APK_PATH"

# ---------------------------------------------------------------------------
# Checksum (docs/release.md step 3)
# ---------------------------------------------------------------------------

CURRENT_STEP="computing SHA-256 of the signed APK"

(cd "$(dirname "$APK_PATH")" && sha256sum "$(basename "$APK_PATH")" > "$(basename "$APK_PATH").sha256")
echo "Checksum: $APK_PATH.sha256"
cat "$APK_PATH.sha256"

# ---------------------------------------------------------------------------
# Release note extraction. docs/release.md's "Building" section has no change-record file the
# way 2.x's PHYSIBOARD_CHANGES.md did; 3.0's equivalent is CHANGELOG.md, read with the same
# heading rule as app/build.gradle.kts's generateWhatsNewAsset task / extractWhatsNewCard, which
# is itself a Gradle-side mirror of
# brobata.physiboard.core.shell.ChangeRecordSection.extractCard (core/shell/src/main/kotlin/
# brobata/physiboard/core/shell/WhatsNewNotes.kt): the section heading is exactly "## <version> "
# (a trailing space, so "3.0.0" does not match "3.0.0-dev" or "3.0.10"). Unlike that rule, this
# extraction does NOT fall back to the first numeric heading when an exact match is missing
# (that fallback exists so the app's what's-new page never shows an "Unreleased" section to a
# user; a release script asking for a specific version must fail loudly instead, per this
# task's own instruction, rather than silently stapling the wrong version's notes onto a
# release). It also does not stop at "<!-- /card -->": release notes on GitHub are the FULL
# section up to (not including) the next "## " heading, not just the card-cut preamble the
# in-app asset uses.
# ---------------------------------------------------------------------------

CURRENT_STEP="extracting the release note for $VERSION from CHANGELOG.md"

[ -f "$CHANGELOG" ] || fail "cannot find $CHANGELOG"

extract_release_note() {
    local version="$1" changelog="$2"
    local heading="## ${version} "
    if ! grep -qF "$heading" "$changelog"; then
        return 1
    fi
    awk -v heading="$heading" '
        found {
            if (index($0, "## ") == 1) { exit }
            lines[++n] = $0
            next
        }
        index($0, heading) == 1 { found = 1 }
        END {
            first = 1
            last = n
            while (first <= last && lines[first] == "") first++
            while (last >= first && lines[last] == "") last--
            for (i = first; i <= last; i++) print lines[i]
        }
    ' "$changelog"
}

if ! RELEASE_NOTE="$(extract_release_note "$VERSION" "$CHANGELOG")"; then
    fail "CHANGELOG.md has no '## $VERSION ' section. Add one before cutting this release (docs/release.md; the app's own generateWhatsNewAsset task reads the same heading)."
fi

if [ -z "$RELEASE_NOTE" ]; then
    fail "CHANGELOG.md's '## $VERSION ' section is empty. A release must ship with a real note."
fi

# ---------------------------------------------------------------------------
# Stage the distribution copy
# ---------------------------------------------------------------------------

CURRENT_STEP="staging the distribution copy under release/dist"

mkdir -p "$DIST_DIR"
DIST_APK="$DIST_DIR/physiboard-${VERSION}.apk"
DIST_NOTES="$DIST_DIR/physiboard-${VERSION}-notes.md"

cp "$APK_PATH" "$DIST_APK"
(cd "$DIST_DIR" && sha256sum "$(basename "$DIST_APK")" > "$(basename "$DIST_APK").sha256")
printf '%s\n' "$RELEASE_NOTE" > "$DIST_NOTES"

echo ""
echo "Staged:"
echo "  $DIST_APK"
echo "  $DIST_APK.sha256"
echo "  $DIST_NOTES"
echo ""
echo "--- Release note for $VERSION ---"
printf '%s\n' "$RELEASE_NOTE"
echo "--- end release note ---"

# ---------------------------------------------------------------------------
# Publishing (docs/release.md steps 4-5): tag, push, create the GitHub release. HUMAN ONLY.
#
# The repository memory rule is explicit: no version bump, tag or publish without an explicit
# go-ahead from the maintainer in that message, and a script run by an agent must never take
# this step on its own. This block only runs when a human passed --publish by hand AND set
# I_UNDERSTAND_THIS_PUBLISHES=1 in their own shell AND then answers "yes" at the prompt below.
# Nothing above this comment ever sets that variable or answers that prompt.
# ---------------------------------------------------------------------------

if [ "$PUBLISH" -eq 1 ]; then
    CURRENT_STEP="publishing (tag, push, GitHub release)"

    if [ "${I_UNDERSTAND_THIS_PUBLISHES:-}" != "1" ]; then
        {
            echo "ERROR: --publish requires I_UNDERSTAND_THIS_PUBLISHES=1 to be exported by you,"
            echo "the human running this script, as explicit confirmation you already have a"
            echo "go-ahead to tag, push and publish this release. This script will not set that"
            echo "variable itself, and an agent must never export it on your behalf."
        } >&2
        exit 1
    fi

    TAG="v${VERSION}"
    echo ""
    echo "About to run docs/release.md steps 4-5 for $TAG:"
    echo "  - git tag -a $TAG -m \"PhysiBoard $VERSION\" and push it"
    echo "  - gh release create $TAG (title \"$VERSION\", attach $DIST_APK and its .sha256,"
    echo "    mark Latest, not draft, not pre-release)"
    read -r -p "Type 'yes' to proceed, anything else to abort: " CONFIRM
    if [ "$CONFIRM" != "yes" ]; then
        fail "publish aborted (confirmation not given)"
    fi

    command -v gh >/dev/null 2>&1 || fail "'gh' (GitHub CLI) not found; required to create the GitHub release"

    (cd "$ROOT_DIR" && git tag -a "$TAG" -m "PhysiBoard $VERSION")
    (cd "$ROOT_DIR" && git push origin "$TAG")
    (cd "$ROOT_DIR" && gh release create "$TAG" \
        --title "$VERSION" \
        --notes-file "$DIST_NOTES" \
        --latest \
        "$DIST_APK" "$DIST_APK.sha256")

    echo "Published $TAG."
fi

exit 0
