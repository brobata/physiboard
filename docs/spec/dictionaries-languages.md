# Dictionaries and keyboard languages

This document specifies which languages the keyboard knows, what a dictionary file is and where
it lives, how dictionaries are hosted, discovered, downloaded, verified, installed, updated and
removed, how the user's personal words sit on top of them, how the set of enabled keyboard
languages is built and registered with Android, how the user switches language, how a language
picks a keyboard layout, and how the app's own interface language is chosen. Candidate
retrieval, scoring, the automatic-correction decision, the personal dictionary screen and the
English word-list build pipeline are specified in `autocorrect-suggestions.md` and are only
referenced here. The strip's geometry and button slots are in `status-bar.md`; layout contents
are in `layers-sym-alt.md`; the Ctrl, Alt and Shift state machines are in
`keys-and-modifiers.md`.

Vocabulary used below:

- **Language code**: a two-letter lowercase ISO 639-1 code such as `en`, `de`, `uk`.
- **Locale string**: a language code optionally followed by an underscore and a region, as
  Android's input-method subtypes use them: `en_US`, `de_DE`, `fr`. A **language tag** is the
  same with a hyphen: `en-US`. The keyboard treats both spellings as equal everywhere.
- **Base dictionary**: the per-language word list file named `<lang>_base.dict`.
- **Subtype**: one entry in Android's per-keyboard list of "input languages". Android shows
  subtypes in its keyboard picker and lets the user enable them in system settings.
- **Base subtype**: one of the twelve subtypes declared statically in the keyboard's manifest.
- **Input style**: a language plus keyboard layout pair the user configures in the app; each
  input style becomes an **additional subtype** registered with Android at runtime.
- **Primary language**: the language of the subtype Android currently reports as active. It
  selects the primary dictionary, the substitution rule set, the speech recognition language
  and the keyboard layout.
- **Extra suggestion language**: another language whose dictionary is consulted alongside the
  primary one for a given input style.

## 1. The nineteen languages

### 1.1 Inventory

| Code | Name shown in the download list | Hosted file size | Hosted `updatedAt` | Bundled in the APK | Base subtype | Layout from the mapping file | App UI translation |
|---|---|---|---|---|---|---|---|
| `cs` | Czech (Basic) | 9,926,592 B | 2026-07-05T13:52:33Z | no | no | qwerty (fallback) | no |
| `da` | Danish (Basic) | 13,381,053 B | 2026-03-31T11:30:04Z | yes | `da_DK` | qwerty | no |
| `de` | German (Basic) | 14,819,232 B | 2026-01-15T18:20:18Z | yes | `de_DE` | qwertz | yes |
| `el` | Greek (Basic) | 21,237,827 B | 2026-07-05T13:52:35Z | no | no | qwerty (fallback) | no |
| `en` | English (Basic) | 13,198,738 B | 2026-01-15T18:20:19Z | yes (21,491,827 B, a different build) | `en_US` | qwerty | yes (default) |
| `es` | Spanish (Basic) | 14,191,031 B | 2026-01-15T18:20:20Z | yes | `es_ES` | qwerty | yes |
| `fr` | French (Basic) | 13,919,878 B | 2026-01-15T18:20:20Z | yes | `fr_FR` | azerty | yes |
| `gd` | Scottish Gaelic | 2,409,298 B | 2026-01-15T19:15:48Z | no | no | qwerty (fallback) | no |
| `hu` | Hungarian (Basic) | 10,258,517 B | 2026-07-05T13:52:35Z | no | no | qwerty (fallback) | no |
| `it` | Italian (Basic) | 13,938,536 B | 2026-01-15T18:20:21Z | yes | `it_IT` | qwerty | yes |
| `nl` | Dutch (Basic) | 13,476,178 B | 2026-03-31T11:30:04Z | yes | no | qwerty (fallback) | no |
| `no` | Norwegian (Basic) | 13,102,923 B | 2026-03-31T11:26:23Z | yes | `no_NO` | norwegian_multitap_qwerty | no |
| `pl` | Polish (Basic) | 14,150,750 B | 2026-01-15T18:20:22Z | yes | `pl_PL` | qwerty | yes |
| `pt` | Portuguese (Basic) | 13,819,649 B | 2026-01-15T18:20:22Z | yes | `pt_PT` | qwerty | no |
| `ru` | Russian (Basic) | 21,054,932 B | 2026-01-15T18:20:23Z | yes | `ru_RU` | russian_translit | yes |
| `sv` | Swedish (Basic) | 13,482,111 B | 2026-07-05T13:52:36Z | no | no | qwerty (fallback) | no |
| `tr` | Turkish (Basic) | 13,837,862 B | 2026-07-05T13:52:37Z | no | no | turkish_multitap | no |
| `uk` | Ukrainian | 14,517,270 B | 2026-02-25T09:18:35Z | yes | `uk_UA` | ukrainian | yes |
| `vi` | Vietnamese (Basic) | 33,150,247 B | 2026-02-25T21:27:54Z | no | no | vietnamese_telex_qwerty | yes |

Twelve dictionaries are bundled (`da de en es fr it nl no pl pt ru uk`, 177 MB of assets);
seven are download-only (`cs el gd hu sv tr vi`). Serbian (`sr_RS`) has a base subtype and a
layout (`serbian_cyrillic`) but no dictionary anywhere, so it types without suggestions.
Armenian (`hy`) has an interface translation and no dictionary or subtype. A build-only
Lithuanian word list (`lt_base.json`) exists with no dictionary built from it.

Eleven bundled dictionaries are byte-for-byte the files the hosting repository serves; the
English one is not (section 2.2).

### 1.2 Provenance of the files

The dictionary files are inherited from Pastiera: a byte-for-byte copy of the upstream
`pastiera-dict` release 12, each file verified against the upstream SHA-256 before being
published under this project's own repository at `github.com/brobata/physiboard-dict`. It is an
independent copy, not a mirror, so an upstream deletion changes nothing. The English list was
later rebuilt by this project (section 11 of `autocorrect-suggestions.md`); every other list is
the upstream corpus unchanged.

The file format itself (section 2) is Pastiera's, and so is the `<lang>_base.dict` naming
contract. This is recorded here as a Keep/Drop fact: 3.0 defines its own format and hosting
path and leaves the `<lang>_base.dict` path untouched for 2.x installs. Nothing in this
document designs that format.

## 2. Dictionary data contracts

### 2.1 Build input: `<lang>_base.json`

A JSON array of objects `{"w": word, "f": frequency}`, one per word, in descending frequency
order. `w` keeps its original spelling and case (`Mario`, `Roma`, `casa`); `f` is an integer.

| List | Entries | `f` range | Entries starting with a capital |
|---|---|---|---|
| `en` | 80,000 | 66..222 | 0 |
| `da` | 50,000 | 70..225 | 16,526 |
| `de` | 50,000 | 73..216 | 25,312 |
| `es` | 50,000 | 70..225 | 1,771 |
| `fr` | 50,000 | 74..221 | 6,186 |
| `it` | 50,000 | 76..216 | 4,512 |
| `lt` | 50,000 | 87..213 | 3,361 |
| `nl` | 50,000 | 70..225 | 14,960 |
| `no` | 50,000 | 1..64,938 | 23,774 |
| `pl` | 50,000 | 86..218 | 4,093 |
| `pt` | 50,000 | 71..221 | 6,657 |
| `ru` | 50,000 | 87..192 | 2,299 |
| `uk` | 50,000 | 2..26,356 | 12,391 |

The nominal contract is `f` in 0..255. Norwegian and Ukrainian violate it with raw corpus
counts; at load time every frequency is clamped to 255 before scaling, so in those two
languages nearly every word saturates at the top of the scale and frequency stops
discriminating (section 15).

These files live under `common/dictionaries/` in the source tree but are excluded from the APK
by the asset pattern `*_base.json`; nothing reads them at runtime. The build tools that turn
them into `.dict` files are described in section 2.5.

### 2.2 The base dictionary: `<lang>_base.dict`

One file per language, named exactly `<lang>_base.dict` with `<lang>` lowercase. The file has
no header, no magic number, no embedded language, version or checksum: its identity is its
file name, and its version is whatever the hosting manifest says (section 5.1). The keyboard
accepts two encodings and tells them apart by the first byte:

