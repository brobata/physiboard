#!/usr/bin/env python3
"""Builds a `.pbd` dictionary from a frequency ranking intersected with a spelling lexicon.

spec: docs/spec/autocorrect-suggestions.md §11 (the English word-list pipeline: inputs, filters,
the 80,000 entry size) and docs/plans/rebuild-from-scratch.md's "dictionary coverage and
licensing problem" section, which explains why this recipe is proven for English only.

The recipe (unchanged from the spec's description of the 2.x pipeline, only the output format
changed: this script writes `.pbd` bytes directly instead of an intermediate JSON plus a
separate SymSpell-serialization pass):

    1. Take the top `multiplier * size` words from the `wordfreq` frequency ranking for the
       language (a blend of subtitles, web, books, Twitter and Wikipedia, whichever sources
       `wordfreq` has for that language).
    2. Keep a word only if it matches `^[a-z]+(?:'[a-z]+)?$` (lowercase letters with at most one
       internal apostrophe) and is not a single letter other than `a` or `i`.
    3. Keep a word only if it is also in the `pyspellchecker` spelling lexicon for that language.
       This is the filter that keeps real misspellings out of the list: without it, a frequency
       ranking alone contains `alot`, `teh`, `thier`, `seperate`, ... in its top 80,000, and any
       of those becomes a "known word" the keyboard can never correct again.
    4. Take the first `size` survivors. Warn if fewer are available.
    5. Map each survivor's `wordfreq` Zipf frequency linearly from the survivor list's own
       [min, max] onto [66, 222], rounded and clamped, matching the raw-frequency scale
       `:core:text`'s `SuggestionRanking.effectiveFrequency` and its rare-completion thresholds
       are written against (see core/text/.../SuggestionRanking.kt, "raw 0..255 to effective
       frequency").
    6. Write a `.pbd` file: PbdReader's byte-for-byte contract (core/dict/.../PbdReader.kt KDoc),
       reproduced here in Python since the builder runs outside the JVM.

ONLY ENGLISH IS BUILT THIS WAY TODAY. Step 3's lexicon exists in `pyspellchecker` for eight of
the nineteen languages PhysiBoard eventually wants (de, en, es, fr, it, nl, pt, ru); the other
eleven either have no lexicon in this library at all, or have one whose redistribution licence
has not been checked against PhysiBoard's dual GPLv3/commercial licensing (most of the readily
available spelling dictionaries are GPL-family, which is fine for the GPLv3 side and a problem
for the commercial one). Measured attempts at a licence-free, corpus-only substitute for the
lexicon failed the "a correctly spelled word is never removed" invariant (rebuild-from-scratch.md:
`ember` and `tv` were dropped, `teh`/`thier`/`wich` survived). So this script refuses, by default,
to build a language whose lexicon it cannot find, rather than silently shipping a defective list;
see `--allow-missing-lexicon` below. docs/dictionaries.md records this decision and the standing
question for whoever resolves the other eighteen languages.

Requires: pip install wordfreq pyspellchecker

Usage:
    python3 scripts/build_dictionary.py --lang en --size 80000 \\
        --out app/src/main/assets/dictionaries/en.pbd \\
        --tsv /tmp/vocab_80k.tsv
"""

from __future__ import annotations

import argparse
import re
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------------------------------------
# The `.pbd` byte layout. This MUST match core/dict/.../PbdReader.kt's KDoc exactly: it is a
# contract with the Kotlin reader, not a format this script is free to reinterpret. Do not change
# these without changing PbdReader/PbdWriter and bumping the format version in both places.
# ---------------------------------------------------------------------------------------------

MAGIC = 0x50424431  # ASCII "PBD1"
FORMAT_VERSION = 1
LANGUAGE_FIELD_BYTES = 4
WORD_BLOCK_TAG = 0x574F5244  # ASCII "WORD"

# Step 5's target scale: matches the English list's historical raw-frequency range, which is also
# the scale `:core:text`'s rare-completion filters and effective-frequency formula assume.
RAW_FREQUENCY_MIN = 66
RAW_FREQUENCY_MAX = 222

SHAPE_PATTERN = re.compile(r"^[a-z]+(?:'[a-z]+)?$")
SINGLE_LETTER_ALLOWED = {"a", "i"}

# Every language `pyspellchecker` ships a bundled dictionary for, as of this writing. A language
# not in this set has no lexicon this script can intersect with, so it is refused unless the
# caller explicitly overrides that with --allow-missing-lexicon.
PYSPELLCHECKER_LANGUAGES = {"ar", "de", "en", "es", "eu", "fa", "fr", "it", "lv", "nl", "pt", "ru"}


