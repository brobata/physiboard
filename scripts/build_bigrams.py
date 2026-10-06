#!/usr/bin/env python3
"""Builds the `.bigrams` context table autocorrect reads, plus the held-out sentence corpora the
evaluation harness replays.

Source: the Tatoeba English sentence export (https://tatoeba.org, CC BY 2.0 FR), which allows
redistribution with attribution under both of PhysiBoard's licences. Download it with

    curl -sSfL -o eng_sentences.tsv.bz2 \\
        https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2
    bunzip2 eng_sentences.tsv.bz2

Every tenth sentence (by a hash of its Tatoeba id, so the split is stable across exports) is held
out of the counts and written to `--heldout` instead, so the harness measures on sentences the
table never saw.

Tokenisation mirrors the keyboard's own word tracking: lowercase, curly apostrophes straightened,
a word is letters with at most one internal apostrophe. A sentence start is its own context
(`SENTENCE_START`, id 0). `.`, `!`, `?`, `;` and `:` also reset the context; a comma does not
(the keyboard treats it as a soft boundary too). A word missing from the dictionary breaks the
chain: no pair is recorded into or out of it.

Output (big-endian, the same conventions as `.pbd`):

    int   magic 0x50424731 ("PBG1")
    short version 1
    short reserved 0
    int   vocabulary size V (including id 0, the sentence-start context)
    V-1 x (short byteLength, UTF-8 bytes)      words for ids 1..V-1
    V   x int                                   unigram count per id (id 0: sentence count)
    V+1 x int                                   row offsets into the pair arrays (CSR)
    P   x int                                   next-word id, ascending within each row
    P   x short                                 pair count, saturating at 65535
    int   CRC32 of everything above

Requires only the Python standard library.

Usage:
    python3 scripts/build_bigrams.py --pbd app/src/main/assets/dictionaries/en.pbd \\
        --sentences /path/eng_sentences.tsv \\
        --out app/src/main/assets/dictionaries/en.bigrams \\
        --heldout core/text/src/test/resources/autocorrect/en_heldout_sentences.txt
"""

from __future__ import annotations

import argparse
import re
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

MAGIC = 0x50424731
VERSION = 1
WORD_BLOCK_TAG = 0x574F5244
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?|[.!?;:]")
RESETS = set(".!?;:")


def read_pbd_words(path: Path) -> list[str]:
    data = path.read_bytes()
    # Header: magic int, version short, 4-byte language, reserved short, count int, crc int.
    pos = 4 + 2 + 4 + 2 + 4 + 4
    while pos < len(data):
        tag, length = struct.unpack_from(">ii", data, pos)
        pos += 8
        if tag == WORD_BLOCK_TAG:
            (count,) = struct.unpack_from(">i", data, pos)
            table = pos + 4
            payload = table + count * 8
            words = []
            for i in range(count):
                offset, size, _freq = struct.unpack_from(">iHH", data, table + i * 8)
                words.append(data[payload + offset:payload + offset + size].decode("utf-8"))
            return words
        pos += length
    raise SystemExit(f"{path}: no WORD block")


def tokens(sentence: str) -> list[str]:
    s = sentence.lower().replace("’", "'").replace("‘", "'")
    return TOKEN.findall(s)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pbd", required=True, type=Path)
    ap.add_argument("--sentences", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--heldout", required=True, type=Path)
    ap.add_argument("--min-count", type=int, default=2)
    args = ap.parse_args()

    vocab_words = read_pbd_words(args.pbd)
    known = set(vocab_words)
    unigrams: Counter[str] = Counter()
    pairs: Counter[tuple[str, str]] = Counter()
    sentences = 0
    heldout: list[str] = []

    with args.sentences.open(encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            sid, text = parts[0], parts[2]
            if zlib.crc32(sid.encode()) % 10 == 0:
                heldout.append(text)
                continue
            sentences += 1
            prev: str | None = ""  # "" is the sentence-start context
            for tok in tokens(text):
                if tok in RESETS:
                    prev = ""
                    continue
                if tok not in known:
                    prev = None
                    continue
                unigrams[tok] += 1
                if prev is not None:
                    pairs[(prev, tok)] += 1
                prev = tok

    kept = {k: v for k, v in pairs.items() if v >= args.min_count}
    # Ids: 0 is the sentence-start context, then every dictionary word seen at least once in a
    # kept pair or as a unigram, most frequent first.
    ordered = [w for w, _ in unigrams.most_common()]
    ids = {"": 0}
    for w in ordered:
        ids[w] = len(ids)
    rows: dict[int, list[tuple[int, int]]] = {}
    for (a, b), c in kept.items():
        rows.setdefault(ids[a], []).append((ids[b], min(c, 0xFFFF)))

    out = bytearray()
    out += struct.pack(">iHHi", MAGIC, VERSION, 0, len(ids))
    for w in ordered:
        b = w.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    out += struct.pack(">i", sentences)
    for w in ordered:
        out += struct.pack(">i", unigrams[w])
    offsets = [0]
    next_ids: list[int] = []
    counts: list[int] = []
    for i in range(len(ids)):
        for nid, c in sorted(rows.get(i, [])):
            next_ids.append(nid)
            counts.append(c)
        offsets.append(len(next_ids))
    out += struct.pack(f">{len(offsets)}i", *offsets)
    out += struct.pack(f">{len(next_ids)}i", *next_ids)
    out += struct.pack(f">{len(counts)}H", *counts)
    out += struct.pack(">I", zlib.crc32(out) & 0xFFFFFFFF)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(out)
    args.heldout.parent.mkdir(parents=True, exist_ok=True)
    args.heldout.write_text("\n".join(heldout) + "\n", encoding="utf-8")
    print(
        f"sentences={sentences} heldout={len(heldout)} vocab={len(ids)} "
        f"pairs={len(next_ids)} (of {len(pairs)} seen) bytes={len(out)}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
