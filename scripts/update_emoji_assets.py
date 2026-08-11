#!/usr/bin/env python3
"""Update Pastiera's generated emoji assets from pinned Unicode sources.

The generator intentionally pins both Unicode Emoji and CLDR. ``--check`` runs
the complete generation in a temporary tree and fails when committed assets are
stale, without modifying the checkout.
"""

from __future__ import annotations

import argparse
import filecmp
import json
import re
import shutil
import sys
import tempfile
import unicodedata
import urllib.request
from collections import OrderedDict
from pathlib import Path


UNICODE_EMOJI_VERSION = "17.0"
CLDR_JSON_VERSION = "48.2.1"
EMOJI_TEST_URL = (
    f"https://www.unicode.org/Public/{UNICODE_EMOJI_VERSION}.0/emoji/emoji-test.txt"
)
CLDR_ANNOTATIONS_BASE_URL = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/"
    f"{CLDR_JSON_VERSION}/cldr-json/cldr-annotations-full/annotations"
)
SEARCH_LOCALES = ("en", "de", "es", "fr", "hy", "it", "pl", "ru", "uk")

GROUP_TO_FILE = {
    "Smileys & Emotion": "SMILEYS_AND_EMOTION.txt",
    "People & Body": "PEOPLE_AND_BODY.txt",
    "Animals & Nature": "ANIMALS_AND_NATURE.txt",
    "Food & Drink": "FOOD_AND_DRINK.txt",
    "Travel & Places": "TRAVEL_AND_PLACES.txt",
    "Activities": "ACTIVITIES.txt",
    "Objects": "OBJECTS.txt",
    "Symbols": "SYMBOLS.txt",
    "Flags": "FLAGS.txt",
}

VERSION_TO_MIN_API = {
    "E4.0": 24,
    "E5.0": 26,
    "E11.0": 28,
    "E12.0": 29,
    "E12.1": 29,
    "E13.0": 30,
    "E13.1": 31,
    "E14.0": 32,
    "E15.0": 33,
    "E15.1": 34,
    "E16.0": 35,
}

SKIN_TONES = {chr(codepoint) for codepoint in range(0x1F3FB, 0x1F400)}


def repo_root_from_script() -> Path:
    return Path(__file__).resolve().parents[1]


def fetch_text(url: str) -> str:
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read().decode("utf-8")


def fetch_annotations(locale: str) -> dict[str, dict]:
    url = f"{CLDR_ANNOTATIONS_BASE_URL}/{locale}/annotations.json"
    payload = json.loads(fetch_text(url))
    return payload["annotations"]["annotations"]


def codepoints_to_emoji(codepoints: str) -> str:
    return "".join(chr(int(codepoint, 16)) for codepoint in codepoints.split())


def strip_skin_tones(emoji: str) -> str:
    return "".join(character for character in emoji if character not in SKIN_TONES)


def normalize_field(value: str) -> str:
    return " ".join(value.replace("\t", " ").replace("\n", " ").split())


def normalize_shortcode(value: str) -> str:
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "_", ascii_value.lower()).strip("_")


def parse_emoji_test(text: str) -> tuple[str, list[dict[str, str]]]:
    version = "unknown"
    group = ""
    subgroup = ""
    records: list[dict[str, str]] = []

    for line in text.splitlines():
        if line.startswith("# Version:"):
            version = line.split(":", 1)[1].strip()
        elif line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
        elif line.startswith("# subgroup:"):
            subgroup = line.split(":", 1)[1].strip()

        if "; fully-qualified" not in line:
            continue
        codepoints, rest = line.split(";", 1)
        if rest.split("#", 1)[0].strip() != "fully-qualified":
            continue
        comment = line.split("#", 1)[1].strip()
        comment_parts = comment.split(" ", 2)
        records.append(
            {
                "emoji": codepoints_to_emoji(codepoints.strip()),
                "version": comment_parts[1] if len(comment_parts) > 1 else "",
                "name": comment_parts[2] if len(comment_parts) > 2 else comment,
                "group": group,
                "subgroup": subgroup,
            }
        )
    return version, records