@dataclass(frozen=True)
class WordEntry:
    word: str
    raw_frequency: int


def encode_pbd(language: str, entries: list[WordEntry]) -> bytes:
    """Encodes `entries` for `language` into `.pbd` bytes. Mirrors PbdWriter.writeTo exactly:
    entries de-duplicated by spelling, sorted ascending, one WORD block, CRC-32 over the WORD
    block's tag+length+payload (everything after the 20-byte header)."""
    by_word: dict[str, int] = {}
    for entry in entries:
        if not (0 <= entry.raw_frequency <= 0xFFFF):
            raise ValueError(f"frequency {entry.raw_frequency} for {entry.word!r} does not fit an unsigned 16-bit field")
        by_word[entry.word] = entry.raw_frequency
    sorted_words = sorted(by_word.keys())

    word_bytes = []
    for word in sorted_words:
        encoded = word.encode("utf-8")
        if len(encoded) > 0xFFFF:
            raise ValueError(f"word {word!r} is too long to encode ({len(encoded)} bytes)")
        word_bytes.append(encoded)

    records = bytearray()
    offset = 0
    for word, encoded in zip(sorted_words, word_bytes):
        records += struct.pack(">iHH", offset, len(encoded), by_word[word])
        offset += len(encoded)

    blob = b"".join(word_bytes)
    block = struct.pack(">i", len(sorted_words)) + bytes(records) + blob

    body = struct.pack(">i", WORD_BLOCK_TAG) + struct.pack(">i", len(block)) + block
    checksum = zlib.crc32(body) & 0xFFFFFFFF

    lang_bytes = language.encode("ascii")
    if len(lang_bytes) > LANGUAGE_FIELD_BYTES:
        raise ValueError(f"language code {language!r} does not fit the {LANGUAGE_FIELD_BYTES}-byte header field")
    lang_field = lang_bytes + b"\x00" * (LANGUAGE_FIELD_BYTES - len(lang_bytes))

    header = (
        struct.pack(">i", MAGIC)
        + struct.pack(">H", FORMAT_VERSION)
        + lang_field
        + struct.pack(">H", 0)  # reserved
        + struct.pack(">i", len(sorted_words))
        + struct.pack(">I", checksum)
    )
    return header + body


def wordfreq_license_note() -> str:
    return (
        "wordfreq: package code is Apache-2.0 (Robyn Speer); the frequency data it ships is "
        "redistributed under CC BY-SA 4.0 (per wordfreq's own README, 'wordfreq contains data "
        "extracted from ... Google Books Ngrams ... and data derived from ... Creative "
        "Commons-licensed sources', including OpenSubtitles/OPUS and the Leeds Internet Corpus). "
        "CC BY-SA is share-alike: a derived word list built from it should carry the same "
        "attribution/share-alike terms unless that is separately cleared."
    )


def pyspellchecker_license_note(lang: str, available: bool) -> str:
    if available:
        return (
            f"pyspellchecker ({lang}): package is MIT (Tyler Barrus). Used here only as a "
            "spelling-membership filter (word set), not for its own frequencies. The bundled "
            "per-language dictionaries derive from mixed public corpora (Wiktionary, Norvig's "
            "big.txt for English, etc.); pyspellchecker's own MIT licence covers the code, not "
            "necessarily every source word list, so this is not a final answer for languages "
            "shipped under the commercial licence — see docs/dictionaries.md."
        )
    return (
        f"pyspellchecker: NO lexicon bundled for '{lang}'. This language has no spelling filter "
        "available from this pipeline; see docs/plans/rebuild-from-scratch.md's licensing section."
    )