- first byte `0x7B` (`{`): a UTF-8 JSON object (the legacy encoding);
- anything else: a CBOR map (the current encoding).

Both carry the same object with these fields:

| Field | Type | Required | Content |
|---|---|---|---|
| `normalizedIndex` | map of string to list of entry | yes | Key: the normalized form of the word (section 2.3). Value: every entry whose normalization is that key, so `e`, `è` and `é` share a bucket |
| `prefixCache` | map of string to list of entry | yes | Key: each prefix of a normalized key of length 1, 2, 3 and 4 (shorter keys contribute only their existing prefixes). Value: every entry under that prefix, sorted by `frequency` descending at build time |
| `symDeletes` | map of string to list of string | no | Key: a string obtained from the first 4 characters of a normalized key by deleting up to 2 characters (the empty string included). Value: the normalized keys (current builds) or 4-character prefixes (older builds) that produce it |
| `symMeta` | object `{"maxEditDistance": int, "prefixLength": int}` | no | Always `{2, 4}` in every shipped file; read back at load time |

An **entry** is an object `{"word": string, "frequency": int, "source": int}`. `word` keeps
the original case; `source` is `0` for a main-dictionary word in every shipped file. The build
tools document `1` as "user word", but the loader maps `1` to "default user word" and `2` to
"personal word" (section 7), so a file that used `1` would have its words ranked and excluded
like the shipped default user words. Unknown fields are ignored in both encodings; a missing
required field, a malformed file or a non-object root is a format error.

Only `symDeletes` and `symMeta` together enable the precomputed fuzzy index; when either is
absent the fuzzy index is built at load time from the keys (section 4.2).

Shipped files (a blank cell means the file was not decoded for this document; every CBOR file
has all four fields):

| File | Bytes | Encoding | Normalized keys | Prefix buckets | Delete buckets | `symMeta` |
|---|---|---|---|---|---|---|
| `da_base.dict` | 13,381,053 | CBOR | | | | 2, 4 |
| `de_base.dict` | 14,819,232 | CBOR | | | | 2, 4 |
| `en_base.dict` | 21,491,827 | CBOR | 77,361 | 16,861 | 9,467 | 2, 4 |
| `es_base.dict` | 14,191,031 | CBOR | | | | 2, 4 |
| `fr_base.dict` | 13,919,878 | CBOR | | | | 2, 4 |
| `it_base.dict` | 13,938,536 | CBOR | 49,558 | 10,020 | 6,579 | 2, 4 |
| `nl_base.dict` | 13,476,178 | CBOR | | | | 2, 4 |
| `no_base.dict` | 13,102,923 | CBOR | 44,275 | 15,526 | 9,963 | 2, 4 |
| `pl_base.dict` | 14,150,750 | CBOR | | | | 2, 4 |
| `pt_base.dict` | 13,819,649 | CBOR | | | | 2, 4 |
| `ru_base.dict` | 21,054,932 | CBOR | | | | 2, 4 |
| `uk_base.dict` | 14,517,270 | JSON (legacy) | 45,161 | 11,702 | none | none |

The bundled `en_base.dict` (80,000 words, rebuilt by this project, SHA-256 beginning
`19138c21`) is not the file the hosting repository serves as `en_base.dict` (the upstream
50,000-word build, 13,198,738 bytes, SHA-256 beginning `4e0244f7`). Every other bundled file
matches its hosted counterpart (for example `it_base.dict`, SHA-256 beginning `7776eb23` on
both sides). The consequence is in section 15.

Older CBOR builds (Italian, Norwegian and the other upstream files) store 4-character prefixes
in `symDeletes` values instead of whole keys, for example the empty-string key mapping to
`a`, `ab`, `ac`; the English rebuild stores whole keys (`h` mapping to `aah`, `ah`, `aha`).
The loader accepts both (section 4.2).

### 2.3 Normalization key

The key under which a word is indexed, at build time and at runtime:

1. straight apostrophes for `’`, `‘`, `ʼ`;
2. lowercase in the dictionary's language;
3. fold `œ`/`Œ` to `oe`, `æ`/`Æ` to `ae`, `ĳ`/`Ĳ` to `ij`, `ß` to `ss`;
4. Unicode NFD decomposition, then remove every combining mark (so `è` becomes `e`);
5. remove everything that is not a letter (digits, apostrophes and hyphens go too).

The Python build tools perform steps 2, 4 and 5 only. A file built without step 3 therefore
has `œil` under the key `œil`; at load time the keyboard re-normalizes every key with the full
rule and, when the result differs, adds the entries under the new key as well and rebuilds the
prefix buckets (section 4.2). A word that normalizes to the empty string (a lone apostrophe, a
number) is indexed under the empty key and is never suggested.

### 2.4 Installed-file sidecar: `<lang>.meta.json`

Next to every installed (not bundled) dictionary the keyboard writes a pretty-printed JSON
object:

| Field | Type | Meaning |
|---|---|---|
| `origin` | string | `"download"` or `"import"`; always equal to the tier the file sits in |
| `sha256` | string or absent | The manifest's checksum for a download; absent for an import |
| `bytes` | integer | The manifest's `bytes` for a download; the staged file length for an import; `0` if unknown |
| `sourceReleaseTag` | string or absent | Reserved; never written |
| `manifestUpdatedAt` | string or absent | The manifest item's `updatedAt` for a download; absent for an import |
| `installedAt` | integer | Wall-clock milliseconds when the file was placed |

Unknown fields are ignored. An unreadable sidecar is treated as absent.

### 2.5 The build tools

All in the repository's scripts folder. They are the only way a `.dict` is produced; the
keyboard never writes one.

| Tool | Input | Output | Notes |
|---|---|---|---|
| SymSpell builder (`build_symspell_dict.py`) | a `<lang>_base.json` array, or an existing `.dict` in either encoding | a CBOR `.dict` with all four fields | `--max_edit_distance` default 2, `--prefix_length` default 4; requires the `cbor2` package; deletes are generated from the first 4 characters of every key and map back to the whole key |
| Convert all (`convert_all_to_symspell.py`) | every `*_base.json` | one `.dict` per list in the serialized assets folder | runs the SymSpell builder per language |
| JSON to CBOR (`convert_dict_to_cbor.py`) | every `*_base.dict` whose first byte is `{` | the same file rewritten as CBOR | skips files already in CBOR; reported 20-30 percent smaller |
| Legacy preprocessor (`preprocess_dictionaries.py`, `preprocess-dictionaries.main.kts`) | `*_base.json` | JSON `.dict` with `normalizedIndex` and `prefixCache` only | the encoding `uk_base.dict` still uses; measured 130-250 ms load for 50,000 words against 800-1,300 ms for raw JSON |
| Truncate (`truncate_dict.py`) | a `<lang>_base.json` | the top N by `f` | default `--max_words` 20000 |
| Backup, truncate and convert (`backup_truncate_and_convert.py`) | every `*_base.json` | copies under `dict_backup/`, truncated lists, CBOR `.dict` files | `--max_words` default 20000 |
| English word list (`build_en_wordlist.py`) | `wordfreq` and `pyspellchecker` | `en_base.json` | specified in `autocorrect-suggestions.md` section 11 |

## 3. Where dictionaries live on the device

Three tiers, highest precedence first. All paths are relative to the app's private files
directory (`/data/data/brobata.physiboard/files/` on the device, `files/` below).

| Tier | Directory | Written by | Removed by |
|---|---|---|---|
| Imported | `files/dictionaries_serialized/imported/` | the Import action (section 5.6) | Uninstall on the installed-dictionaries screen |
| Downloaded | `files/dictionaries_serialized/downloaded/` | the Download action (section 5.3) | Uninstall |
| Bundled | APK assets `common/dictionaries_serialized/` | the app build | an app update only |

Both writable tiers are created (empty) the first time anything asks for them. A file in a
tier is named `<lang>_base.dict` and its sidecar `<lang>.meta.json`.

**Resolution for a language**: the first of `imported/<lang>_base.dict`,
`downloaded/<lang>_base.dict` that exists as a regular file with length greater than 0;
otherwise the bundled asset if it exists; otherwise nothing. A language "has a dictionary"
when any of the three exists (the zero-length rule applies only to the writable tiers). The
language code is lowercased before building the name.

