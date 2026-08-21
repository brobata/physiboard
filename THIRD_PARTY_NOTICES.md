# Third-party Notices

PhysiBoard (a GPLv3 fork of Pastiera) is licensed under the GNU General Public License
version 3. Some components are derived from or vendored from third-party open source
projects and retain their original attribution requirements.

## Pastiera (upstream project)

- Project: Pastiera, by Andrea Palumbo (PalSoftware) and contributors
- Source repository: https://github.com/palsoftware/pastiera
- License: GNU General Public License, version 3 (`LICENSE`)

PhysiBoard is a fork of Pastiera. The entire keyboard engine and the majority of this
codebase originate there; fork changes are documented in `PHYSIBOARD_CHANGES.md` per
GPLv3 §5(a).

## JetBrains Mono

- Project: JetBrains Mono
- Source repository: https://github.com/JetBrains/JetBrainsMono
- Copyright: 2020 The JetBrains Mono Project Authors
- License: SIL Open Font License, Version 1.1
- License text: `third_party/licenses/OFL-1.1.txt`
- Bundled files: `app/src/main/res/font/jetbrains_mono_regular.ttf`,
  `jetbrains_mono_medium.ttf`, `jetbrains_mono_bold.ttf`

The PhysiBoard app UI is typeset in JetBrains Mono (the "PhysiBoard Terminal" brand face).
The font files are redistributed unmodified under the SIL Open Font License 1.1.

## Shizuku wireless-ADB implementation (vendored)

- Project: Shizuku, by RikkaApps (Xingchen Song / rikka)
- Source repository: https://github.com/RikkaApps/Shizuku
- License: Apache License, Version 2.0
- License text: `third_party/licenses/Apache-2.0.txt`
- Vendored source: `app/src/main/java/moe/shizuku/manager/adb/` (AdbClient, AdbKey, AdbMdns,
  AdbMessage, AdbPairingClient, AdbPairingService, AdbProtocol, AdbException)

PhysiBoard's self-contained keyboard-backlight control pairs with the device's own Wireless
Debugging using Shizuku's ADB pairing/connection classes, vendored with their original
`moe.shizuku.manager.adb` package name preserved for attribution. These files are covered by
the Apache License 2.0; see `app/src/main/java/moe/shizuku/manager/adb/NOTICE`.

### libadb.so (prebuilt native binary)

- Origin: built from the Shizuku project's native `adb` module, which incorporates BoringSSL
  (for the SPAKE2 pairing handshake and TLS).
- Source repository: https://github.com/RikkaApps/Shizuku (native `adb`/starter modules);
  BoringSSL: https://boringssl.googlesource.com/boringssl
- License: Apache License, Version 2.0 (Shizuku); BoringSSL is distributed under the OpenSSL
  License / ISC-style terms as documented in the BoringSSL source.
- Bundled file: `app/src/main/jniLibs/arm64-v8a/libadb.so` (arm64-v8a only)

The JNI entry class name `moe/shizuku/manager/adb/PairingContext` is hardcoded in the binary
and is therefore preserved in the vendored Kotlin sources.

## Android Open Source Project LatinIME

## Android Open Source Project LatinIME

- Project: Android Open Source Project, LatinIME (`platform/packages/inputmethods/LatinIME`)
- Source repository: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- Pinned source revision used as reference: `127336e9f29d69607eab55982324b210279ae8c5`
- License: Apache License, Version 2.0
- License text: `third_party/licenses/Apache-2.0.txt`

### Derived scope

Pastiera's full virtual keyboard mode uses AOSP LatinIME as a reference for keyboard geometry and visual assets:

- `app/src/main/java/it/palsoftware/pastiera/inputmethod/aospkeyboard/AospKeyboardView.kt`
- AOSP-derived keyboard background, key, spacebar, preview, popup and selected-popup `.9.png` resources under:
  - `app/src/main/res/drawable-mdpi/`
  - `app/src/main/res/drawable-hdpi/`
  - `app/src/main/res/drawable-xhdpi/`
  - `app/src/main/res/drawable-xxhdpi/`
  - `app/src/main/res/drawable-xxxhdpi/`

### Non-derived scope

Pastiera does not import AOSP dictionaries. Suggestions, status bars, prediction bars, IME lifecycle, settings, PKB behavior and custom dictionaries remain Pastiera-specific unless separately documented.

## OpenGameArt Typing Sounds

Pastiera includes short typing sound samples derived from CC0 sound effects.

### Keyboard Soundpack #1 [Typing and Single Keystrokes]

- Author: unicaegames
- Source: https://opengameart.org/content/keyboard-soundpack-1-typing-and-single-keystrokes
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)
- Derived files:
  - `app/src/main/res/raw/typing_click_1.ogg`
  - `app/src/main/res/raw/typing_click_2.ogg`
  - `app/src/main/res/raw/typing_click_3.ogg`
  - `app/src/main/res/raw/typing_click_4.ogg`

### Typewriter sounds

- Author: Cassie-OrbitGames
- Source: https://opengameart.org/content/typewriter-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

### Mechanical Sounds

- Author: BMacZero
- Source: https://opengameart.org/content/mechanical-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

Typing sound derivatives are included under `app/src/main/res/raw/typing_*.ogg`.

## Unicode CLDR Emoji Annotations

- Project: Unicode Common Locale Data Repository (CLDR)
- Source repository: https://github.com/unicode-org/cldr-json
- Source path used by generator: `cldr-json/cldr-annotations-full/annotations`
- License: Unicode Data Files and Software License / Unicode License

Pastiera's local emoji search assets under `app/src/main/assets/common/emoji_search/*.tsv`
are generated from Unicode CLDR annotation data by `scripts/generate_emoji_search_assets.py`.
The generated TSV files are filtered to the emoji set bundled with Pastiera and normalized
for compact local lookup.

## Leipzig Corpora Collection / Wortschatz Leipzig

- Project: Leipzig Corpora Collection / Wortschatz Leipzig
- Project pages: https://corpora.uni-leipzig.de/ and https://wortschatz.uni-leipzig.de/
- Companion asset repository: https://github.com/palsoftware/pastiera-dict
- License: Creative Commons attribution terms for the downloaded corpus/frequency-list data;
  the Leipzig frequency dictionary word lists are documented as CC-BY 3.0, while current
  downloadable corpus data may carry corpus-specific terms.

Pastiera's bundled base dictionaries under `app/src/main/assets/common/dictionaries/*_base.json`
and the generated serialized dictionaries under `app/src/main/assets/common/dictionaries_serialized/*_base.dict`
are frequency-list derivatives built mainly from Leipzig Corpora Collection / Wortschatz Leipzig
word-frequency data, with project-maintained filtering, truncation, normalization, and additional
entries. The companion `pastiera-dict` repository distributes larger downloadable `.dict` assets
and their manifests. Conversion and serialization scripts live under `scripts/`.

Provenance note: the maintained dictionary pipeline and current maintainer-provided sources point
mainly to Leipzig/Wortschatz frequency data. Some older bundled entries predate the current
documentation trail, so their exact upstream corpus IDs are not fully reconstructed here.
