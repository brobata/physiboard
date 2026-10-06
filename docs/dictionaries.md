# Bundled dictionaries

How PhysiBoard's `.pbd` word lists are built, what is shipped today, and why only English.

## The recipe

`scripts/build_dictionary.py` builds a `.pbd` from two independent, freely-available sources
intersected together:

1. A **frequency ranking** (`wordfreq`), which knows what people actually write.
2. A **spelling lexicon** (`pyspellchecker`), which knows what is a real word.

Frequency alone is not enough: real text contains real misspellings, so a list built from
frequency alone contains `alot`, `teh`, `thier`, `seperate`, ... in its own top 80,000, and any
word that clears that filter becomes a "known word" the keyboard can never correct again (spec:
`docs/spec/autocorrect-suggestions.md` §10, the measured 2.x incident this exists to prevent).
The lexicon is the filter that keeps those out.

The full recipe (candidate pool size, the shape filter, the lexicon filter, the frequency-to-raw
scale mapping, the `.pbd` byte layout) is documented in the script's own header and in
`docs/spec/autocorrect-suggestions.md` §11.

## Why English only, today

`pyspellchecker` bundles a spelling lexicon for eight of the nineteen languages PhysiBoard
eventually wants to support: `de en es fr it nl pt ru`. The other eleven have no lexicon in that
library at all. Spelling dictionaries for most of them do exist elsewhere (Hunspell/aspell
packages and similar), but the large majority are GPL-family, and filtering a frequency list
through one arguably makes the resulting word list a derivative of it. That is harmless for
PhysiBoard's GPLv3 distribution and a real problem for the commercial licence
(`docs/plans/rebuild-from-scratch.md` §"License"), because a word list the maintainer cannot
relicense cannot ship in a commercial build.

A corpus-only substitute for the lexicon (no external word list, no licence question at all) was
tried and measured to fail the "a correctly spelled word is never removed" invariant: it dropped
real words (`ember`, `tv`) while still admitting real misspellings (`teh`, `thier`, `wich`). The
full experiment and its negative result are recorded in `docs/plans/rebuild-from-scratch.md`
("Building the 19 dictionaries: a coverage and licensing problem"). That result is why this
script *refuses*, by default, to build a language it has no lexicon for
(`--allow-missing-lexicon` overrides that refusal, loudly, for experimentation only; the output
is not meant to ship).

**Open decision, per language, before any of the other eighteen ship:** which lexicon to use and
whether its licence is compatible with the commercial build. This is not resolved here. Anyone
building a new language's `.pbd` should read the licence block the script prints for that
language's inputs and record the decision next to the shipped file, the way this document records
English's.

## What is bundled today

| File | Language | Built by | Entries | Size |
|---|---|---|---|---|
| `app/src/main/assets/dictionaries/en.pbd` | English (`en`) | `scripts/build_dictionary.py --lang en --size 80000` | 80,000 | ~1.2 MB |

Rebuild it with:

```
./dictenv/bin/python scripts/build_dictionary.py --lang en --size 80000 \
    --out app/src/main/assets/dictionaries/en.pbd
```

(or any Python environment with `wordfreq` and `pyspellchecker` installed; there is nothing
venv-specific about the script itself).

### Licences of the English build's inputs

- **`wordfreq`** (the frequency ranking): the package's own code is Apache-2.0. The frequency
  data it ships is redistributed under CC BY-SA 4.0 (per `wordfreq`'s own README: it "contains
  data extracted from Google Books Ngrams ... and data derived from ... Creative
  Commons-licensed sources", including OpenSubtitles/OPUS and the Leeds Internet Corpus). CC
  BY-SA is share-alike: a word list derived from it should carry the same attribution/share-alike
  terms unless that is separately cleared with whoever owns PhysiBoard's commercial licence.
- **`pyspellchecker`** (the spelling lexicon, English only): the package is MIT (Tyler Barrus).
  It is used here purely as a membership filter (a set of correctly-spelled words), never for its
  own frequencies. Its bundled English dictionary derives from mixed public corpora (Wiktionary,
  Peter Norvig's `big.txt`, ...); the MIT licence covers pyspellchecker's *code*, not necessarily
  every word in every bundled list, so this is a reasonable starting point rather than a final
  legal answer.

Neither of the above has been given a legal review for the commercial licence track as of this
writing; it is recorded here so the question is visible rather than assumed away, exactly the way
the build script's own printed report makes it visible for a future language.

## The loader

`:ime`'s `DictionaryAssetLoader` resolves a language against the same three tiers
`docs/spec/dictionaries-languages.md` SS3 describes (imported, then downloaded, then bundled),
decided purely by `:core:dict`'s `DictionaryTierResolver` and read from wherever it points:
`files/dictionaries/imported/<lang>.pbd`, `files/dictionaries/downloaded/<lang>.pbd` (both written
by `:app`'s `DictionaryFileStore`), or the bundled `dictionaries/<lang>.pbd` asset. Whichever file
resolves is read on a background thread and handed to
`brobata.physiboard.core.dict.DictionaryIndex.fromPbdBytes`, which parses `.pbd` bytes into a
queryable index or returns null for anything that is not a well-formed file (missing, truncated,
wrong magic, checksum mismatch). The keyboard starts up and accepts keystrokes immediately with no
dictionary loaded; `KeyboardSession` swaps `KeyboardPipeline.resources` in, on the main thread,
once the background load actually produces an index. A missing or corrupt file simply never calls
back, so the keyboard keeps typing with no suggestions rather than crashing.

Installing, importing or uninstalling a dictionary on the settings screen (`DictionaryFileStore`)
broadcasts `brobata.physiboard.ACTION_DICTIONARY_CHANGED` (package-internal,
`:core:dict`'s `DictionaryBroadcastActions`); `KeyboardSession` reloads every dictionary it has
loaded as soon as that arrives, so a newly installed dictionary is picked up immediately, without
waiting for the keyboard process to restart (SS17's Keep/Drop: "Per-process dictionary cache never
invalidated | Fix | Reload on install, import, uninstall"). Only English is bundled today, for the
licensing reason described above, so switching to a style whose language is not `en` and has no
downloaded or imported file loads no dictionary for it.

## The word-pair table (`en.bigrams`)

`app/src/main/assets/dictionaries/en.bigrams` tells autocorrect which words follow which, so it
can read the sentence: `I definately` becomes `I definitely` (never `defiantly`), and, with "Fix
mixed-up words" on, `wagged it's tail` becomes `wagged its tail`. It holds about 500,000 pairs of
dictionary words, each seen at least twice. How autocorrect uses it, and what it measured, is in
[`plans/autocorrect-context.md`](plans/autocorrect-context.md).

It is built by `scripts/build_bigrams.py` from the English sentences of
**[Tatoeba](https://tatoeba.org)**, used under **CC BY 2.0 FR**, which permits redistribution
(commercial included) with attribution; this paragraph and the app's About screen are that
attribution. Only counts of word pairs are shipped, never sentences. One sentence in ten, chosen
by its Tatoeba id, is held out of the counts; a 6,000-sentence sample of those is the test corpus
`core/text/src/test/resources/autocorrect/en_heldout_sentences.txt`, so the harness measures on
text the table never saw.

It is a separate asset rather than a block inside `en.pbd` because a downloaded or imported
`en.pbd` replaces the bundled one, and the pair table must not disappear with it.

Rebuild it with:

```
python3 scripts/build_bigrams.py --pbd app/src/main/assets/dictionaries/en.pbd \
    --sentences eng_sentences.tsv --out app/src/main/assets/dictionaries/en.bigrams \
    --heldout /tmp/heldout.txt
```