def build_word_list(lang: str, size: int, multiplier: int, allow_missing_lexicon: bool) -> tuple[list[WordEntry], bool]:
    """Runs steps 1-5 of the recipe. Returns (entries, lexicon_was_used)."""
    try:
        from wordfreq import top_n_list, zipf_frequency
    except ImportError as e:
        sys.exit(f"error: wordfreq is not installed ({e}). pip install wordfreq pyspellchecker")

    lexicon_available = lang in PYSPELLCHECKER_LANGUAGES
    lexicon_words: set[str] | None = None
    if lexicon_available:
        try:
            from spellchecker import SpellChecker
        except ImportError as e:
            sys.exit(f"error: pyspellchecker is not installed ({e}). pip install wordfreq pyspellchecker")
        lexicon_words = set(SpellChecker(language=lang).word_frequency.dictionary.keys())
    elif not allow_missing_lexicon:
        sys.exit(
            f"error: no pyspellchecker lexicon for language '{lang}'. Frequency alone is known "
            "to admit real misspellings as 'known words' (docs/plans/rebuild-from-scratch.md). "
            "Pass --allow-missing-lexicon to build an unfiltered, lower-quality list anyway "
            "(not recommended to ship)."
        )
    else:
        print(
            f"WARNING: building '{lang}' with NO spelling lexicon (--allow-missing-lexicon). "
            "This list will very likely contain misspellings it can never correct. Do not ship "
            "this without resolving the licensing/coverage question in docs/dictionaries.md.",
            file=sys.stderr,
        )

    candidate_pool = top_n_list(lang, multiplier * size, wordlist="best")

    survivors: list[str] = []
    for word in candidate_pool:
        if len(survivors) >= size:
            break
        if not SHAPE_PATTERN.match(word):
            continue
        if len(word) == 1 and word not in SINGLE_LETTER_ALLOWED:
            continue
        if lexicon_words is not None and word not in lexicon_words:
            continue
        survivors.append(word)

    if len(survivors) < size:
        print(
            f"WARNING: only {len(survivors)} survivors, fewer than the requested {size}. "
            "Consider a larger --multiplier.",
            file=sys.stderr,
        )

    zipf_scores = {w: zipf_frequency(w, lang, wordlist="best") for w in survivors}
    lo = min(zipf_scores.values())
    hi = max(zipf_scores.values())
    span = hi - lo

    entries: list[WordEntry] = []
    for word in survivors:
        if span <= 0:
            scaled = RAW_FREQUENCY_MAX
        else:
            fraction = (zipf_scores[word] - lo) / span
            scaled = RAW_FREQUENCY_MIN + fraction * (RAW_FREQUENCY_MAX - RAW_FREQUENCY_MIN)
        raw = int(round(scaled))
        raw = max(RAW_FREQUENCY_MIN, min(RAW_FREQUENCY_MAX, raw))
        entries.append(WordEntry(word=word, raw_frequency=raw))

    return entries, lexicon_words is not None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", required=True, help="two-letter language code, e.g. en")
    parser.add_argument("--out", required=True, type=Path, help="output .pbd path")
    parser.add_argument("--size", type=int, default=80000, help="target entry count (default 80000)")
    parser.add_argument("--multiplier", type=int, default=6, help="how many times --size to pull from the frequency ranking before filtering (default 6)")
    parser.add_argument("--tsv", type=Path, default=None, help="optional word<TAB>frequency dump, for the evaluation sweep")
    parser.add_argument(
        "--allow-missing-lexicon",
        action="store_true",
        help="build a frequency-only list even when no pyspellchecker lexicon exists for --lang (NOT recommended to ship; see docs/dictionaries.md)",
    )
    args = parser.parse_args()

    lang = args.lang.lower()
    if not re.match(r"^[a-z]{2}$", lang):
        sys.exit(f"error: --lang must be a two-letter code, got {args.lang!r}")

    entries, lexicon_used = build_word_list(lang, args.size, args.multiplier, args.allow_missing_lexicon)
    if not entries:
        sys.exit("error: zero survivors, refusing to write an empty dictionary")

    pbd_bytes = encode_pbd(lang, entries)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(pbd_bytes)

    if args.tsv is not None:
        args.tsv.parent.mkdir(parents=True, exist_ok=True)
        with args.tsv.open("w", encoding="utf-8") as f:
            for entry in sorted(entries, key=lambda e: e.word):
                f.write(f"{entry.word}\t{entry.raw_frequency}\n")

    size_mb = len(pbd_bytes) / (1024 * 1024)
    print("=" * 78)
    print(f"Built {args.out} for language '{lang}'")
    print(f"  entries:    {len(entries)}")
    print(f"  file size:  {len(pbd_bytes)} bytes ({size_mb:.2f} MiB)")
    print(f"  raw freq:   [{RAW_FREQUENCY_MIN}, {RAW_FREQUENCY_MAX}] (lexicon filter {'applied' if lexicon_used else 'NOT applied'})")
    print("-" * 78)
    print("Input licences (review before shipping a NEW language, English already cleared):")
    print(f"  - {wordfreq_license_note()}")
    print(f"  - {pyspellchecker_license_note(lang, lexicon_used)}")
    print("=" * 78)


if __name__ == "__main__":
    main()
