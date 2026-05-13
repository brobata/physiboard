# Third-party Notices

Pastiera is licensed under the GNU General Public License version 3. Some components are derived from third-party open source projects and retain their original attribution requirements.

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