def load_category_files(
    emoji_dir: Path,
) -> tuple[dict[str, list[list[str]]], dict[str, tuple[str, int]]]:
    files: dict[str, list[list[str]]] = OrderedDict()
    token_locations: dict[str, tuple[str, int]] = {}
    for file_name in GROUP_TO_FILE.values():
        lines: list[list[str]] = []
        for line in (emoji_dir / file_name).read_text(encoding="utf-8").splitlines():
            tokens = [token for token in line.split(" ") if token]
            if not tokens:
                continue
            line_index = len(lines)
            lines.append(tokens)
            for token in tokens:
                token_locations[token] = (file_name, line_index)
        files[file_name] = lines
    return files, token_locations


def load_min_api(path: Path) -> OrderedDict[int, list[str]]:
    result: OrderedDict[int, list[str]] = OrderedDict()
    for line in path.read_text(encoding="utf-8").splitlines():
        tokens = [token for token in line.split(" ") if token]
        if tokens:
            result[int(tokens[0])] = tokens[1:]
    return result


def min_api_for_version(version: str) -> int | None:
    # Android system emoji fonts can update independently of the OS. Unknown
    # versions are therefore runtime-gated with Paint.hasGlyph instead of being
    # exposed through an invented API level.
    if version == "E17.0":
        return 0
    return VERSION_TO_MIN_API.get(version)


def update_categories(emoji_dir: Path, records: list[dict[str, str]]) -> int:
    files, token_locations = load_category_files(emoji_dir)
    local_tokens = set(token_locations)
    added_records: list[dict[str, str]] = []
    new_lines_by_file: dict[str, OrderedDict[str, list[str]]] = {
        file_name: OrderedDict() for file_name in GROUP_TO_FILE.values()
    }

    for record in records:
        emoji = record["emoji"]
        if emoji in local_tokens:
            continue
        file_name = GROUP_TO_FILE.get(record["group"])
        if not file_name:
            continue
        base = strip_skin_tones(emoji)
        if base in token_locations and emoji != base:
            base_file, line_index = token_locations[base]
            if emoji not in files[base_file][line_index]:
                files[base_file][line_index].append(emoji)
                token_locations[emoji] = (base_file, line_index)
                local_tokens.add(emoji)
                added_records.append(record)
            continue
        grouped = new_lines_by_file[file_name].setdefault(base, [])
        if emoji == base:
            grouped.insert(0, emoji)
        elif emoji not in grouped:
            grouped.append(emoji)
        token_locations[emoji] = (file_name, -1)
        local_tokens.add(emoji)
        added_records.append(record)

    for file_name, grouped_lines in new_lines_by_file.items():
        for base, tokens in grouped_lines.items():
            if base not in tokens:
                tokens.insert(0, base)
            files[file_name].append(list(OrderedDict.fromkeys(tokens)))

    for file_name, lines in files.items():
        (emoji_dir / file_name).write_text(
            "".join(" ".join(tokens) + "\n" for tokens in lines),
            encoding="utf-8",
        )

    min_api_path = emoji_dir / "minApi.txt"
    min_api = load_min_api(min_api_path)
    known_gated = {emoji for emojis in min_api.values() for emoji in emojis}
    for record in added_records:
        api = min_api_for_version(record["version"])
        if api is None or record["emoji"] in known_gated:
            continue
        min_api.setdefault(api, []).append(record["emoji"])
        known_gated.add(record["emoji"])
    min_api_path.write_text(
        "".join(f"{api} {' '.join(emojis)}\n" for api, emojis in sorted(min_api.items())),
        encoding="utf-8",
    )
    return len(added_records)


def load_project_emojis(emoji_dir: Path) -> set[str]:
    emojis: set[str] = set()
    for path in sorted(emoji_dir.glob("*.txt")):
        if path.name == "minApi.txt":
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            emojis.update(token for token in line.split(" ") if token)
    return emojis


def build_search_rows(
    annotations: dict[str, dict], allowed_emojis: set[str]
) -> list[tuple[str, str, list[str]]]:
    rows: list[tuple[str, str, list[str]]] = []
    for emoji, payload in annotations.items():
        if emoji not in allowed_emojis or not isinstance(payload, dict):
            continue
        tts_values = payload.get("tts") or []
        keywords = payload.get("default") or []
        if not isinstance(tts_values, list):
            tts_values = [str(tts_values)]
        if not isinstance(keywords, list):
            keywords = [str(keywords)]
        name = normalize_field(str(tts_values[0])) if tts_values else ""
        normalized_keywords = list(
            OrderedDict.fromkeys(
                normalize_field(str(keyword)) for keyword in keywords if str(keyword).strip()
            )
        )
        if name or normalized_keywords:
            rows.append((emoji, name, normalized_keywords))
    return sorted(rows, key=lambda row: row[0])


