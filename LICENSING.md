# Licensing

PhysiBoard 3.0 is copyright © 2026 brobata. It is free software under the
**GNU General Public License, version 3** (the full text is in [`LICENSE`](LICENSE)). You may
use, study, share and modify it under those terms. Any work built on it must be published under
the same license with its source.

## Commercial license

If you want to use PhysiBoard, or code from it, in a product without the obligations of the
GPL, for example a closed-source keyboard or a preinstalled build for a device, a separate
commercial license is available. Contact the maintainer through GitHub
([@brobata](https://github.com/brobata)) to discuss terms.

Because every line of 3.0 is written by the maintainer or contributed under the agreement in
[`CONTRIBUTING.md`](CONTRIBUTING.md), the maintainer can grant such licenses for the whole
work.

## Trademark

The license covers the code, not the name. **PhysiBoard**, its icon and its visual identity are
marks of the maintainer and are not licensed under the GPL. A fork or derivative must be
distributed under a different name and icon, and must not suggest that it is made or endorsed
by the PhysiBoard project. Saying "based on PhysiBoard" is fine.

*Unihertz*, *Titan* and *Titan 2 Elite* are marks of Unihertz. PhysiBoard is not affiliated
with, endorsed by, or supported by Unihertz.

## Third-party code

`broker/` is a vendored subset of **[Shizuku](https://github.com/RikkaApps/Shizuku)** by
RikkaApps, under the Apache License 2.0, together with its prebuilt `libadb.so`. It keeps its
original package name so attribution is visible in the source and in stack traces. It is not
PhysiBoard's work and is not under PhysiBoard's licence: Apache-2.0 travels with it, and the
full text is in `third_party/licenses/`. See `broker/NOTICE`.

It is a separate Gradle module so the boundary is visible in the build rather than only in a
comment, and so it is plain that the clean-room rule governing the rest of 3.0 does not apply
to it.

`app/src/main/res/font/jetbrains_mono_*.ttf` vendors three static weights (Regular, Medium,
Bold) of **[JetBrains Mono](https://github.com/JetBrains/JetBrainsMono)** by the JetBrains Mono
Project Authors, under the **SIL Open Font License, version 1.1**. It is the app-shell's
title and prompt typeface (app-shell.md SS22.1) and is not PhysiBoard's work; the full licence
text is in `third_party/licenses/OFL-1.1.txt`.

`app/src/main/res/font/inter_*.ttf` vendors three static weights (Regular, Medium, SemiBold) of
**[Inter](https://github.com/rsms/inter)** 4.1, Copyright 2016 The Inter Project Authors, under
the **SIL Open Font License, version 1.1**, unmodified. It is the app-shell's reading typeface
(app-shell.md SS22.1) and is not PhysiBoard's work; the full licence text, with Inter's own
copyright line, is in `third_party/licenses/OFL-1.1-Inter.txt`. The About screen credits both
fonts under "Fonts".

The Fill page's password manager suggestions use **androidx.autofill** (Android Jetpack, by
the Android Open Source Project, Apache License 2.0) as an ordinary library dependency, like the
other Jetpack libraries the app is built with: it writes the chip style password managers read.
None of its code is copied into PhysiBoard's source.

### Unicode data

The emoji picker's emoji lists and English search names (`ime/src/main/assets/emoji/`,
`ime/src/main/assets/emoji_search/en.tsv`, built by `tools/emoji/build_emoji_assets.py`) and the
skin-tone table (`core/actions/src/main/kotlin/brobata/physiboard/core/actions/emoji/SkinToneTable.kt`,
built by `scripts/build_skin_tone_table.py`) are derived from Unicode Emoji 17.0
`emoji-test.txt`, **Copyright © 1991-2026 Unicode, Inc.**, used under the **Unicode License
v3**, which allows redistribution with this notice. The full licence text is in
`third_party/licenses/Unicode-3.0.txt`. The names the symbol search shows are not shipped; they
come from the phone's own Unicode tables at run time.

The kaomoji collection (`core/actions/.../kaomoji/KaomojiData.kt`) is PhysiBoard's own work.

### Online services

The GIF Sym page (off unless the user turns it on) searches **[KLIPY](https://klipy.com)** at
run time. No KLIPY code or media ships with PhysiBoard: the client is PhysiBoard's own, written
from KLIPY's public API documentation, and GIFs are shown and sent straight from KLIPY's servers
under KLIPY's terms, with the "Powered by KLIPY" attribution they ask for. A build needs its own
KLIPY API key (`klipy.apiKey`), which is never committed.

The bundled dictionaries carry their own terms; see [`docs/dictionaries.md`](docs/dictionaries.md).
The English word-pair table is built from sentences of [Tatoeba](https://tatoeba.org), used under
CC BY 2.0 FR.

## The 2.x line

PhysiBoard 1.x and 2.x were a GPLv3 fork of Pastiera by Andrea Palumbo (PalSoftware) and
contributors. That line lives on the `legacy-2.x` branch, stays GPLv3, and carries its own
notices. Nothing from it is in 3.0.

## Supporting the project

PhysiBoard is free and has no ads, no tracking and no paid tier. If it is useful to you, a
coffee is welcome: see the Sponsor button on the repository, or *About → Support PhysiBoard*
in the app.
