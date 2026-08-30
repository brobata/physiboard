# Reddit post — PhysiBoard 2.0.1: I reset everyone's settings on purpose

Suggested subs: r/unihertz, r/PhysicalKeyboards.
Images, in order: `2.0.1-whatsnew.png`, `2.0.1-bar-height.png`, `2.0.1-german.png`.
Screenshots also in `~/shared/physiboard-reddit-2.0.1/`.

---

**Title:** PhysiBoard 2.0.1 — I wiped everyone's settings back to a known good config, and I'm sorry

---

Owning this one up front. 2.0 reorganised the settings tree and I broke things doing it. Screens moved, some got merged, and a few settings ended up still stored but with no screen left to change them. If you were sitting on a value you didn't like, you were stuck with it, and there was nothing you could do from inside the app. The bar height was the one people actually noticed. It was still being applied, the row was just gone.

So 2.0.1 does something blunt. **On first launch it clears your settings and replaces them with the ones off my phone.** Once. Never again.

[whatsnew image]

The reason it's safe to be this heavy handed: we all have the exact same phone, same firmware, same build number. There is exactly one configuration that is known to work, because I use it every day, and now that's what everyone starts from. Theme, SYM pages, the notification ring fit, shortcuts, status bar, all the toggles.

**Your words are safe.** The personal dictionary, your custom layouts and your variations are stored as files, not settings, so the wipe doesn't reach them. Anything you taught it is still there. Your old settings also get written out to `settings_before_reset.json` inside the app's storage before anything is cleared, so nothing is genuinely destroyed if you want to go digging.

The one thing I deliberately kept: if you'd paired the ADB stuff and let it change your Fn key or your orange side key, PhysiBoard remembers what those pointed at *before* it touched them. Those writes survive an uninstall, and that record is the only way "Reset device settings to stock" can put them back. Wiping it would have stranded those changes on your phone forever, so it stays.

**Bar height is back**, 36 / 48 / 56 / 64, sitting under Show status bar on the Status Bar Theme page. Default is 56. 36 is below Android's minimum touch target and I've said so right in the description, because someone asked for it and the reason was good: if you live on the physical keys and only ever read that strip, you want it small and out of the way. It's your phone.

[bar height image]

Other stuff in this one:

- **All nine translations are actually finished.** They'd been sitting at about half for ages, so most of the app fell back to English if you weren't reading it in English. German, French, Spanish, Italian, Polish, Russian, Ukrainian, Vietnamese and Armenian are all complete now.
- **Typing got cheaper.** The Alt character map and the launcher shortcut table were being read off disk on *every single keypress* while the modifier was held. Both cached now. Clipboard history writes moved off the typing thread, and the brightness shortcuts don't block your keyboard while the command runs.
- **A leaked background process.** Every time you toggled the trackpad settings it left a `getevent` reader running. Sat there forever chewing battery. Fixed.
- **Things tell you when they fail now.** A pile of places quietly logged an error and carried on as if it worked. Saving key mappings, SYM layers, custom corrections, the dictionary. Restoring a backup that isn't a PhysiBoard backup says so instead of "Restore completed". Dictation that can't reach the text field stops instead of leaving the mic sitting there listening at nothing.
- **The what's new card shows the right release.** It was a hand-copied file and 2.0.0 shipped showing you the 1.2.4 notes. It's generated from the changelog at build time now, so it can't drift again.

[german image]

APK and source: https://github.com/brobata/physiboard/releases/latest
Full changes: https://github.com/brobata/physiboard/blob/main/PHYSIBOARD_CHANGES.md

Install over the top as usual. This time your settings will not survive, and that's the point. Set it up how you like once it lands and it'll stick.