def write_search_tsv(path: Path, rows: list[tuple[str, str, list[str]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "".join(f"{emoji}\t{name}\t{'|'.join(keywords)}\n" for emoji, name, keywords in rows),
        encoding="utf-8",
    )


def write_shortcode_index(
    path: Path,
    english_annotations: dict[str, dict],
    allowed_emojis: set[str],
) -> None:
    index: dict[str, list[str]] = {}
    for emoji, payload in english_annotations.items():
        if emoji not in allowed_emojis or not isinstance(payload, dict):
            continue
        values = list(payload.get("tts") or []) + list(payload.get("default") or [])
        for value in values:
            shortcode = normalize_shortcode(str(value))
            if not shortcode:
                continue
            matches = index.setdefault(shortcode, [])
            if emoji not in matches:
                matches.append(emoji)
    path.write_text(
        json.dumps(dict(sorted(index.items())), ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


def rebuild_shortcodes_from_committed_search(root: Path) -> None:
    search_path = root / "app/src/main/assets/common/emoji_search/en.tsv"
    annotations: dict[str, dict[str, list[str]]] = {}
    for line in search_path.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        emoji = parts[0]
        name = parts[1]
        keywords = parts[2].split("|") if len(parts) > 2 and parts[2] else []
        annotations[emoji] = {"tts": [name] if name else [], "default": keywords}
    emoji_dir = root / "app/src/main/assets/common/emoji"
    write_shortcode_index(
        root / "app/src/main/assets/common/emoji_shortcodes.json",
        annotations,
        load_project_emojis(emoji_dir),
    )


def generate(root: Path) -> tuple[str, int]:
    emoji_dir = root / "app/src/main/assets/common/emoji"
    search_dir = root / "app/src/main/assets/common/emoji_search"
    shortcode_path = root / "app/src/main/assets/common/emoji_shortcodes.json"
    emoji_version, records = parse_emoji_test(fetch_text(EMOJI_TEST_URL))
    added_count = update_categories(emoji_dir, records)
    allowed_emojis = load_project_emojis(emoji_dir)
    annotations_by_locale = {locale: fetch_annotations(locale) for locale in SEARCH_LOCALES}
    for locale, annotations in annotations_by_locale.items():
        write_search_tsv(search_dir / f"{locale}.tsv", build_search_rows(annotations, allowed_emojis))
    write_shortcode_index(shortcode_path, annotations_by_locale["en"], allowed_emojis)
    return emoji_version, added_count


def generated_files(root: Path) -> list[Path]:
    common = root / "app/src/main/assets/common"
    return sorted((common / "emoji").glob("*.txt")) + sorted(
        (common / "emoji_search").glob("*.tsv")
    ) + [common / "emoji_shortcodes.json"]


def check(root: Path) -> int:
    with tempfile.TemporaryDirectory(prefix="pastiera-emoji-check-") as temp_dir:
        temp_root = Path(temp_dir)
        source_common = root / "app/src/main/assets/common"
        target_common = temp_root / "app/src/main/assets/common"
        shutil.copytree(source_common / "emoji", target_common / "emoji")
        shutil.copytree(source_common / "emoji_search", target_common / "emoji_search")
        shortcode_source = source_common / "emoji_shortcodes.json"
        if shortcode_source.exists():
            shutil.copy2(shortcode_source, target_common / shortcode_source.name)
        generate(temp_root)
        stale: list[str] = []
        for generated in generated_files(temp_root):
            relative = generated.relative_to(temp_root)
            committed = root / relative
            if not committed.exists() or not filecmp.cmp(generated, committed, shallow=False):
                stale.append(str(relative))
        if stale:
            print("Generated emoji assets are stale:", file=sys.stderr)
            for path in stale:
                print(f"  {path}", file=sys.stderr)
            return 1
    print("Generated emoji assets are current")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify assets without modifying the checkout")
    parser.add_argument(
        "--rebuild-shortcodes-from-search",
        action="store_true",
        help="rebuild only the compact index from the committed English search asset",
    )
    args = parser.parse_args()
    root = repo_root_from_script()
    if args.rebuild_shortcodes_from_search:
        rebuild_shortcodes_from_committed_search(root)
        return 0
    if args.check:
        return check(root)
    emoji_version, added_count = generate(root)
    print(f"Unicode Emoji {emoji_version}; added picker entries: {added_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
