# Reddit post — PhysiBoard 1.2.2: a status bar you can hit, and only where you want it

Suggested subs: r/unihertz, r/Titan2Elite (if it exists), r/PhysicalKeyboards.
Images, in order: `status-bar-before-after.png`, `status-bar-settings.png`.

---

**Title:** PhysiBoard 1.2.2 — the keyboard status bar is finally tall enough to tap, and it only shows up in the apps you pick

---

Quick one for the Titan 2 Elite crowd. PhysiBoard is my GPLv3 fork of Pastiera tuned for this phone — physical keyboard first, plus a toolbox for the stuff Unihertz didn't give us a switch for (keyboard backlight that stays on, bloat removal, a notification ring around the camera hole, a key that pulls down the shade). This update is all about the little strip that sits above the keyboard.

**The strip was 36dp tall. Android's own touch minimum is 48dp.** I'd been fat-fingering the clipboard button for weeks before I measured it. It's now 56dp out of the box — the same height as a text box — with a *Bar height* setting (36 / 48 / 56 / 64) if you want it smaller or bigger. Clipboard and mic got a third narrower and the three suggestion slots take the space back.

[before/after image]

**It only shows up where you want it.** Old behaviour was a switch: bar everywhere or bar nowhere. Now it's *Always / Never / Only in these apps*. Pick the third and the strip appears when you're typing in Messages, Gmail, WhatsApp, Signal, Telegram, and so on (that list is seeded for you — only the ones you actually have installed show) and stays out of the way everywhere else, including the launcher's search box. Add or remove anything from the list.

[settings image]

Other things that landed in 1.2.x since the last post, in case you missed them:

- **Notification ring** around the camera cutout, aodNotify-style, on a phone whose ROM has AOD compiled out. Breathes in the app's colour, or a colour you assign per app, until you pick the phone up. Fitted to the lens out of the box.
- **A key for the notification shade** (and one for quick settings), in the key mapper.
- **T2E Tools** is what the device hub is called now. Backlight, bloat, stabiliser, the ring — all in one place.
- **What's new** card after an update that lists what actually changed instead of "bug fixes and improvements".

Your settings survive updates — install over the top.

APK and source: https://github.com/brobata/physiboard/releases/latest
Changes, fully: https://github.com/brobata/physiboard/blob/main/PHYSIBOARD_CHANGES.md

Still ADB once at setup for the privileged bits (backlight, bloat, ring permissions); nothing at runtime. If the strip height still feels wrong on your unit, say so — I'm tuning against one phone.
