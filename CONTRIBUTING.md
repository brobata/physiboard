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

Standard Gradle Android project, one build, no flavors, application id `brobata.physiboard`:

```bash
./gradlew :app:assembleDebug      # development build
./gradlew :app:assembleSideload   # the release R8 pipeline, own application id, logging kept
./gradlew :app:assembleRelease    # the shipped build; needs the signing key
```

`assembleSideload` installs alongside the real app (`brobata.physiboard.sideload`), so it is the
build to put on a phone you rely on. The source namespace is `brobata.physiboard`; attribution
lives in NOTICE.md.

Release signing is read from `release/keystore.properties` or the environment
(`PHYSIBOARD_KEYSTORE_PATH`, `PHYSIBOARD_KEYSTORE_PASSWORD`, `PHYSIBOARD_KEY_ALIAS`,
`PHYSIBOARD_KEY_PASSWORD`); no keys are committed.

## Testing

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Lint is gated: findings that predate the gate live in `app/lint-baseline.xml`, anything new fails
the build. Fix the finding rather than adding to the baseline.

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