**Legacy folder**: releases before 2.0.6 kept downloads and imports together in
`files/dictionaries_serialized/custom/`. On the first dictionary load of a process, every
`*_base.dict` there is moved into `downloaded/` (a rename, or copy then delete when the rename
fails); a file already present in `downloaded/` wins and the legacy copy is deleted; the
legacy folder is deleted once empty. Files migrated this way have no sidecar, so they are
never offered as updatable (section 5.5).

**Staging**: a download is streamed to `cache/dictionary_downloads/<filename>.tmp`; an import
is copied to `cache/dictionary_import/<lang>_base.dict`. Installation copies the staged file
to `<tier>/<lang>_base.dict.part`, deletes any existing `<lang>_base.dict`, renames `.part`
into place, then writes the sidecar with `installedAt` set to now. A process killed mid-copy
leaves a `.part` file and never a truncated dictionary; a failed install deletes the `.part`.
The staged file is deleted after installation whether or not it succeeded. Nothing sweeps
stale `.part` or `.tmp` files.

Other per-language files:

- `files/user_defaults.json`: the editable copy of the default user words (section 7).
- `files/locale_layout_mapping.json`: the user's layout overrides (section 10).

Dictionaries are not part of the settings backup; `user_defaults.json` and
`locale_layout_mapping.json` are.

## 4. Loading a dictionary

### 4.1 When

The keyboard keeps one in-memory dictionary per language code per keyboard process (the
region is ignored: `en_US` and `en_GB` share one). A dictionary is created for:

- the primary language when the keyboard service starts (preloaded in the background at once),
- the primary language whenever the active subtype changes to a different language,
- every extra suggestion language of the active input style, the first time a suggestion is
  computed while it is configured.

Once loaded, a dictionary stays loaded until the keyboard process dies. Switching to another
language and back does not reload. Installing, importing or uninstalling a dictionary does
not touch an already loaded one: the new file is used the next time the process starts.

Loads run off the main thread, one at a time across all languages (a second load waits). A
load that is cancelled by a rapid language switch restarts cleanly next time. A load that
runs out of memory drops everything it built, marks the dictionary as not loaded and leaves
it that way; the keyboard keeps running without suggestions for that language.

### 4.2 What happens

1. The legacy folder migration (section 3) runs.
2. The file for the language is resolved (section 3). If nothing resolves, the load fails
   silently: no suggestions, no autocorrect, no add-word candidate, and no known-word checks
   for that language, with no message to the user. The load may be attempted again on the
   next trigger.
3. The whole file is read into memory and decoded by first byte (section 2.2). A decode error
   fails the load the same silent way.
4. Both indexes are copied in; every bucket is re-sorted by effective frequency
   (`autocorrect-suggestions.md` section 3.2).
5. Compatibility aliases (section 2.3) are added and, when any were, the prefix buckets are
   rebuilt from the normalized index.
6. If the file has `symDeletes` and `symMeta`: a fuzzy index with the file's edit distance and
   prefix length is loaded from the delete map. Each value is kept if it is a normalized key
   of this file; otherwise it is treated as a prefix and expanded to every key starting with
   it; delete buckets that end up empty are dropped. Alias keys are added afterwards with the
   frequency of the bucket they alias. Otherwise the fuzzy index is built at the end of the
   load from every key with distance 2 and prefix length 4.
7. The default user words are merged (section 7), then the personal words, both keeping the
   existing main entries.
8. The dictionary is marked ready and, if a word is being typed, suggestions are recomputed.

Measured on the upstream files: 130-250 ms for a 50,000-word JSON `.dict`; CBOR is the faster
of the two. The English CBOR file is 21.5 MB and is read whole, so the keyboard process holds
the raw bytes and the decoded indexes at the same moment during the load.

## 5. Hosting, discovery, download, verification, install, update, removal

### 5.1 Hosting and the manifest

Manifest URL: `https://brobata.github.io/physiboard-dict/dicts-manifest.json`.
File URL pattern: `https://github.com/brobata/physiboard-dict/releases/download/<releaseTag>/<lang>_base.dict`,
currently with `releaseTag` `1`. The app never composes a URL: it downloads exactly the `url`
each manifest item carries.

Manifest contract:

| Field | Type | Content |
|---|---|---|
| `schemaVersion` | integer | `1` |
| `generatedAt` | string | ISO-8601 timestamp of the manifest build, e.g. `2026-08-31T22:32:15.661255Z` |
| `releaseTag` | string | `"1"` |
| `items` | array | one object per dictionary |

Item contract:

| Field | Type | Content |
|---|---|---|
| `id` | string | `<lang>_base`, used to key download state on the screen |
| `filename` | string | `<lang>_base.dict`; the language code is what remains after removing `_base.dict` (or, failing that, `.dict`) |
| `url` | string | direct download URL |
| `bytes` | integer | file size, shown on the screen and recorded in the sidecar |
| `sha256` | string | lowercase hex SHA-256 of the file |
| `updatedAt` | string | ISO-8601 timestamp of the file's last change, compared as a string |
| `name` | string | display name, e.g. `Italian (Basic)` |
| `shortDescription` | string | e.g. `Common words • lightweight`, may be empty; not shown |
| `languageTag` | string | e.g. `it-IT`; not used by the app |

All fields are required; unknown fields are ignored; a missing field is a parse error.

### 5.2 Fetching the manifest

The manifest is fetched when the installed-dictionaries screen opens and again on each tap of
its refresh button. One HTTP GET with header `Accept: application/json`, using the HTTP
client's defaults: 10 s to connect, 10 s per read, 10 s per write, no overall deadline, and
one transparent retry only on a connection-level failure; the app itself never retries.
Outcomes:

| Result | Behavior |
|---|---|
| 2xx with a non-blank body that parses | The list is merged with the local dictionaries (section 6) |
| Non-2xx status | Treated as an error `HTTP <code>` |
| 2xx with an empty or blank body | Error `Empty response` |
| Body that does not parse | Error |
| Any network exception | Error |

While the fetch runs the refresh button is replaced by a 24 dp spinner. On error the screen
still lists local dictionaries; the message "Failed to load dictionary list" is shown only when
there is nothing local either. There is no automatic re-fetch and no background check.

### 5.3 Download

Tapping the download icon on a row that is online and not installed starts a download for
that item; a second tap while it runs is ignored. Different items can download concurrently.

1. HTTP GET of the item's `url`, same client defaults as above, no size limit and no
   application deadline (a 33 MB Vietnamese file on a slow link simply takes its time).
2. The body is streamed in 8,192-byte chunks to `cache/dictionary_downloads/<filename>.tmp`.
   After every chunk the row's progress is updated with (bytes so far, declared content
   length); when the length is unknown it is -1 and the bar shows 0.
3. SHA-256 of the staged file is computed and compared case-insensitively with the item's
   `sha256`. Mismatch: the staged file is deleted, result "Download verification failed".
4. The staged file is fully decoded as a dictionary (section 2.2). Failure: result "Invalid
   dictionary format" (the staged file is left in the cache).
5. Installed into the **downloaded** tier (section 3) with sidecar
   `{origin: "download", sha256, bytes, manifestUpdatedAt: item.updatedAt}`. Failure: "Download
   failed".
6. Success: "Downloaded <name>", and the local list is re-read.

A non-2xx response or a missing body is "Network error"; any other exception during the
transfer is "Download failed". Every outcome is a snackbar. The keyboard does not load the new
file until its process restarts (section 4.1).

### 5.4 Verification summary

A file is accepted from the network only if its SHA-256 equals the manifest's and it decodes
as a dictionary. An imported file is accepted if it decodes; there is no checksum to compare.
Nothing verifies the manifest itself beyond HTTPS.

### 5.5 Update detection

A downloaded dictionary is "updatable" when the manifest's `updatedAt` for that language,
compared as a plain string, is greater than the sidecar's `manifestUpdatedAt`. An imported
dictionary is never updatable (it is the user's, not the project's). A downloaded file without
a sidecar (pre-2.0.6 migration) is treated as unknown, not stale, so an upgrade never
re-downloads every language on mobile data.

The installed-dictionaries screen does not use this rule: no row ever shows an update badge or
button, and a language that is installed never shows the download button. The only way to
take a newer hosted build today is Uninstall then Download. The rule exists and is tested, but
the screen was never wired to it.

### 5.6 Import

