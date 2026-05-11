# AOSP Layout Source (Issue #183 bootstrap)

This project uses AOSP LatinIME as the reference source for initial software keyboard layout alignment and selected keyboard visual assets.

## Pinned source

- Repository: `platform/packages/inputmethods/LatinIME`
- Source URL: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- Branch checked: `main`
- Pinned commit: `127336e9f29d69607eab55982324b210279ae8c5`
- Checked on: 2026-05-11
- License: Apache License, Version 2.0

## Derived artifacts

- `app/src/main/java/it/palsoftware/pastiera/inputmethod/aospkeyboard/AospKeyboardView.kt`
- AOSP-derived keyboard background, key, spacebar, preview, popup and selected-popup `.9.png` resources under `app/src/main/res/drawable-*`.

## Attribution files

- Repository notice: `THIRD_PARTY_NOTICES.md`
- App-visible notice: `app/src/main/assets/common/notices/aosp_latinime.md`
- Apache-2.0 license text: `third_party/licenses/Apache-2.0.txt`
- Packaged Apache-2.0 license text: `app/src/main/assets/common/licenses/Apache-2.0.txt`

## Scope notes

- Pastiera remains licensed under GPLv3.
- AOSP LatinIME is Apache-2.0 and GPLv3-compatible.
- Dictionaries are not imported from AOSP.
- Pastiera suggestions, status bars, prediction bars, IME lifecycle, settings, PKB behavior and custom dictionaries remain Pastiera-specific unless separately documented.
