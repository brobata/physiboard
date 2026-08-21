# Contributing to PhysiBoard

Thanks for your interest. PhysiBoard is a GPLv3 fork of
[Pastiera](https://github.com/palsoftware/pastiera) focused on physical-keyboard Android
devices (primarily the Unihertz Titan 2 Elite). Contributions are welcome.

## Before you start

- **Generic keyboard improvements** are often a better fit **upstream in Pastiera** — the core
  engine lives there, and both projects benefit. If your change isn't physical-keyboard- or
  Titan-specific, consider opening it against Pastiera first.
- For device-specific features (backlight, Fn behavior, physical-key swipe, the Terminal
  UI/brand), PhysiBoard is the right home.

## Building

Standard Gradle Android project. The physical-keyboard release flavor is `physi`:

```bash
./gradlew :app:assemblePhysiDebug
```

The build has three product flavors: `physi` (PhysiBoard, app id `brobata.physiboard`),
`stable`, and `nightly`. The internal source namespace stays `it.palsoftware.pastiera` — this
is intentional (it keeps the fork lineage honest and merges from upstream possible). Only the
user-facing application id and branding differ.

Release signing is read from Gradle properties / environment variables
(`PASTIERA_KEYSTORE_PATH`, `PASTIERA_KEYSTORE_PASSWORD`, `PASTIERA_KEY_ALIAS`,
`PASTIERA_KEY_PASSWORD`); no keys are committed.

## Testing

```bash
./gradlew :app:testStableDebugUnitTest
```

On-device changes (especially anything touching the IME service, modifier handling, or the
backlight) should be verified on real hardware or an emulator configured for hardware-keyboard
input before submitting.

## Pull requests

- Keep changes focused; one feature or fix per PR.
- Match the existing Kotlin/Compose style in the files you touch.
- Don't modify the vendored `moe.shizuku.manager.adb` package (it's Apache-2.0 code whose
  package name is JNI-locked to `libadb.so`) except to update the attribution notice.
- Update `PHYSIBOARD_CHANGES.md` for user-facing changes.
- New bundled assets or dependencies must have their license recorded in
  `THIRD_PARTY_NOTICES.md`.

## License

By contributing you agree that your contributions are licensed under the **GNU General Public
License, version 3**, consistent with the rest of the project.
