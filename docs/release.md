# The release process

Adapted from `docs/spec/app-shell.md` SS24 ("The release process as documented"), which is 2.x's
process as observed on the device; the mechanics below are 3.0's own, built for what `:app`'s
Gradle files and `:core:shell`'s update checker actually do today.

**The repository memory rule still applies: no version bump, tag or publish without an explicit
go-ahead from the maintainer in that message. Local work stays local until asked for.**

## Signing

The maintainer's keystore is never checked into this repository. The `release` build type in
`app/build.gradle.kts` reads four environment variables and applies a signing config only when
all four are present:

| Variable | Holds |
|---|---|
| `PASTIERA_KEYSTORE_PATH` | Path to the `.jks`/`.keystore` file |
| `PASTIERA_KEYSTORE_PASSWORD` | The keystore's password |
| `PASTIERA_KEY_ALIAS` | The signing key's alias inside that keystore |
| `PASTIERA_KEY_PASSWORD` | The signing key's own password |

When any of the four is missing, blank, or unset, `:app:assembleRelease` still succeeds; it
prints one warning line during Gradle's configuration phase and produces an **unsigned** APK at
`app/build/outputs/apk/release/app-release-unsigned.apk`. This is what CI and any contributor
without the keystore should expect, and it is why the release build type must never be made to
fail when the environment is bare.

## Building

```
./gradlew :app:assembleRelease
```

Signed output (when the four variables above are set):
`app/build/outputs/apk/release/app-release.apk`

The `sideload` build type (`./gradlew :app:assembleSideload`) stays unshrunk, debuggable and
signed with the debug key; it is the build for testing on the maintainer's own phone next to the
2.x daily driver (a different `applicationId`, see `app/build.gradle.kts`), never for
distribution.

## Cutting a release

1. **Version.** The maintainer sets `versionCode` and `versionName` in `app/build.gradle.kts`.
   No other step in this document does that.
2. **Build.** With the four signing variables exported, run `./gradlew build test
   :app:assembleRelease`. It must build clean and every test must pass.
3. **Checksum.** Compute the SHA-256 of the signed APK and save it next to the APK:
   `sha256sum app-release.apk > app-release.apk.sha256`.
4. **Tag.** Create an annotated tag named `v<major>.<minor>.<patch>` (matching `versionName`,
   for example `v3.0.0`) on the commit being released, and push it.
5. **GitHub release.** Create a release from that tag, title it with the version, attach the
   APK and its `.sha256` file, and mark it **Latest**. It must not be left as a draft and must
   not be marked pre-release.

Steps 4 and 5 matter to more than the person downloading it: `:core:shell`'s update checker
(`ReleaseFeed`, `UpdatePolicy`, spec app-shell.md SS13) reads GitHub's releases list, picks the
first entry that is neither a draft nor a pre-release, and offers the first attached asset whose
name ends in `.apk` (case-insensitive) as the download. A release left in draft, marked
pre-release, or without an `.apk` asset is invisible to every installed copy's update check even
though the page itself is public.

## What is not automated yet

3.0 has no `scripts/build-release.sh` counterpart yet: every step above is run by hand. If that
changes, this document is the place to update.
