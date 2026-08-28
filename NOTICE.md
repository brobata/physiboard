# Notice

PhysiBoard is free software under the **GNU General Public License, version 3**. The full licence
text is in [`LICENSE`](LICENSE).

## Upstream

PhysiBoard is a fork of **[Pastiera](https://github.com/palsoftware/pastiera)** by **Andrea Palumbo
(PalSoftware)** and its contributors, and is distributed under the same licence. The keyboard engine
— input handling, the suggestion and autocorrection engines, layouts, the theming system — is
derived from their work.

As of 2.0 the fork no longer tracks upstream. The Java package, the preferences file and the product
naming are PhysiBoard's own, and support for every device other than the Unihertz Titan 2 family has
been removed. None of that changes where the work came from, and none of it changes the licence:
PhysiBoard is GPLv3 because Pastiera is, and any redistribution carries the same terms.

If you want to support the people who wrote the engine this runs on, back the original project on
**[OpenCollective](https://opencollective.com/pastiera)**. This fork takes no donations.

## Modifications

GPLv3 §5(a) requires a fork to carry prominent notices stating that it changed the work and when.
[`PHYSIBOARD_CHANGES.md`](PHYSIBOARD_CHANGES.md) is that record, kept per release since 1.0.0.

## Vendored code

`moe.shizuku.manager.adb` is a vendored subset of **[Shizuku](https://github.com/RikkaApps/Shizuku)**
by RikkaApps, used for the embedded wireless-ADB pairing. It keeps its own package name and its own
licence; it is not covered by PhysiBoard's copyright.

## Trademarks

*Unihertz*, *Titan* and *Titan 2 Elite* are marks of Unihertz. PhysiBoard is not affiliated with,
endorsed by, or supported by Unihertz.