The plus icon opens the system file picker for any file type. After a pick:

1. The picked file's display name must end with `.dict` (case-insensitive) and contain
   `_base` (case-insensitive); otherwise "Invalid file name. Expected *_base.dict". The
   language code is the lowercased name with `_base.dict` removed; a name like
   `EN_BASE.dict` imports as `en`. A name that lowercases to something not ending in
   `_base.dict` (for example `my_base_words.dict`) is also rejected.
2. The file is decoded as a dictionary. Failure: "Invalid dictionary format".
3. The file is opened a second time and copied to `cache/dictionary_import/<lang>_base.dict`
   (the first stream was consumed by validation). A file that cannot be opened: "Import
   failed".
4. Installed into the **imported** tier with sidecar `{origin: "import", bytes}`.
5. "Imported <lang>_base.dict" and the local list is re-read.

An import for a language that also has a download does not touch the download; the import
simply wins at resolution. An import for a bundled language shadows the asset.

### 5.7 Uninstall

The delete icon appears only on installed rows that are not bundled. It opens a confirmation
("Uninstall dictionary" / "Are you sure you want to uninstall <name>? This action cannot be
undone." with Uninstall in the error color and Cancel). Confirming removes the file and the
sidecar from whichever writable tier holds it, checking the imported tier first. When an
import is removed and a download of the same language exists underneath, the download becomes
active. Outcomes: "Uninstalled <name>", "Dictionary file not found" (no tier holds it),
"Failed to uninstall dictionary" (delete failed), "Cannot uninstall built-in dictionaries".

## 6. The installed-dictionaries screen

Reached from Input Languages (section 8.2) by the book icon in its top bar. Title "Installed
dictionaries". Top-bar actions: refresh (section 5.2) and import (section 5.6).

**Rows.** The local list (bundled files plus every language with a file in either writable
tier) is merged with the manifest by lowercase file name. Each row shows:

- the display name: for a local dictionary, the language's own name in its own language with
  the first letter capitalized (`Italiano`, `Русский`), computed from the language code; for
  an online-only dictionary, the manifest `name`;
- "Language: XX" with the code uppercased;
- "Size: N MB" (one decimal; KB or B below a megabyte) when the manifest knows the file, even
  if it is installed;
- badges: "Installed" (any local file), "Imported" (any file in a writable tier, downloaded
  files included), "Available online" (in the manifest and not installed);
- on the right: a 20 dp spinner while downloading, else the delete icon (installed, not
  bundled) and the download icon (online, not installed);
- below, a linear progress bar while downloading with progress = downloaded / total.

Rows are sorted by display name, case-insensitive. Empty state: "No serialized dictionaries
found." (cannot happen with twelve bundled files unless asset listing fails).

**Dedup rule.** The local list is built as bundled files first, then writable-tier files, and
deduplicated by file name keeping the first. A downloaded or imported `en_base.dict` is
therefore reported as the bundled row: badge "Installed" only, no "Imported" badge, no delete
icon. Such a file cannot be uninstalled from the app; only clearing app data removes it.

The screen never tells the keyboard anything; it edits files, and the keyboard notices at its
next process start.

## 7. Default user words and the personal dictionary

Storage, precedence and the screens are specified in `autocorrect-suggestions.md` section 6.
What this document adds:

- Both lists are merged into every loaded dictionary, primary and extra alike, at the end of
  each load and again whenever the broadcast
  `brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED` arrives; a word added from the strip is
  merged into the primary dictionary at once, including its fuzzy index.
- Within a normalized bucket, main, default-user and personal entries coexist; a personal word
  can never be filtered out by frequency or capitalization rules and is a known word in every
  language.
- A word is **known** when it is in the primary dictionary or in any ready extra dictionary of
  the active input style. If an extra dictionary is configured but not yet loaded, the answer
  is "known" (true) until it loads, so a text replacement is skipped rather than wrongly
  applied at that boundary; the load is scheduled by the question.
- Default user words come from the asset `common/dictionaries/user_defaults.json`
  (`[{"w":"PhysiBoard","f":30},{"w":"BlackBerry","f":25},{"w":"Parenzo","f":20}]`), copied
  to `files/user_defaults.json` on the first load that finds it absent, and read from the copy
  from then on. If neither is readable the list is empty. A default word's `f` defaults to 1
  when missing.

## 8. Keyboard languages: subtypes and input styles

### 8.1 Base subtypes

The keyboard's manifest declares twelve subtypes, all mode `keyboard`, all with extra value
`noSuggestions=true` (a leftover the app never reads), in this order with these labels:

`en_US` English, `it_IT` Italiano, `fr_FR` Français, `de_DE` Deutsch, `pl_PL` Polski,
`da_DK` Dansk, `no_NO` Norsk, `es_ES` Español, `pt_PT` Português, `ru_RU` Русский,
`sr_RS` Српски, `uk_UA` Українська.

Android enables the base subtypes whose locale matches a system language implicitly, and
lets the user enable any of them in system settings. The keyboard declares
`supportsSwitchingToNextInputMethod` and points its settings entry at the app's settings
screen.

### 8.2 Input styles (`custom_input_styles`)

Screen "Input Languages", reached from the settings home ("Input languages") and from Extras.
It lists, in order: a "Layout mode" card (section 10), a "Layout switch shortcuts" card
(section 9.1), then one row per input style. Its top bar has the installed-dictionaries book
icon and a plus.

**Rows** come from two sources, deduplicated by `locale:layout`:

1. every system language (each entry of the device's locale list formatted as `xx_YY`, or
   `xx` when it has no region), paired with its mapped layout (section 10), marked "System",
   not deletable, with a show/hide eye toggle; and
2. every entry of the preference `custom_input_styles`.

A row shows "<Language name> - <layout>", a second line "<locale> - <layout>", and the badge.
Tapping a row edits it; the delete icon (custom rows only) asks "Delete Input Style" / "Are
you sure you want to delete this input style?".

**Preference format.** `custom_input_styles` is one string of entries separated by `;`, each
`locale:layout` or `locale:layout:extra`. Whitespace around parts is trimmed; empty entries
are skipped. Default: the `predefined_subtypes` string array, which ships empty, so the
effective default is `""`. Duplicates are refused with "This language and layout combination
already exists" (matching against system rows too, by prefix of `locale:layout`).

**Add / edit dialog.** Title "Add Input Style", "Edit Input Style" or, for a system row,
"Edit System Locale Layout". Contents:

- Language: a dropdown of every language code that has a dictionary in any tier (bare codes
  such as `de`, sorted), plus "Add Custom Locale…", which asks for a code matching
  `^[a-zA-Z]{2,3}([_-][a-zA-Z]{2,3})?$` ("Invalid locale code format. Use format: xx_XX or
  xx"; "Locale code cannot be empty"). A system row's language cannot be changed ("System
  locale - cannot be changed"). When the chosen language has no dictionary the dialog warns
  "No dictionary available for this locale. Suggestions and auto-correction will be disabled."
- Layout: preselected from the mapping for the chosen locale (section 10) or the row's
  current layout; "Tap to change layout" opens the layout picker, which returns to the dialog
  with the selection kept.
- Suggestion dictionaries: "Primary: <language name>", then one switch per other language
  that has a dictionary in any tier (bare codes, sorted, the primary language excluded). "No
  other installed dictionaries available." when none. This sets the input style's extra
  suggestion languages (section 8.4).
- Save / Cancel.

**Saving a custom row** appends `locale:layout` to the preference (or rewrites the edited
entry in place), stores the suggestion languages under the new key, removes them under the
old key when the key changed, and shows "Input style added: <Language> - <layout>" or "Input
style updated: ...". **Saving a system row** does not touch `custom_input_styles`: it writes
the layout into `files/locale_layout_mapping.json` (section 10) and stores the suggestion
languages; "Layout mapping updated: <Language> - <layout>", or "save failed" when either
write fails. Deleting a custom row removes its entry and its suggestion languages, then
re-registers subtypes at once; "Input style deleted".

**Hiding a system row** adds `"<locale-with-hyphens>:<layout>"` to the JSON array in
`hidden_system_input_styles`; showing removes it. The last visible row cannot be hidden
("Keep at least one input style visible"); after a delete that would leave nothing visible,
the first hidden system row is shown again. A hidden system style is skipped by language
cycling (section 9.3) and left out of the explicitly enabled set (section 8.3).

### 8.3 Registration with Android

The input styles are turned into additional subtypes and handed to Android:

- when the app process starts (posted to the main thread),
- when the keyboard service is created,
- whenever `custom_input_styles` changes,
- immediately after a delete, hide or show on the Input Languages screen,
- 500 ms after the device's language list changes (section 8.5).

Android forgets additional subtypes on reinstall, which is why the process start re-registers
them; an older empty "sync" at startup that erased them has been removed (commit "restore
dynamic subtypes after updates").

For each `custom_input_styles` entry, in order:

1. Skip it when the locale does not parse to a language, or the layout is not among the
   available layouts (bundled `common/layouts/*.json` plus the user's custom layouts).
2. Skip it when the locale is one of the twelve base locales and the layout equals the
   mapped layout for that locale (it would duplicate the base subtype). `en_US:qwerty` is
   skipped; `en_US:vietnamese_telex_qwerty` is kept.
3. Build a subtype: locale string as written, language tag from it, mode `keyboard`, extra
   value `KeyboardLayoutSet=<layout>,AsciiCapable,EmojiCapable,isAdditionalSubtype`
   followed by `,<extra>` when the entry had a third part, a stable id derived from the text
   `pastiera-subtype-v2|<locale>|<layout>` (never 0), not auxiliary, not overriding the
   implicit subtype. On Android 14 and later the display name is set to
   "<Language> · <Layout name>" (the language label from the app's own strings for
   `en it fr de pl es pt ru`, otherwise the system's name for the language in the current UI
   language; the layout name from the layout file's metadata, the part before " | ", or the
   layout id); on older Android the string resource for those eight languages, or Android's
   own name.

The whole array replaces Android's previous additional set, even when it is empty. Then,
500 ms later, on Android 14 and later, the explicitly enabled set is rewritten as: every
currently enabled subtype that is either additional, or a base subtype whose locale or
language is still a system language and whose `locale:layout` is not hidden; plus every
additional subtype just registered. When the array was empty, only the surviving base
subtypes are enabled. Below Android 14 the enabled set is left to the user.

### 8.4 Extra suggestion languages (`input_style_suggestion_locales`)

A JSON object whose keys are `"<locale-with-hyphens>:<layout>"` and whose values are arrays
of language tags (hyphens, trimmed, deduplicated). Lookup for the active subtype's locale
`L` (hyphenated, language `l`) and the current layout `Y`: `L:Y`, then `l:Y`, then for
`Y` = `qwertz` the legacy aliases `L:german_multitap_qwertz` and `l:german_multitap_qwertz`.
Saving an empty list removes the key. The tag `x-pastiera` is dropped before use. The
primary language is excluded even if listed. The current layout used for the lookup is the
`keyboard_layout` preference, not the subtype's own layout.

Each extra language gets its own dictionary (section 4) and its own suggestions, merged with
the primary's: primary results carry a 0.35 score bonus, ties break on shorter candidate,
duplicates (case-insensitive) collapse to the first, and the list is cut to the strip's
maximum. Suggestions from an extra language that has not finished loading are simply absent
from that computation; the load is scheduled and the strip refreshes when it completes.
Changing the active style's extra languages takes effect at the next suggestion computation:
engines for languages no longer listed are dropped, new ones are created.

### 8.5 System languages without a dictionary

When the keyboard registers subtypes it first scans the device's language list. Every system
locale that has no dictionary (checked against the bundled and installed language codes
expanded to common variants: `en` covers `en_US en_GB en_AU en_CA en`, `no` covers
`no_NO nb_NO nn_NO nb nn no`, and so on for `it fr de pl da es pt ru`), is not a base
locale, and is not already in `custom_input_styles`, is appended to `custom_input_styles` as
`<locale>:<mapped layout>` and remembered in the string set `auto_added_system_locales`. When
the device's language list changes (compared as the full tag list), after 500 ms the
remembered locales that are no longer system languages are removed from
`custom_input_styles` and from the set, new ones are added, and registration runs. This makes
a new system language typeable at once even without a dictionary. Locales the user added
themselves are never removed by this sweep.

### 8.6 The legacy "Languages" screen

A second, older mechanism survives in the build: an activity titled "Languages" ("Add
languages to the Android input method selector. Only languages with dictionaries that are not
system languages are shown.") with one switch per bundled or installed language code that is
not a system language. Each toggle writes the string set `additional_ime_subtypes` and sends
the package-internal broadcast `brobata.physiboard.ACTION_ADDITIONAL_SUBTYPES_UPDATED`; the
keyboard service answers by replacing Android's additional subtypes with one plain subtype
per code (locale from a fixed table: `ru_RU pt_PT de_DE da_DK no_NO nb_NO nn_NO fr_FR es_ES
pl_PL it_IT en_US`, else the code itself; extra value `noSuggestions=true`; no layout). No
screen, menu or search entry opens this activity; it is unreachable in 2.x. If it were
reached, its registration and the input-style registration would overwrite each other, since
both replace the same Android list. `additional_ime_subtypes` is still in the settings
backup.

### 8.7 The primary language

The primary language is the language part of the subtype Android reports as current for the
keyboard. When Android reports no subtype (possible right after install before any is
enabled) the keyboard assumes `it-IT`: Italian suggestions, Italian substitutions, Italian
speech. The suggestion language changes only through Android's subtype-changed callback
(section 9.4); the keyboard does not re-read the subtype when a field is entered.

## 9. Switching language

### 9.1 Triggers

| Trigger | Condition | Setting | Default |
|---|---|---|---|
| Ctrl+Space (keycode 62 with Ctrl) | an editable field is focused; Ctrl physically held, latched, one-shot, or reported by the event | `ctrl_space_layout_switch` | true |
| Alt+Shift, either order, on the first event (no repeat) | editable field; the other modifier physically held or reported | `alt_shift_layout_switch` | false on a fresh install; true when the upgrade that introduced the switch found existing preferences (recorded once in `alt_shift_default_initialized`) |
| Alt+Enter (keycode 66 with Alt), first event only | editable field | `alt_enter_layout_switch` | false |
| Status bar language button, tap | the button is placed in a slot (`status-bar.md`) | none | |
| Hamburger menu, "Language" entry | | none | |

All three key combinations consume the key event. Alt+Shift clears both Alt and Shift state;
Alt+Enter clears Alt and swallows Enter repeats until the key is released. Ctrl+Space clears
Alt if it was active and resets Ctrl, except that a Ctrl latched by tap stays locked when both
`ctrl_tap_latches` and `ctrl_latch_stays_on_space` are on; a Ctrl latch that came from nav
mode is cancelled together with its notification. The exact modifier rules are in
`keys-and-modifiers.md`.

The status bar button: a haptic tap, then the button is disabled at 50 percent opacity for
300 ms and its text is refreshed when re-enabled; a tap within 500 ms of the previous one is
ignored. Long-press opens the app's settings. The keyboard also refuses a switch while one is
in flight and for 300 ms after a successful one from the button or menu. A button or menu
tap while a Shift or Alt layer is latched releases that latch first.

### 9.2 What the button shows

The language button is a 14 sp white text with the current subtype's language code uppercased
(`EN`, `DE`, `??` when Android reports no subtype), drawn with a dashed underline (dash and
gap each the larger of 2 dp and one fifth of the text width, three dashes across) as the
long-press hint. Accessibility: content "Switch language", state "Language <subtype display
name>, layout <layout name>". The text is refreshed on every status-bar render, after a
switch, and when the strip is rebuilt.

### 9.3 Cycling order

1. Take Android's list of enabled subtypes for the keyboard, implicit ones included. This is
   in Android's order: base subtypes in manifest order, then additional subtypes.
2. Resolve each subtype's layout: the `KeyboardLayoutSet` from its extra value, else the
   mapped layout for its locale (`en_US` when the locale is blank).
3. Drop base subtypes whose `locale:layout` is hidden (section 8.2), and drop any subtype
   whose `locale:layout` has already been seen.
4. Find the current subtype by equal locale string and equal extra value; move to the next
   entry, wrapping to the first; when the current one is not in the list, go to the first.
5. Ask Android to switch to it. This needs the keyboard window's token; without it (window
   never shown) nothing happens and the trigger reports failure.
6. On success: `keyboard_layout` is set to the resolved layout, and if
   `toast_on_layout_switch` is true a short toast shows
   `<Layout name> | <PRIMARY> | <EXTRA1>, <EXTRA2>` (layout metadata name before " | ", or
   the id; language codes uppercased; the extra part only when the style has extra
   suggestion languages other than the primary). A previous toast is cancelled first. If the
   toast cannot be built the locale string is shown instead.

With one enabled subtype the cycle returns to the same subtype; Android still reports a
change when the extra value differs, otherwise nothing visible happens beyond the toast.

### 9.4 What the keyboard does after a switch

Android calls the keyboard with the new subtype. In order:

1. The suggestion language becomes the subtype's language (region ignored). If it differs
   from the previous one: any in-flight load is cancelled, the current word tracker and the
   strip are cleared, "sentence start" is assumed, the dictionary for the new language is
   taken from the per-process cache or created and loaded in the background, and when ready
   the word at the cursor is re-read and suggestions recomputed (immediately when the field is
   not restricted). Same language: nothing is reloaded, but extra-language engines are
   dropped and rebuilt lazily.
2. The layout becomes the subtype's own layout, or, in automatic layout mode, the mapped
   layout for its locale; in manual mode, the `keyboard_layout` preference (which the cycler
   has just set). The layout switch happens without a toast of its own.
3. The strip re-renders and the language button text updates.

The same callback runs when the user picks a subtype from Android's keyboard picker.

## 10. Locale to layout mapping

The asset `common/locale_layout_mapping.json` maps locale strings and bare language codes to
layout ids:

| Key | Layout |
|---|---|
| `tr`, `tr_TR`, `tr_CY` | `turkish_multitap` |
| `en_US`, `it_IT`, `pl_PL`, `es_ES`, `pt_PT`, `da`, `da_DK` | `qwerty` |
| `fr_FR` | `azerty` |
| `de`, `de_DE`, `de_AT`, `de_CH`, `de_LU` | `qwertz` |
| `no`, `no_NO`, `nb`, `nb_NO`, `nn`, `nn_NO` | `norwegian_multitap_qwerty` |
| `vi_VN` | `vietnamese_telex_qwerty` |
| `ru_RU` | `russian_translit` |
| `sr_RS` | `serbian_cyrillic` |
| `uk_UA` | `ukrainian` |

Bundled layouts (`common/layouts/`): `arabic armenian_phonetic azerty bulgarian_phonetic
bulgarian_phonetic_traditional Cyrillic_Translite german_multitap_qwertz greek
norwegian_multitap_qwerty qwerty qwertz russian_jcuken russian_standard russian_translit
serbian_cyrillic turkish_multitap ukrainian vietnamese_telex_qwerty`.

**Lookup** for a locale string `L` with language `l`: if `files/locale_layout_mapping.json`
exists and is readable, its `L` then its `l`, non-empty values only; then the asset's `L`,
then the asset's `l`, then `qwerty`. `de-AT` resolves to `qwertz` through `de`; `en_GB`
resolves to `qwerty` through the fallback, since neither `en_GB` nor `en` is mapped.

**Writing an override** (saving a system row's layout): the asset is read, every key of the
existing override file is merged over it, the new `L` is set, and the whole merged object is
written pretty-printed with 2-space indentation to `files/locale_layout_mapping.json`. If the
edited locale is the active subtype's and automatic mode is on, the keyboard is told to
re-resolve its layout at once through the timestamp `keyboard_layout_auto_mapping_updated`.

**Automatic vs manual** (`keyboard_layout_auto_by_locale`, default true, the "Layout mode"
card on Input Languages): automatic resolves the layout from the subtype or mapping on every
subtype change; manual keeps whatever `keyboard_layout` holds (default `qwerty`), which
language cycling still overwrites with the resolved layout of the subtype it lands on.

**One-time migration**: the first time the Input Languages screen opens, if the override file
maps any of `de de_DE de_AT de_CH de_LU` to `german_multitap_qwertz` it is rewritten to
`qwertz`; `legacy_german_qwertz_default_migrated` records that this ran (also set when there
is no override file).

## 11. The app's own interface language

`app_language_tag` (string, unset means "System default") is applied to every app screen by
wrapping each activity's resources in that locale; the keyboard service and its toasts are
not wrapped and follow the system language. Changing it recreates the current screen.

The selector ("App Language", "Choose the language used for app interface texts.", label
"Language") is a card reached from About and from Extras. Options in order: System default,
then `en it de es fr pl ru uk vi hy`. Each option is labelled with the language's name in
itself, and, when the name in the current UI language differs, "<native> - <ui name>"
(`Deutsch - German`). Translations exist for `de es fr hy it pl ru uk vi`; English is the
default resource set.

## 12. Speech recognition language

Dictation asks the recognizer for the current subtype's locale string with underscores
replaced by hyphens (`fr_FR` becomes `fr-FR`, `sr_Latn_RS` becomes `sr-Latn-RS`); when the
subtype's locale does not parse (`___`) or there is no subtype, the device locale's tag; when
that is empty, `it-IT`. The rest of dictation is in `dictation.md`.

## 13. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `custom_input_styles` | string, `locale:layout[:extra];...` | `""` (the `predefined_subtypes` array is empty) | The input styles registered as additional subtypes | Input Languages | rows, "Add Input Style" |
| `hidden_system_input_styles` | string, JSON array of `"locale:layout"` | unset (none hidden) | System input styles skipped by cycling and left disabled | Input Languages | eye icon "Hide" / "Show" |
| `input_style_suggestion_locales` | string, JSON object | unset | Extra suggestion languages per input style | Input Languages, add/edit dialog | "Suggestion dictionaries" |
| `auto_added_system_locales` | string set | empty | Which `custom_input_styles` entries the keyboard added for dictionary-less system languages, so it may remove them | none (internal) | |
| `keyboard_layout_auto_by_locale` | boolean | true | Whether the layout follows the subtype and mapping (true) or `keyboard_layout` (false) | Input Languages | "Layout mode" card |
| `keyboard_layout` | string | `qwerty` | The active layout id; rewritten by every language switch | Input Languages, layout picker | |
| `keyboard_layout_auto_mapping_updated` | long, wall-clock ms | unset | Signal that the mapping override changed and the layout must be re-resolved | none (internal) | |
| `legacy_german_qwertz_default_migrated` | boolean | false | Marks the German mapping migration as done | none (internal) | |
| `ctrl_space_layout_switch` | boolean | true | Ctrl+Space cycles languages | Input Languages, "Layout switch shortcuts" | "Ctrl+Space Layout Switch", "Cycle languages/layouts with Ctrl+Space." |
| `alt_shift_layout_switch` | boolean | false (true on upgraded installs, see 9.1) | Alt+Shift cycles languages | same | "Alt+Shift Layout Switch" |
| `alt_shift_default_initialized` | boolean | false | Marks the one-time Alt+Shift default decision | none (internal) | |
| `alt_enter_layout_switch` | boolean | false | Alt+Enter cycles languages | same | "Alt+Enter Layout Switch" |
| `toast_on_layout_switch` | boolean | true | Shows the layout / language toast after a switch | same | "Layout Switch Toast", "Show a toast when switching languages/layouts." |
| `app_language_tag` | string, BCP-47 | unset (system) | The app UI language | About > App Language, Extras | "App Language" |
| `additional_ime_subtypes` | string set of language codes | empty | Legacy additional subtypes (section 8.6); unreachable | "Languages" (unreachable) | switches per language |
| `user_dictionary_entries` | string, JSON array | `[]` | Personal words (`autocorrect-suggestions.md`) | Auto-correction > Personal dictionary | |

In the settings backup: `custom_input_styles`, `hidden_system_input_styles`,
`input_style_suggestion_locales`, `keyboard_layout`, the three shortcut switches,
`toast_on_layout_switch`, `additional_ime_subtypes`, `user_dictionary_entries`, and the files
`user_defaults.json` and `locale_layout_mapping.json`. Not backed up: `app_language_tag`,
`keyboard_layout_auto_by_locale`, `auto_added_system_locales`, the migration flags, and every
dictionary file.

## 14. Device facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Titan 2 Elite's physical keyboard is a fixed QWERTY key matrix; `azerty`, `qwertz`, `russian_translit` and the other layout ids in this document are software remaps of those keys, chosen per language by the mapping in section 10. | The device key layout and key character map recorded in the Titan docs folder (`TitanKey.kl`, `TitanKey.kcm`), referenced from `DEVICE.md` |
| D2 | Fn on the Titan 2 arrives as Ctrl with scancode 251 and never sends key-up, and holding Fn starts dictation; whether Fn+Space therefore reaches the Ctrl+Space language switch, or is consumed by the Fn handling first, is not determinable from the source and needs device evidence. | The Fn facts in `keys-and-modifiers.md`; the changelog entry "hold-Fn dictation" |
| D3 | The keyboard process reads a 21.5 MB English file whole and decodes it in memory; the Titan 2 Elite's memory headroom for the keyboard process is not recorded anywhere, and the out-of-memory fallback in section 4.1 exists because a load has failed that way. | The out-of-memory handling in the loader and the comment "leave it unloaded rather than letting the error take the IME process down" |

## 15. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| User downloads English from the list | The hosted 50,000-word upstream build (13.2 MB) is installed in the downloaded tier and outranks the bundled 80,000-word rebuild; real words the rebuild added become typos again after the next process restart | The hosting repository was never updated with the rebuilt list; resolution prefers any installed file over the asset |
| A downloaded or imported file exists for a bundled language | The screen shows it as the bundled row: "Installed" badge only, no delete icon | The local list keeps the bundled entry when deduplicating by file name |
| A downloaded file is shown | It carries the "Imported" badge | Both writable tiers are reported under one label |
| A newer build appears in the manifest for an installed language | Nothing changes; no update indicator | The update rule is implemented and tested but no screen calls it |
| Dictionary installed, imported or removed while the keyboard runs | No effect on suggestions until the keyboard process restarts | Loaded dictionaries are cached per process and never invalidated |
| Norwegian or Ukrainian words | Almost every word has the maximum effective frequency | Raw corpus counts far above 255 are clamped to 255 |
| `uk_base.dict` | Loads through the JSON path and builds its fuzzy index at load time | It was never converted to CBOR and has no delete map |
| A `.dict` written with `source` 1 | Its words rank as default user words and are excluded from starter suggestions | The loader maps the integer by position, not by the documented meaning |
| Serbian subtype active | Typing works, no suggestions, no autocorrect, no add-word candidate, no message | No `sr_base.dict` exists anywhere |
| Android reports no current subtype | Italian dictionary, Italian substitutions, `it-IT` speech, `??` on the language button | The upstream default locale was Italian and survived the fork |
| Manifest fetch fails but local dictionaries exist | The list shows only local rows; no error text | The error is shown only for an empty list |
| Download of a 33 MB file on a slow link | No overall deadline; it fails only when a single read stalls for 10 s | Client defaults, no application timeout |
| Two taps on Download for the same row | The second is ignored | Download state is keyed by item id |
| Import of a file whose name lowercases to something else than `<lang>_base.dict` | "Invalid file name" | The name check is on the display name, the code extraction on its lowercase form |
| Import of a valid file with a language code that no subtype uses | Installed; appears in the language dropdown of Input Languages and can be given an input style | Availability is by file, not by subtype |
| System language with no dictionary, for example Armenian | Added to `custom_input_styles` automatically with the mapped layout (usually `qwerty`) and registered; removed again if the system language goes away | Section 8.5 |
| Custom input style for a base locale with its mapped layout (`en_US:qwerty`) | Silently not registered | It would duplicate the base subtype |
| `hidden_system_input_styles` hides every visible style | Refused for the last one; after a delete, the first hidden system style reappears | Cycling needs at least one target |
| Language cycle with no keyboard window token | Nothing happens; the button still re-enables after 300 ms | Android's subtype switch requires the token |
| Ctrl+Space in a field with no editor | Passed through; no switch | The combination needs an editable field |
| Alt+Shift on a fresh install | Off | The default flipped to off; upgrades kept on |
| The legacy "Languages" activity | Unreachable but still declared, still backed up | Superseded by input styles; never removed |
| Extra suggestion language still loading at a boundary | Every word is treated as known; text replacement skipped at that boundary | Wrong replacement judged worse than a skipped one |
| `input_style_suggestion_locales` keyed by the subtype layout, looked up with `keyboard_layout` | In manual layout mode with a layout that differs from the style's, the extra languages are not found | The lookup uses the preference, not the subtype |
| `x-pastiera` in an extra-language list | Ignored | It names the substitution rule set, not a dictionary |
| App language set to `hy` | UI in Armenian; keyboard toasts still in the system language | Only activities are wrapped |
| Alias keys from step 3 of normalization | `œil` reachable by typing `oeil` even in older files | Aliases added at load |
| Zero-length file in a writable tier | Ignored for resolution, but counted as installed by the screen | Resolution checks length; listing checks existence |

## 16. Test cases

Each case is encodable as a JVM test against the file, manifest and preference contracts.

| # | Input | Expected outcome |
|---|---|---|
| T1 | Empty tiers; resolve `de` | Nothing resolves (asset lookup is separate) |
| T2 | Install `de` as download; resolve | The downloaded file; origin `download` |
| T3 | Install `de` as download, then as import; resolve | The imported file; origin `import`; the download still exists untouched |
| T4 | T3 then remove the import | The download resolves again |
| T5 | Install `de` with `manifestUpdatedAt` `2026-07-05T13:52:33Z`; ask with manifest `2026-07-05T13:52:33Z` | Not an update |
| T6 | As T5, ask with `2026-07-05T13:52:34Z` | An update |
| T7 | Install `de` as import; ask with any manifest date | Never an update |
| T8 | Downloaded file without sidecar; ask with any date | Not an update |
| T9 | `custom/fr_base.dict` exists, `downloaded/` empty; run the migration | The file is in `downloaded/`, `custom/` is gone, origin `download` |
| T10 | `custom/fr_base.dict` and `downloaded/fr_base.dict` both exist; migrate | The downloaded copy is kept, the legacy copy deleted |
| T11 | `imported/de_base.dict`, `downloaded/fr_base.dict`, `downloaded/de_base.dict`, `imported/gd_base.dict` | Installed languages are exactly `{de, fr, gd}` |
| T12 | Install as download with manifest date; read the sidecar | `origin` `download`, `manifestUpdatedAt` equals the manifest date, `installedAt` greater than 0 |
| T13 | File name for `DE` | `de_base.dict`; language of `de_base.dict` is `de`; language of `notadictionary.txt` is none |
| T14 | Language of manifest filename `en_base.dict` | `en`; of `en.dict` is `en` |
| T15 | Bytes starting with `{"normalizedIndex":...}` versus bytes starting with `0xA4` | JSON path versus CBOR path; both must yield the same indexes for the same content |
| T16 | A `.dict` whose `symDeletes` values are 4-character prefixes (`a`, `ab`) | The fuzzy index expands each prefix to every key starting with it |
| T17 | A `.dict` with `symDeletes` but no `symMeta` | The fuzzy index is built from keys at load time |
| T18 | A key `œil` in a file built without ligature folding | After load, `oeil` also resolves to the same entries and the prefix `oe` contains them |
| T19 | Raw frequency 64,938 | Effective frequency equals that of raw 255 |
| T20 | Mapping lookup for `de`, `de_DE`, `de-AT`, no override file | `qwertz` for all three |
| T21 | Mapping lookup for `en_GB` | `qwerty` (fallback) |
| T22 | Override file `{"en_US":"azerty"}`; lookup `en_US` | `azerty`; the asset is not consulted |
| T23 | `custom_input_styles` `en_US:qwerty` | Zero additional subtypes |
| T24 | `custom_input_styles` `en_US:vietnamese_telex_qwerty` | One subtype with layout `vietnamese_telex_qwerty`, extra value containing `KeyboardLayoutSet=vietnamese_telex_qwerty`, `AsciiCapable`, `EmojiCapable`, `isAdditionalSubtype`; it matches (`en_US`, `vietnamese_telex_qwerty`) and not (`en_US`, `qwerty`) |
| T25 | `custom_input_styles` `xx_YY:qwerty;fr_FR:nosuchlayout;;de_DE:qwertz` | Exactly one subtype, `xx_YY` with `qwerty`: the unknown layout entry, the empty entry and the base-redundant `de_DE:qwertz` are all skipped |
| T26 | Same locale and layout, subtype id computed twice | Identical, non-zero ids |
| T27 | `input_style_suggestion_locales` `{"de:qwertz":["fr-FR","en_US","fr-FR"]}`; lookup (`de_DE`, `qwertz`) | `[fr-FR, en-US]` via the language-only key, deduplicated |
| T28 | `{"de-DE:german_multitap_qwertz":["fr"]}`; lookup (`de_DE`, `qwertz`) | `[fr]` via the legacy alias |
| T29 | Set locales for (`it_IT`, `qwerty`) to `[]` | The key `it-IT:qwerty` is removed from the object |
| T30 | Enabled subtypes `[en_US, it_IT (hidden), fr_FR]`, current `en_US` | Next is `fr_FR`; from `fr_FR` next is `en_US` |
| T31 | Enabled subtypes `[en_US]`, current unknown | Next is `en_US` |
| T32 | Speech tag for subtype `fr_FR` with device `en_US` | `fr-FR`; for no subtype and device `en_GB`: `en-GB`; for `___` and no device locale: `it-IT`; for `sr_Latn_RS`: `sr-Latn-RS` |
| T33 | Locale code validation | `en`, `en_US`, `en-US`, `ast` accepted; `e`, `en_USA_x`, `en US`, empty rejected |
| T34 | Merge primary `[casa 1.0]` and extra `[casa 1.2, maison 1.3]` with limit 3 | Order `casa` (primary, 1.0 + 0.35 = 1.35), `maison` (1.3); the extra `casa` is dropped as a duplicate |
| T35 | Manifest JSON with an extra top-level field and an item missing `sha256` | Parse error (missing required field); with the extra field only, parses |

## 17. Keep / Drop for 3.0

| Item | Decision | Reason |
|---|---|---|
| The `<lang>_base.dict` format (CBOR map with four fields, no header) | Drop, replace | Inherited from Pastiera; 3.0 defines a headered format with language, version and checksum, served under a new path; the old path stays for 2.x |
| The build-input `{"w","f"}` JSON lists | Keep as the builder's intermediate | The English pipeline already produces it; extend to all 19 from Leipzig |
| Two accepted encodings by first byte | Drop | One format, one decoder |
| Runtime alias re-normalization | Drop | Build the keys right once; the builder is ours now |
| Frequency clamp to 255 | Drop, fix in the data | Norwegian and Ukrainian need rebuilt scales, not a clamp |
| Nineteen languages | Undecided per language | Keep the nine with an interface translation or a Titan owner asking; rebuild or drop the rest |
| Serbian base subtype without a dictionary | Drop, or add the dictionary | A language that can never suggest is a trap |
| Three-tier storage (imported, downloaded, bundled) with sidecars | Keep | Small, tested, and the import-never-overwritten rule is a user promise |
| Legacy `custom/` migration | Keep for one release, then drop | Only 2.0.5 and older installs have it |
| Hosting at `github.com/brobata/physiboard-dict` with a JSON manifest and SHA-256 | Keep | Self-owned, checksum-verified; add a new path for the 3.0 format |
| Manifest fetch with client defaults only | Keep, add an overall deadline | 10 s per read is fine; add a call deadline and a size cap per item |
| Update rule (`updatedAt` string compare) | Keep and wire it to the screen | Implemented, tested, never shown |
| Installed-dictionaries screen dedup that hides installed files behind bundled rows | Fix | A file the user installed must be visible and removable |
| "Imported" badge on downloads | Fix | Label the tier correctly |
| Import via file picker | Keep | The only route for a custom (Shavian, dialect) list |
| Per-process dictionary cache never invalidated | Fix | Reload on install, import, uninstall |
| Default user words file | Keep, fold into personal words | Per `autocorrect-suggestions.md` |
| Base subtypes declared in the manifest (twelve) | Keep, trim to shipped languages | Android needs them for the picker and implicit enabling |
| Input styles as additional subtypes with layout, stable id and named display | Keep | The only way to register "Deutsch · QWERTZ" and "English · Telex" side by side |
| Auto-add of dictionary-less system languages | Keep | Makes a new system language typeable at once |
| Hidden system input styles | Keep | Users hide the system's second language |
| Extra suggestion languages per input style | Keep | Bilingual typing is the norm on this device |
| Lookup of extra languages by `keyboard_layout` instead of the subtype's layout | Fix | Breaks in manual layout mode |
| Legacy "Languages" activity and `additional_ime_subtypes` | Drop | Unreachable, conflicts with input styles |
| Italian fallback when there is no subtype | Replace with English | Upstream leftover |
| Ctrl+Space cycling, default on | Keep | The Titan shortcut users know |
| Alt+Shift and Alt+Enter cycling | Keep, both off | Cheap; some users come from desktops |
| Status bar language button with dashed underline and long-press to settings | Keep | Works; see `status-bar.md` |
| Hamburger menu language entry | Undecided | Depends on whether the hamburger survives in `status-bar.md` |
| Cycle toast `Layout | XX | YY` | Keep | Only feedback when the strip is hidden |
| Locale to layout mapping asset plus user override file | Keep, shrink | Only layouts the Titan can express; drop `arabic`, `greek`, `armenian_phonetic`, the Bulgarian pair, `russian_jcuken`, `russian_standard`, `Cyrillic_Translite` unless a Titan user asks (`layers-sym-alt.md` decides layouts) |
| German `german_multitap_qwertz` migration | Drop | One-time, already run everywhere it matters |
| Automatic vs manual layout mode | Undecided | Manual mode is the source of the lookup bug above; keep only if a user needs a layout that no locale maps to |
| App UI language selector with ten options | Keep | Cheap, translations exist |
| Speech language from the subtype locale | Keep | Correct and tested |
| `noSuggestions=true` on every base subtype | Drop | Never read |

## 18. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/docs/spec/autocorrect-suggestions.md
- /home/disdiqqq/projects/pastiera/docs/plans/rebuild-from-scratch.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/DEVICE.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/TitanKey.kcm
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
- /home/disdiqqq/projects/pastiera/README.md
- /home/disdiqqq/projects/pastiera/app/build.gradle.kts
- /home/disdiqqq/projects/pastiera/gradle/libs.versions.toml
- /home/disdiqqq/projects/pastiera/app/src/main/AndroidManifest.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/xml/method.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/arrays.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/locale_layout_mapping.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries/user_defaults.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries/en_base.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries/it_base.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries/ (all `*_base.json`, counted and ranged)
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries_serialized/ (all `*_base.dict`, headers and three decoded)
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/layouts/ (listing)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/dictionaries/DictionaryRepositoryManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/dictionaries/InstalledDictionariesActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryIndex.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryEntry.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/WordNormalization.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/SuggestionController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/LanguagesScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/LanguagesActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AppLanguageSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AppLocaleManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/LocalizedComponentActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/CustomInputStylesScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AboutScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsBaseline.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtils.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/SubtypeCycler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/SpeechRecognitionManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/LanguageButtonFactory.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonConfig.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonHost.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ui/HamburgerMenuView.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/LayoutMappingRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/JsonLayoutLoader.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/DictionaryStoreTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/DictionaryRepositoryTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtilsLayoutTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/SpeechRecognitionManagerLanguageTagTest.kt
- /home/disdiqqq/projects/pastiera/scripts/README.md
- /home/disdiqqq/projects/pastiera/scripts/build_symspell_dict.py
- /home/disdiqqq/projects/pastiera/scripts/convert_dict_to_cbor.py
- /home/disdiqqq/projects/pastiera/scripts/convert_all_to_symspell.py
- /home/disdiqqq/projects/pastiera/scripts/preprocess_dictionaries.py
- /home/disdiqqq/projects/pastiera/scripts/preprocess-dictionaries.main.kts
- /home/disdiqqq/projects/pastiera/scripts/truncate_dict.py
- /home/disdiqqq/projects/pastiera/scripts/backup_truncate_and_convert.py
- git history: docs/custom_dictionary_guide.md and docs/online-dictionaries.md (deleted; read at their last commits), commit messages 4962da9, d60ff5c, b5951a3
- https://brobata.github.io/physiboard-dict/dicts-manifest.json (fetched 2026-09-20)
