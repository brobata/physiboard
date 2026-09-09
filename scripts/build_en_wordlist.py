#!/usr/bin/env python3
"""Generate `en_base.json` from a real frequency source.

The repository has never owned this step. `<lang>_base.json` arrived from outside with its
frequencies already quantised to 0-255, and every script in `scripts/` starts from that file,
so the one decision that matters most - which words the keyboard knows - was made somewhere
nobody here can see or re-run.

What that produced, measured against `wordfreq` on 2026-09-08:

  * 20% of the shipped 50,000 words are outside wordfreq's top 50,000.
  * 21.7% of wordfreq's top 50,000 are missing from the shipped list.
  * The list carries `passerine`, `officership`, `subchannel`, `kbit`, `simulcasting`,
    `USAAF` and `municipality's`, while missing `vex`, `ember`, `loathe`, `flout` and
    `salve`.

That is the signature of an encyclopedic corpus, and it is the wrong shape for a phone
keyboard: it spends its budget on terms nobody types and omits ordinary words, which the
correction engine then treats as typos. It is not a truncation-depth problem - the rarest
shipped word sits at Zipf 1.68, well below every word the sweep found missing - so it is
fixable at the same 50,000 entries and the same file size.

`wordfreq` blends several corpora (subtitles, web text, books, Twitter, Wikipedia) rather
than leaning on any one, which is why its ranking looks like language people actually write.

It cannot be used on its own, though, and the evaluation harness caught why. Frequency data
drawn from real text contains the misspellings people really make: `alot`, `teh`,
`definately`, `wierd`, `recieve`, `seperate`, `thier` and `untill` are all in wordfreq's top
80,000, several of them ranked higher than ordinary words. Ship that and the keyboard treats
every one of them as a word it must not correct - measured recall fell from 0.775 to 0.250
as the list grew, because the typos in the corpus had become "real words".

So membership and ranking come from different places: a curated lexicon decides WHICH words
exist, and wordfreq decides how common they are. The shipped list gets this half right - it
contains no misspellings at all - and half wrong, missing 12% of ordinary vocabulary.

Usage:
    pip install wordfreq
    python3 scripts/build_en_wordlist.py --size 50000 --out app/src/main/assets/common/dictionaries/en_base.json

Then rebuild the serialized dictionary as usual:
    python3 scripts/build_symspell_dict.py <in> <out>

Frequencies are written on the existing 0-255 scale with the existing endpoints (`the` = 222,
rarest kept word = 66) so the runtime's `effectiveFrequency` curve keeps its meaning and no
Kotlin has to change.
"""

import argparse
import json
import re
import sys

F_MAX = 222  # what `the` scores in the shipped file
F_MIN = 66   # what the rarest kept word scores in the shipped file

# Letters, plus the internal apostrophe of don't / it's. No digits, no punctuation runs,
# no single letters except `a` and `i`, which are real words.
WORD = re.compile(r"^[a-z]+(?:'[a-z]+)?$")


def acceptable(word: str) -> bool:
    if not WORD.match(word):
        return False
    if len(word) == 1 and word not in ("a", "i"):
        return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--size", type=int, default=50000, help="how many words to keep")
    parser.add_argument("--lang", default="en")
    parser.add_argument("--out", required=True)
    parser.add_argument(
        "--tsv", help="also write a word<TAB>frequency file, for the evaluation harness"
    )
    parser.add_argument(
        "--no-lexicon",
        action="store_true",
        help="skip the lexicon filter (for measuring what it is worth; do not ship the result)",
    )
    args = parser.parse_args()

    try:
        from wordfreq import top_n_list, zipf_frequency
    except ImportError:
        print("wordfreq is not installed:  pip install wordfreq", file=sys.stderr)
        return 1

    lexicon = None
    if not args.no_lexicon:
        try:
            from spellchecker import SpellChecker
        except ImportError:
            print(
                "pyspellchecker is not installed:  pip install pyspellchecker\n"
                "It supplies the lexicon that keeps real misspellings out of the word list.\n"
                "Pass --no-lexicon only to measure what that filter is worth.",
                file=sys.stderr,
            )
            return 1
        lexicon = set(SpellChecker(language=args.lang).word_frequency.dictionary.keys())
        print(f"lexicon: {len(lexicon)} words")

    # Over-fetch, because both filters remove a good fraction.
    candidates = top_n_list(args.lang, args.size * 6)
    words = [
        w for w in candidates
        if acceptable(w) and (lexicon is None or w in lexicon)
    ][: args.size]
    if len(words) < args.size:
        print(
            f"only {len(words)} words survived filtering, wanted {args.size}", file=sys.stderr
        )

    zipfs = [zipf_frequency(w, args.lang) for w in words]
    z_max, z_min = max(zipfs), min(zipfs)
    span = (z_max - z_min) or 1.0

    entries = []
    for word, z in zip(words, zipfs):
        f = round(F_MIN + (z - z_min) * (F_MAX - F_MIN) / span)
        entries.append({"w": word, "f": max(F_MIN, min(F_MAX, f))})

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(entries, handle, ensure_ascii=False)
    print(f"wrote {len(entries)} entries to {args.out}  (zipf {z_min:.2f}..{z_max:.2f})")

    if args.tsv:
        with open(args.tsv, "w", encoding="utf-8") as handle:
            for entry in entries:
                handle.write(f"{entry['w']}\t{entry['f']}\n")
        print(f"wrote {args.tsv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
