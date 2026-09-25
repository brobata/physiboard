# PhysiBoard

A from-scratch, physical-keyboard-first Android IME for the Unihertz Titan 2 Elite: it types
straight off the hardware keyboard, corrects and predicts from an on-device dictionary, and adds
the device's own features (backlight, notification ring, screen trackpad, dictation) without a
network connection or a soft keyboard layout in sight. PhysiBoard succeeds Pastiera, the GPLv3
keyboard by Andrea Palumbo (PalSoftware) that PhysiBoard's earlier 1.x and 2.x line forked; this
rewrite is a clean-room design written from a behavioral specification and shares no code with it
or with anything that came before it.

## License

GPL-3.0. See [`LICENSE`](LICENSE) for the text. A commercial license is available for anyone who
wants PhysiBoard, or code from it, without the GPL's obligations; [`LICENSING.md`](LICENSING.md)
has the terms, the trademark notice, the third-party attributions, and how the 2.x line relates.
Contributions are accepted under the agreement in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Module layout

Everything under `core/` and `device/titan` is pure Kotlin, no `android.*` import anywhere, so
every rule in `docs/spec/` is a JVM test that runs with no device or emulator. `ime`, `broker`,
`device/privileged` and `app` are the Android adapters that wire that pipeline to a real keyboard
service, a real settings app, and the Titan's own privileged features.

| Module | What it owns |
|---|---|
| `core/keys` | Physical key identity, modifier state, and character resolution. |
| `core/text` | The typing pipeline: strokes in, editor operations out. |
| `core/dict` | The dictionary index and format, and the personal word list. |
| `core/pointer` | Caret geometry and usability, and the screen trackpad's gesture rules. |
| `core/speech` | Dictation as a pure state machine: engine state, cues, text operations. |
| `core/settings` | The typed settings schema, its codec, and the one-shot 2.x importer. |
| `core/strip` | The candidates/status strip's decisions, given a snapshot it never computes. |
| `core/actions` | Snippet expansion, clipboard history, pickers, launcher keys, the command catalogue. |
| `core/toolbox` | The T2E toolbox's decisions: bloat removal, screen density, system tweaks, key mapping. |
| `core/shell` | The app shell as plain Kotlin: update checker, what's-new note, launch routing, backup codec. |
| `device/titan` | Titan 2 Elite-specific pure decisions the pipeline and toolbox both depend on. |
| `device/privileged` | The privileged side of Titan features: pairing, the broker, backlight, ring. |
| `broker` | A vendored, unmodified subset of Shizuku (Apache-2.0) for pairing with Wireless Debugging. |
| `ime` | The Android adapter: the `InputMethodService`, dictation, dictionary loading, the strip UI. |
| `app` | The settings app (Compose/Material 3), the 2.x importer, and the shipped APK. |

## Building

```
./gradlew :app:assembleSideload
```

produces `app/build/outputs/apk/sideload/app-sideload.apk`: an unshrunk, debuggable build with a
`.dev3` application id suffix so it installs next to a 2.x daily driver without touching it. This
is the build for testing on the real Titan. See [`docs/release.md`](docs/release.md) for the
signed `release` build type and the steps to cut an actual release.

## Testing

```
./gradlew build test
```

runs every module's JVM tests, including the pure Kotlin pipeline modules under `core/` and
`device/titan`, which encode `docs/spec/`'s test cases directly against the pipeline as pure
functions. Run a single module with `./gradlew :core:text:test` (or any other module path).

For the behavior no unit test can see, `tools/differential` runs the same keystrokes through the
shipping 2.x keyboard and this rewrite and diffs what each produces, without ever reading 2.x's
source; see `tools/differential/README.md` for how to run it and `tools/differential/corpus.md`
for the test lines.

## Support

Free, no ads, no tracking, no paid tier. If it earns its keep on your phone, the Sponsor button
on this page, or <https://github.com/sponsors/brobata>, buys the maintainer a coffee.
