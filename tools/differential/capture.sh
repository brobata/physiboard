#!/bin/bash
# Read the focused text field's contents off the phone as a STRING.
#
# Screenshots cannot be diffed; this can. It walks the accessibility tree the
# system already exposes, so it needs no cooperation from the app under test and
# behaves the same whichever keyboard is active, which is what makes it a fair
# comparison between two keyboards.
#
# It retries, because a dump is a snapshot of a tree that moves: a window
# animating, or the keyboard's own strip appearing, can produce a dump with no
# field in it a moment before one with the field right there.
#
#   usage: capture.sh <serial> <label>
set -u
SERIAL="${1:?usage: capture.sh <serial> <label>}"
LABEL="${2:?usage: capture.sh <serial> <label>}"
ADB="$HOME/android-sdk/platform-tools/adb -s $SERIAL"
HERE="$(cd "$(dirname "$0")" && pwd)"
REMOTE=/sdcard/physiboard-diff.xml

for attempt in 1 2 3 4; do
  $ADB shell uiautomator dump "$REMOTE" >/dev/null 2>&1
  if $ADB shell cat "$REMOTE" 2>/dev/null | python3 "$HERE/extract_field.py" > "${LABEL}.txt" 2>/dev/null; then
    echo "captured $(wc -c < "${LABEL}.txt") bytes to ${LABEL}.txt (attempt $attempt)"
    echo "--- as the field holds it (· is a space) ---"
    python3 -c "import sys;print(open(sys.argv[1]).read().replace(' ','·'))" "${LABEL}.txt"
    exit 0
  fi
  sleep 1
done
echo "capture failed after 4 attempts: no text field was on screen." >&2
echo "Make sure a field is focused and the cursor is in it, then try again." >&2
exit 2
