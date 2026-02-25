#!/usr/bin/env python3
"""Generate compact local emoji search assets from Unicode CLDR annotations.

Output format (TSV):
emoji<TAB>tts_name<TAB>keyword1|keyword2|...
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path


CLDR_BASE = "https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations"


def repo_root_from_script() -> Path:
    return Path(__file__).resolve().parents[1]


def load_project_emojis(emoji_assets_dir: Path) -> set[str]:
    emojis: set[str] = set()
    for path in sorted(emoji_assets_dir.glob("*.txt")):
        if path.name == "minApi.txt":
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped:
                continue
            tokens = [token for token in stripped.split(" ") if token]
            emojis.update(tokens)
    return emojis


def fetch_cldr_annotations(locale: str) -> dict[str, dict]:
    url = f"{CLDR_BASE}/{locale}/annotations.json"
    with urllib.request.urlopen(url) as response:
        data = json.load(response)
    return data["annotations"]["annotations"]


def normalize_field(value: str) -> str:
    return " ".join(value.replace("\t", " ").replace("\n", " ").split())


def build_rows(locale: str, allowed_emojis: set[str]) -> list[tuple[str, str, list[str]]]:
    raw = fetch_cldr_annotations(locale)
    rows: list[tuple[str, str, list[str]]] = []
    for emoji, payload in raw.items():
        if emoji not in allowed_emojis:
            continue
        if not isinstance(payload, dict):
            continue
        tts_values = payload.get("tts") or []
        default_values = payload.get("default") or []
        if not isinstance(tts_values, list):
            tts_values = [str(tts_values)]
        if not isinstance(default_values, list):
            default_values = [str(default_values)]

        name = normalize_field(tts_values[0]) if tts_values else ""
        keywords: list[str] = []
        seen = set()
        for keyword in default_values:
            if not keyword:
                continue
            normalized = normalize_field(str(keyword))
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            keywords.append(normalized)

        if not name and not keywords:
            continue
        rows.append((emoji, name, keywords))

    rows.sort(key=lambda row: row[0])
    return rows


def write_tsv(out_path: Path, rows: list[tuple[str, str, list[str]]]) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8", newline="\n") as fh:
        for emoji, name, keywords in rows:
            keyword_blob = "|".join(keywords)
            fh.write(f"{emoji}\t{name}\t{keyword_blob}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--locales",
        nargs="+",
        default=["en", "de"],
        help="CLDR locales to generate (default: en de)",
    )
    args = parser.parse_args()

    root = repo_root_from_script()
    emoji_assets_dir = root / "app" / "src" / "main" / "assets" / "common" / "emoji"
    out_dir = root / "app" / "src" / "main" / "assets" / "common" / "emoji_search"

    allowed_emojis = load_project_emojis(emoji_assets_dir)
    if not allowed_emojis:
        print("No project emoji assets found", file=sys.stderr)
        return 1

    for locale in args.locales:
        rows = build_rows(locale, allowed_emojis)
        out_path = out_dir / f"{locale}.tsv"
        write_tsv(out_path, rows)
        print(f"Wrote {out_path} ({len(rows)} rows)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
