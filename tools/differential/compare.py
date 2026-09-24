#!/usr/bin/env python3
"""Compare what two keyboards produced from the same typing.

Prints only what differs, character by character, because the interesting
failures are invisible ones: a space that is not there, a capital that did not
happen, a correction that did not fire.
"""
import sys, pathlib

def show(s: str) -> str:
    return s.replace(" ", "·").replace("\n", "⏎")

def main() -> int:
    if len(sys.argv) != 3:
        print("usage: compare.py <a.txt> <b.txt>", file=sys.stderr)
        return 2
    a_path, b_path = (pathlib.Path(p) for p in sys.argv[1:3])
    a, b = a_path.read_text(), b_path.read_text()
    if a == b:
        print(f"identical ({len(a)} chars). The two keyboards agree.")
        return 0
    print(f"DIFFER\n  {a_path.name}: {show(a)!r}\n  {b_path.name}: {show(b)!r}\n")
    for i, (ca, cb) in enumerate(zip(a, b)):
        if ca != cb:
            lo = max(0, i - 20)
            print(f"first difference at character {i}:")
            print(f"  {a_path.name}: ...{show(a[lo:i+20])}")
            print(f"  {b_path.name}: ...{show(b[lo:i+20])}")
            break
    else:
        longer, name = (a, a_path.name) if len(a) > len(b) else (b, b_path.name)
        print(f"one is a prefix of the other; {name} has {abs(len(a)-len(b))} extra: "
              f"{show(longer[min(len(a), len(b)):])!r}")
    return 1

if __name__ == "__main__":
    raise SystemExit(main())
