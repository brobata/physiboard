#!/bin/bash
# Read the focused text field's contents off the phone as a STRING.
#
# Screenshots cannot be diffed; this can. It walks the accessibility tree the
# system already exposes, so it needs no cooperation from the app under test and
# works the same whichever keyboard is active. That is what makes it fair as a
# comparison between two keyboards.
#
#   usage: capture.sh <serial> [label]
set -u
SERIAL="${1:?usage: capture.sh <serial> [label]}"
LABEL="${2:-capture}"
ADB="$HOME/android-sdk/platform-tools/adb -s $SERIAL"
$ADB shell uiautomator dump /sdcard/physiboard-diff.xml >/dev/null 2>&1
$ADB shell cat /sdcard/physiboard-diff.xml 2>/dev/null | python3 -c '
import sys, re, html
raw = sys.stdin.read()
# Every editable node, in tree order, with its text exactly as the field holds it.
fields = []
for node in re.finditer(r"<node[^>]*>", raw):
    tag = node.group(0)
    if 'class="android.widget.EditText"' in tag or "editable" in tag:
        m = re.search(r'text="([^"]*)"', tag)
        f = re.search(r'focused="true"', tag)
        if m:
            fields.append((bool(f), html.unescape(m.group(1))))
focused = [t for f, t in fields if f]
chosen = focused[0] if focused else (fields[0][1] if fields else None)
if chosen is None:
    print("NO_EDITABLE_FIELD_FOUND", file=sys.stderr); sys.exit(2)
sys.stdout.write(chosen)
' > "${LABEL}.txt"
rc=$?
if [ $rc -eq 0 ]; then
  echo "captured $(wc -c < "${LABEL}.txt") bytes to ${LABEL}.txt"
  echo "--- contents ---"; cat -A "${LABEL}.txt" | head -20
else
  echo "capture failed: no editable field was focused on the phone" >&2
fi
exit $rc
