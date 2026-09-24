#!/usr/bin/env python3
"""Pull the focused text field's contents out of an accessibility tree dump.

Reads the dump on stdin, writes the field's text on stdout exactly as the field
holds it. Kept separate from capture.sh so neither has to quote the other.

Deliberately tolerant about what counts as the field. A dump is a snapshot of a
moving tree, and different apps expose an editable area differently: a native
field is an EditText, a web page inside a browser is not always. So the focused
node wins whatever its class, and an editable node is the fallback.
"""
import sys, re, html

FIELD_CLASSES = ("EditText", "AutoCompleteTextView", "SearchView", "TextView")

def candidates(raw: str):
    for node in re.finditer(r"<node[^>]*>", raw):
        tag = node.group(0)
        text = re.search(r'text="([^"]*)"', tag)
        if not text:
            continue
        cls = re.search(r'class="([^"]*)"', tag)
        yield {
            "focused": 'focused="true"' in tag,
            "editable": 'editable="true"' in tag,
            "fieldish": any(c in (cls.group(1) if cls else "") for c in FIELD_CLASSES),
            "cls": cls.group(1) if cls else "?",
            "text": html.unescape(text.group(1)),
        }

def main() -> int:
    found = list(candidates(sys.stdin.read()))
    # Strict on purpose. A looser fallback will happily return a battery
    # percentage from the status bar, which looks like a successful capture and
    # silently makes the comparison meaningless.
    for pick in (lambda n: n["focused"] and (n["editable"] or n["fieldish"]),
                 lambda n: n["editable"] and n["fieldish"]):
        hit = next((n for n in found if pick(n)), None)
        if hit is not None:
            sys.stdout.write(hit["text"])
            return 0
    print(f"no field in this dump ({len(found)} nodes carried text)", file=sys.stderr)
    return 2

if __name__ == "__main__":
    raise SystemExit(main())
