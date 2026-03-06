#!/usr/bin/env bash
set -euo pipefail

BASE_VERSION="${1:-}"
PAGES_PUBLIC_DIR="${2:-/Users/user/gits/GitHub/palsoftware-web/apps/docs/public}"
REPO_URL="${3:-https://pastiera.eu/fdroid/nightly/repo}"

if [ -z "$BASE_VERSION" ]; then
  echo "Usage: $0 <base-version> [pages-public-dir] [repo-url]" >&2
  exit 1
fi

if ! command -v fdroid >/dev/null 2>&1; then
  echo "fdroid is not installed. Install fdroidserver first, then rerun this script." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FDROID_ROOT="${FDROID_ROOT:-$ROOT_DIR/.fdroid/nightly}"
FDROID_REPO_DIR="$FDROID_ROOT/repo"
FDROID_METADATA_DIR="$FDROID_ROOT/metadata"
TARGET_REPO_DIR="$PAGES_PUBLIC_DIR/fdroid/nightly/repo"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/nightly/release/app-nightly-release.apk"
APP_ID="it.palsoftware.pastiera.nightly"

if [ ! -d "$PAGES_PUBLIC_DIR" ]; then
  echo "Pages public directory not found: $PAGES_PUBLIC_DIR" >&2
  exit 1
fi

ensure_yaml_value() {
  local file="$1"
  local key="$2"
  local value="$3"

  if grep -Eq "^${key}:" "$file"; then
    perl -0pi -e "s#^${key}:.*#${key}: \"${value}\"#m" "$file"
  else
    printf '%s: "%s"\n' "$key" "$value" >> "$file"
  fi
}

mkdir -p "$FDROID_ROOT"

if [ ! -f "$FDROID_ROOT/config.yml" ]; then
  (
    cd "$FDROID_ROOT"
    fdroid init
  )
fi

ensure_yaml_value "$FDROID_ROOT/config.yml" "repo_url" "$REPO_URL"
ensure_yaml_value "$FDROID_ROOT/config.yml" "repo_name" "Pastiera Nightly"
ensure_yaml_value "$FDROID_ROOT/config.yml" "repo_description" "Nightly builds for Pastiera"

mkdir -p "$FDROID_METADATA_DIR"
find "$FDROID_METADATA_DIR" -maxdepth 1 -type f ! -name "${APP_ID}.yml" -delete

cat > "$FDROID_METADATA_DIR/${APP_ID}.yml" <<EOF
AuthorName: PalSoftware
Categories:
  - nightly
IssueTracker: https://github.com/palsoftware/pastiera/issues
License: GPL-3.0-only
Name: Pastiera Nightly
SourceCode: https://github.com/palsoftware/pastiera
Summary: Nightly builds for Pastiera
WebSite: https://pastiera.eu
EOF

"$ROOT_DIR/scripts/build-nightly.sh" "$BASE_VERSION"

mkdir -p "$FDROID_REPO_DIR"
rm -f "$FDROID_REPO_DIR"/*.apk "$FDROID_REPO_DIR"/*.apk.sha256
cp "$APK_PATH" "$FDROID_REPO_DIR/"

(
  cd "$FDROID_ROOT"
  fdroid update
)

mkdir -p "$TARGET_REPO_DIR"
rsync -a --delete "$FDROID_REPO_DIR/" "$TARGET_REPO_DIR/"

printf 'repo_url=%s\n' "$REPO_URL"
printf 'fdroid_root=%s\n' "$FDROID_ROOT"
printf 'pages_repo_dir=%s\n' "$TARGET_REPO_DIR"
