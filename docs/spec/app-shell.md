# App shell: onboarding, home, status, diagnostics, updates, about, notifications, build and release

This document describes everything PhysiBoard 2.x does around the keyboard on the Titan 2 Elite:
what happens when the process starts, how the launcher icon decides which screen to open, the
two-step first-run setup, the "what's new" note after an update, the home screen and its
attention badges, the Status screen, the Diagnostics screen and the bug-report export, the
GitHub update checker and its background job, the release-notes fetcher, the About screen and
its "report a problem" link, every notification the app posts, the receiver that notices a
lost keyboard selection after an update, the small feature-status markers, the build
configuration as far as it changes behavior, the release process as the repository documents
it, and the translations. The settings hub structure (which rows live on which screen) is in
the settings catalog document and is not repeated here; this document names the rows only
where the shell's own behavior hangs on them.

Everything below is the 2.x behavior unless a row says otherwise.

## 1. Vocabulary

- **Home**: the screen the launcher icon opens once setup is done. An action surface: it shows
  only what needs attention, then (3.1) the settings search and the category index (section 6.4).
- **Setup screen**: the two-step first-run flow (enable, then select). Also called onboarding.
- **What's new note**: the single page shown once after a version change, listing this
  release's notes.
- **Enabled**: PhysiBoard appears in Android's list of enabled input methods.
- **Selected** (also "active"): PhysiBoard is the keyboard Android currently routes text input
  to, as recorded in the secure setting `default_input_method`.
- **IME id**: Android's identifier for a keyboard, `package/service-class`. PhysiBoard's is the
  application id followed by `/` and the fully qualified name of its input-method service. The
  short form, application id followed by `/.inputmethod.PhysicalKeyboardInputMethodService`, is
  accepted as the same id everywhere it is compared.
- **GitHub checks allowed**: the build flag for GitHub update checks is on (it is on in every
  build) and the app was not installed by an F-Droid client (installer package
  `org.fdroid.fdroid` or `org.fdroid.basic`). When false, nothing in the app ever contacts
  GitHub for updates and the "Updates" row is absent.
- **Release tag**: a GitHub release's tag, `vX.Y.Z`. The version name is the tag without its
  leading `v` or `V`.
- **Broker**: the embedded wireless-ADB pairing every privileged tool depends on. Its
  behavior is specified in the broker document; this document only says what the shell shows
  about it.
- **Verified broker status**: one of `OK`, `NOT_PAIRED`, `WIRELESS_DEBUGGING_OFF`,
  `NO_SERVICE`, `REJECTED`, or unknown (not yet checked). Verified means the app actually
  connected and ran a trivial shell command; "a pairing key is stored" is a different, weaker
  fact. A verdict is reused for 10 000 ms, then re-checked; forgetting or redoing a pairing
  drops the cached verdict immediately.

## 2. Process start

Every process start, whether the launcher icon, the input-method service, a broadcast or a
background job woke it, runs the same initialization in this order:

1. Settings migration from the 1.x preferences file `pastiera_prefs` into `physiboard_prefs`,
   once per install (details in the settings catalog). It writes synchronously, because the
   keyboard may read a setting microseconds later.
2. The settings baseline reset, once (settings catalog).
3. The one-shot first-run defaults, gated by `impact_defaults_applied` (section 27 lists the
   values). These are stamped here rather than on the home screen on purpose: a user who
   enables the keyboard from Android's own settings never opens the app, and 1.x users in that
   situation ran upstream's defaults for months.
4. The Alt+Shift layout-switch default: on the first run after this rule was introduced, the
   setting `alt_shift_layout_switch` is set to true if the preferences file already had any
   content (an existing installation) and false otherwise, then `alt_shift_default_initialized`
   is written so it never runs again. It does not touch a value the user already chose.
5. The notification ring's keyboard-backlight restore (ring document).
6. Registration of a package-change listener (added, removed, replaced, changed, scheme
   `package`) that keeps the launcher-shortcut app list current (expansion/launcher document).
7. Publishing one dynamic launcher shortcut, id `software_keyboard_mode_toggle`, short label
   "Toggle Keyboard Mode", long label "Temporarily open / close the on-screen keyboard", app
   icon, firing the action `brobata.physiboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE` at the
   app's own no-display activity. The shortcut is removed and re-added on every start.
8. Registration of the additional input-method subtypes, posted to the main thread rather than
   run inline.

There is no StrictMode configuration anywhere in the app. Nothing about locale is set at
process level; locale is applied per activity (section 22).

## 3. Launch routing

Opening the launcher icon runs this decision before anything is drawn:

1. If the first-run pages are due (section 4, "Who sees them"): open them and close the home
   screen. Nothing else in this list runs.
2. Else if setup is not recorded (section 4.1; so PhysiBoard is already enabled and selected):
   write what Skip writes (section 4.6) and draw the home screen. Nothing else in this list runs.
3. Else if the what's-new note is due: it is due when setup is recorded, the build's version
   name is non-blank, and `last_seen_whats_new_version` differs from it. Open the what's-new
   note and close the home screen.
4. Else, if GitHub checks are allowed, (re)schedule the daily background update job
   (section 13.7).
5. Draw the home screen.

The enabled/selected probe (section 8.2) is read for step 1 only while setup is not recorded.
The decision is `LaunchRouting.decide` in `:core:shell`.

Help's "Show the tutorial" row opens the first-run pages directly, without touching
`tutorial_completed` (3.2; 3.0 reset it to false, so backing out of a review made the launcher
open setup again later). On a fresh install, pressing Back out of the first page instead of
finishing leaves `tutorial_completed` false, so the next tap on the icon opens the pages again
while the keyboard is still not set up, and settles it silently (step 2) once it is.

## 4. First-run setup (3.2)

Three short pages, one thing each: turn PhysiBoard on, make it the keyboard, then the optional
extras. They replace 3.0's single page with two step cards.

### 4.1 Who sees them

Setup is recorded when `tutorial_completed` is true OR `last_seen_whats_new_version` is
non-blank. The pages open only when setup is not recorded AND PhysiBoard is not already both
enabled and selected (section 8.2). That is:

| Setup recorded | Enabled | Selected | Launcher icon opens |
|---|---|---|---|
| no | no | either | the first-run pages, page 1 |
| no | yes | no | the first-run pages, page 2 |
| no | yes | yes | home; setup is recorded as finished (section 3, step 2) |
| yes | any | any | home, or the what's-new note when it is due |

Why the version stamp counts: every way of finishing setup, in 3.0, 3.1 and 3.2 alike (the
setup page's Skip and Done, the what's-new note's Done, launch routing's own record), writes
`tutorial_completed` = true and stamps `last_seen_whats_new_version` together, and nothing
writes the stamp otherwise or ever clears it. But 3.0 and 3.1's Help "Show the tutorial" reset
`tutorial_completed` to false and left the stamp alone, so an updater whose user once reviewed
the tutorial holds false with a stamp. The stamp is what says that install finished setup.

Why updaters never see the pages: 3.0 and 3.1 open home only after their own setup was
finished, which wrote both records; a later tutorial review cleared only one. An install
updating from 3.0 or 3.1 that has ever seen home therefore holds at least the stamp, with no
baseline step needed; one that never finished setup did not see home either and gets the
pages, as it would have got the old page. An install whose records were lost but whose
keyboard is clearly in use (app data cleared, a backup from a phone that never finished setup)
is caught by the keyboard check and goes home. 2.x imports carry the flag over
(settings-catalog.md). Backups carry both records, so a restore never reopens the pages. No new
setting was added for 3.2: the two existing records already say "first run is behind this
install".

### 4.2 The pages

Every page has the home screen's terminal header (section 22.1) with the command `setup`
(`physiboard:~$ setup`, the cursor breathing on a 650 ms period) and the page number at the far
end in the value style (`1/3`). Below it the page scrolls on its own; a bar pinned to the bottom,
under a 1 dp rule, holds "Skip" (a text button, at the start) and "Next" ("Done" on page 3, at
the end). Next is filled once the page's step is done and outlined before, so it is a way past
the step rather than the thing to press. A page's title is mono (headline small) and marked a
heading; its text is the reading style.

| Page | Title | Text | The pane | Its button does |
|---|---|---|---|---|
| 1 | "Turn on PhysiBoard" | "Android keeps a new keyboard switched off until you turn it on. Open the list and switch PhysiBoard on." / "Android shows the same warning for every keyboard. That's expected." | "PhysiBoard is off", button "Open keyboard list"; done: "PhysiBoard is on" | Opens Android's input-method settings (`android.settings.INPUT_METHOD_SETTINGS`) |
| 2 | "Make it your keyboard" | "Pick PhysiBoard in the list Android shows. You can change keyboards again any time." | "PhysiBoard is not your keyboard yet", button "Choose keyboard"; done: "PhysiBoard is your keyboard". While PhysiBoard is not enabled: "Turn PhysiBoard on first", button "Back to step 1" | Shows Android's input-method picker; "Back to step 1" goes to page 1 |
| 3 | "A few extras" | "All optional. Turn on what you like now, or later in Settings." | three rows, below | each row's "Turn on" |

A pane that is still to do has the amber (featured) border, the step's keycap icon and its
button; once done it is a calm pane with the green check and no button.

Page 3's rows, each with a "Turn on" button that becomes a green check and "On" once it is:

| Row | Line under it | Turn on opens | On when |
|---|---|---|---|
| "Accessibility service" | "Needed for Fn shortcuts everywhere and focusing the text box" (Home's warning line, section 6.3), and while off: "If Android says it is restricted: App info, ⋮, Allow restricted settings." | the service's own page in Android's settings, else the list (the Accessibility service screen's opener) | the service is enabled |
| "Display over other apps" | "Needed for the emoji and clipboard panels and the screen trackpad" | `OverlayPermission.explainAndOpenSettings` (trackpad-caret-nav.md): the toast "PhysiBoard needs Display over other apps. Turn it on for this app, then come back." and Android's screen for this app, at most once every 8 s | `Settings.canDrawOverlays` |
| "Spell checking" | "Pick PhysiBoard so apps underline misspellings" (Home's line) | Android's spell checker picker, or the nearest screen (autocorrect-suggestions.md section 18) | PhysiBoard is the system spell checker |

Nothing on these pages turns anything on by itself; each row only opens the place where the
user does.

### 4.3 Live state

The pages read the enabled/selected probe (section 8.2), the accessibility service, the overlay
permission and the spell checker immediately, on every return to the app, and every 1000 ms
while showing with the app in front (the input-method picker is a dialog over the page, so no return follows it).
Panes and rows change by themselves; there is nothing to confirm. The pages open at the first
step still to do: page 1 while not enabled, page 2 while enabled but not selected, else page 3.

### 4.4 Navigation and the hardware keyboard

- Next moves one page on; Done finishes. Skip finishes from any page.
- Back (the gesture, the key, or Esc on a hardware keyboard) goes to the page before; on page 1
  it leaves the pages as before (section 3), with nothing written.
- Every button is focusable. When a page opens, focus goes to the page's own button while its
  step is to do, and to Next once it is done (on page 3, to Done), so Enter presses the next
  thing to do. As everywhere in Android, the first key pressed after touching the screen only
  shows the focus; the next Enter presses it. Arrow keys and Tab move between the buttons.
- The current page survives rotation and the process being reclaimed.

### 4.5 Square screen

The bar is pinned and the page scrolls above it, so Skip and Next are never below the fold on
the Titan's 1080x1200 screen. The 3.0 page's 360 ms scroll to its bottom is gone with it.

### 4.6 What "complete" writes

Skip, Done, and the silent settle of section 3 step 2 write `tutorial_completed` = true and
`last_seen_whats_new_version` = the current version name, in one commit. Because the version is
stamped here, a fresh install never sees the what's-new note for the version it was installed
with.

### 4.7 Permissions

The first-run pages ask for no permission themselves; their extras only open the screens where the user grants one. The first time the home screen is drawn on Android 13 or
later without the notification permission, it launches the system `POST_NOTIFICATIONS` prompt
once per home screen creation and ignores the answer; this is what lets the update
notification and the re-selection notice (sections 13.7 and 17) be shown. The toolbox's device
setup card asks for the same permission again when it appears un-granted, because the pairing
code arrives as a notification (section 4.8). Microphone and overlay permissions are asked by
their own features (dictation and trackpad documents).

### 4.8 The device setup card

The card sits at the top of the toolbox screen and on the smart backlight screen. It is the
broker document's subject; the shell facts are:

- It re-reads "is a key stored", "are developer options on"
  (`development_settings_enabled` = 1 in global settings), "is wireless debugging on", and
  "is Do Not Disturb on" (`zen_mode` != 0 in global settings) every 1500 ms while visible.
- Its title and colour are driven by the verified status: "Paired" with a check when `OK`;
  "Checking…" while a key is stored and no verdict has landed; "Cannot reach the system" when a
  key is stored but the verdict is anything else; "Setup needed" when no key is stored.
- While not paired it lists three numbered steps. Without developer options: "Open Settings →
  About phone", "Tap Build number seven times", "Come back here", and a button "Open About
  phone" (`android.settings.DEVICE_INFO_SETTINGS`). With developer options: "Turn on Wireless
  debugging", "Tap Pair device with pairing code", "Type the code into the PhysiBoard
  notification", and a button "Open Wireless debugging" that tries the action
  `android.settings.ADB_WIRELESS_SETTINGS` and falls back to
  `android.settings.APPLICATION_DEVELOPMENT_SETTINGS`.
- Below the steps, in error colour: "Allow PhysiBoard notifications first — the pairing code
  arrives as one." when the notification permission is missing, else "Do Not Disturb is on and
  may hide the pairing code. Turn it off until you are paired." when DND is on.
- Whenever the card is visible and not paired, the pairing foreground service (channel
  `adb_pairing`, importance high, silent, no badge, no bubbles) is started and left running
  when the user leaves the screen; it stops itself when pairing succeeds, and the card stops it
  when a key appears.
- When paired but not `OK`: the body explains the verdict (`REJECTED`: the code was probably
  mistyped and the app holds a key the phone never accepted, button "Re-pair";
  `WIRELESS_DEBUGGING_OFF` or `NO_SERVICE`: an "Open Wireless debugging" button plus "Forget
  pairing"). "Forget pairing" and "Re-pair" both delete the stored key and drop the cached
  verdict.

## 5. The what's-new note

### 5.1 Source

The note's text is the asset `common/whats_new.md`, generated at build time from the top of
`PHYSIBOARD_CHANGES.md` (section 24.3). The build finds the section whose heading is
`## <versionName> ` (with a trailing space, so `2.0.7` does not match `2.0.71`); if none
matches it takes the first heading matching `## <digits>.<digits>` so an "Unreleased" section
at the top never reaches users. It keeps the lines of that section up to, not including, the
first line whose trimmed form starts with `<!-- /card -->`, trims the result and writes it with
a trailing newline. Without the marker the whole section ships; a section without a numeric
heading at all yields an empty file.

### 5.2 Parsing into the card

The generated markdown is parsed into a list of (title, body) notes:

- Every trimmed line starting with `- ` begins a bullet. A non-empty line that follows a bullet
  and does not start a new one is appended to the bullet with a single space (wrapped bullets).
- Non-empty lines before the first bullet are the preamble. Consecutive lines join with a
  space; a blank line inside the preamble becomes a newline. A non-empty preamble becomes one
  untitled note (empty title) that comes first.
- A bullet beginning with `**Title**` splits into that title (trailing `.` and `:` removed) and
  the rest as body, with leading spaces, em-dashes, hyphens and colons stripped from the body.
  A bullet without a bold lead splits at the first ` — ` (space, em-dash, space) into title and
  body; if there is none the whole bullet is the title and the body is empty.
- `**` and backticks are removed everywhere; `*text*` becomes `text` only when the asterisks
  are not adjacent to word characters or other asterisks, so `2*3` survives.
- Blank input yields no notes.

### 5.3 Screen

The note is one scrolling page: the header `physiboard:~$ whatsnew`, a right-arrow icon and
"Updated to v<version>", then either "Your keyboard is up to date. Fixes and improvements are
live." when there are no notes, or each note as a semi-bold title (when non-blank) over a body
(when non-blank), then a full-width "Done" button. There is no skip, no pager and no network
access; the old multi-page tour and the GitHub release-notes fetch (section 14) are not used
here.

"Done" writes `tutorial_completed` = true, `last_seen_whats_new_version` = current version,
then (unless the preview extra `brobata.physiboard.PREVIEW_UPDATE_TUTORIAL` is true) writes the
same version key once more, and opens the home screen. Nothing in the app sends the preview
extra or `brobata.physiboard.PREVIOUS_VERSION`; and because the first write already stamps the
version, a preview would still suppress the note for that version.

## 6. Home screen

### 6.1 Layout

Top to bottom: the terminal header (section 22.1), then one scrolling list 16 dp in from each
side: exactly one status card (section 6.3), the Titan toolbox pane (section 6.4a), the
settings search field (settings-catalog.md section 8, drawn as the `$ search settings_` prompt),
then the category index (section 6.4) on four panes. The toolbox pane is hidden while a query is
typed. While a query is typed the
results replace the index; an empty result shows the empty state "No settings match “<query>”.
Try a shorter word." A translucent overlay (black at 30 % in dark theme, white at 20 % in light)
covers the status-bar area.

### 6.2 What the home probes, and how often

On creation and then every 2000 ms while visible: the enabled/selected probe (section 8.2) and
a count of distinct languages among PhysiBoard's enabled subtypes (computed but not shown
anywhere in 2.x). The verified broker status is observed as in section 1 and refreshed when
wireless debugging flips, polled every 1500 ms.

### 6.3 The status card

| Condition | Card title | Subtitle | Tap |
|---|---|---|---|
| not enabled | "Enable PhysiBoard" | "Turn it on in system keyboard settings" | opens `android.settings.INPUT_METHOD_SETTINGS` |
| enabled, not selected | "Set as keyboard" | "Pick PhysiBoard from the input switcher" | shows the input-method picker |
| an update was found on this open (section 13.4) | "Update available" | "Version X is ready to install" | reopens the update dialog for that version |
| otherwise | "Ready to type" | "PhysiBoard is your keyboard", plus " · Titan tools <problem>" when the verified broker status is known and not `OK` | opens Status |

The first matching row wins. The first three are action panes with the 1 dp amber border: a
40 dp keycap with the icon, the title in mono in the accent, the subtitle, a chevron. "Ready to
type" is a calm pane with the slate border and a green check, so on a phone that is set up the
toolbox below is the one amber pane on the page.

**Accessibility service is off** (3.2). Under whichever of the four is shown, after a 1 dp rule
and above the spell-checking line, a warning line (the amber warning icon, "Accessibility service
is off" / "Needed for Fn shortcuts everywhere and focusing the text box") opens Keys & shortcuts ›
Accessibility service, which explains the service and how to turn it on (restricted settings, or
"Turn on with pairing"). It shows while PhysiBoard's accessibility service is not enabled and at
least one of `accessibility_focus_field` / `accessibility_fn_shortcuts` is on, re-checked every
2 s with the rest of the card, so it disappears as soon as the service is on (maintainer,
2026-10-09: "like a warning that something isn't set up right; other apps do this").

**Turn on spell checking** (3.1). Under whichever of the four is shown, after a 1 dp rule, a
second line "Turn on spell checking" / "Pick PhysiBoard so apps underline misspellings" opens
Android's spell checker screen (the same route as Autocorrect & words > "System spell checker").
It shows only while no pairing key is stored (paired, the setup pass does this itself,
broker-privileged-toolbox.md section 7 step 5), `auto_select_spell_checker` is on, and the same
rules would select PhysiBoard: spell checking is off while PhysiBoard is selected, or the
selected spell checker is none, or is one that came with the phone. A spell checker someone
installed (another keyboard's, or the other PhysiBoard build's) is their choice and is never
nagged about. Re-checked with the 2000 ms probe.

### 6.4 The category index

Four panes of category rows (settings-catalog.md section 9.0 lists every row, its summary and
what it opens): Typing, Autocorrect & words, Languages & layouts; Long press & accents, Sym
pages, Voice, Keys & shortcuts, Apps; Look & feel, Privacy; Backup & restore, Help, About.
Titan tools is the toolbox pane above (section 6.4a). A row is at least 64 dp tall: a 40 dp
keycap outlined and washed in the category's own hue (each glyph at least 3:1 on its tile), the
name (title medium, mono), one line of summary (body medium, ellipsised) and a chevron. The
summary is computed from the stored settings when the row draws, so it is never stale and
nothing loads. The broker problem the old Titan tools row named in its summary is now the
toolbox's `adb` line; the status card's "Ready to type" subtitle still appends " · Titan tools
<problem>" when the verified status is known and not `OK`.

### 6.4a The Titan toolbox

The phone-level tools are what no other keyboard has, so since 3.1 they are featured directly
under the status card rather than listed as one index row among fourteen. The pane has the
1 dp amber border (the one featured pane on a set-up phone); tapping anywhere on it opens Titan
tools, so it is the index's entry for that category and Titan tools is not repeated as a row.

- Header: a 40 dp amber keycap with the toolbox icon, "Titan toolbox" (mono, accent), "The phone
  itself, tuned for a keyboard", a chevron.
- A status listing, one `key  value` line each, mono, the key muted in a 96 dp column and the
  value as output: `adb` (`paired ✓` in the accent when a key is stored and the verified status is
  `OK`; `checking…` muted while unknown; `paired · debugging off`, `paired · unreachable`,
  `pairing refused` or `not paired` in the error colour). With a key stored, three more:
  `backlight` (`lit in the dark` when `smart_backlight_enabled`, else `stock · 30 s`), `ring`
  (`on · N min` from `notification_ring_minutes`, or `off`) and `density` (`N dpi · stock` or
  `N dpi · custom`, read once through the broker when the status turns `OK`; `reading…` until
  then, `needs adb` while the broker is not verified, `unreadable` if the read fails). The
  density is never asked for an unverified broker, so an unreachable one cannot hold the page on
  a ten second timeout.
- Paired: three square chips `backlight`, `ring`, `density` that open Smart keyboard backlight,
  Notification ring and Screen density.
- Not paired: one line on why ("Pair once to keep the keys lit in the dark, glow a ring for
  notifications and fit more on screen. It survives reboots.") and the primary button "Pair
  Titan tools", which opens Titan tools, where the device setup card runs the pairing flow
  (broker-privileged-toolbox.md section 3, 4).

Each listing line is one accessibility node, read as its key with its value as the state. The
density is read by Home, not the pane, so scrolling the pane away or typing a search does not
ask the broker again.

### 6.5 Automatic update check on open

If GitHub checks are allowed, every creation of the home screen runs one update check with
"ignore dismissed releases" = true (section 13). A found update both shows the dialog at once
and leaves the "Update available" card on the page. A failed or negative check shows nothing.

## 7. One-time notices on the home screen

Both are modal dialogs drawn over the home; both may appear on the same launch, migration
first.

**Migration notice** ("You will need to enable PhysiBoard again"). Shown while the 1.x
preferences file `pastiera_prefs` has any content and `v2_migration_notice_seen` is not true in
`physiboard_prefs`. Body: the update renamed the app's internals, Android sees the keyboard as
new and switched away; content settings came across, on/off settings were reset. Buttons:
"Got it" (writes the seen flag, closes) and "Restore my old settings" (replays the entire 1.x
file into the current one, dropped and renamed keys aside, then swaps the body for "Your
previous settings are back." and hides the restore button). Dismissing by tapping outside
counts as "Got it".

**Untested device notice** ("Untested on this phone"). Shown when the device is a Titan 2 that
is not an Elite (D2) and `untested_device_notice_seen` is not true. Body: built and tested on
the Titan 2 Elite, a Titan 2 shares the keyboard so most things should work, none of it is
verified, not supported, bugs may not be fixable. One button, "Got it"; dismissing outside also
marks it seen.

## 8. Status screen and the IME probe

### 8.1 Screen

Reached from the home "Status" tile or the settings row "Status" ("Check PhysiBoard is set up
correctly"). Five rows separated by dividers, re-read every time the screen returns to the
foreground (it is changed from Android's own settings, so a value computed once would report
the state the user just left):

| Row | Value | Colour | Tap |
|---|---|---|---|
| "PhysiBoard enabled" / "Enabled as an input method" | Yes / No | amber when yes, error red when no | when No: opens `android.settings.INPUT_METHOD_SETTINGS` (new task) |
| "Active keyboard" / "Currently the active input method" | Yes / No | same | when No: the picker if enabled, else the input-method settings (the picker cannot select a keyboard that is not enabled) |
| "Input language" | the current subtype's language tag rendered as its own display name, first letter title-cased (system locale when the subtype has no tag); "Unknown" on any failure | neutral | none |
| "Smart backlight" | On / Off from `smart_backlight_enabled` | neutral | none |
| "App version" | version name | neutral | none |

Rows with a tap action show a chevron.

### 8.2 The enabled/selected probe

The source carries four copies of this check (home, setup screen, the dead tutorial tour, and
a two-value variant on the Status screen). They behave the same; this is the one behavior:

1. Ask the input-method manager for the enabled input-method list. Enabled is true when any
   entry has PhysiBoard's package name or an id equal to PhysiBoard's IME id in either long or
   short form. If not enabled, selected is false and the probe ends.
2. Read the secure setting `default_input_method`. Selected is true when it equals the IME id
   in either form.
3. If reading that setting throws a security error (Android 14 and later can refuse it): the
   Status screen variant reports not selected; the shared variant infers "selected" only when
   there is a current subtype, PhysiBoard is in the full input-method list, and exactly one
   input method is enabled on the phone.
4. Any other failure anywhere yields not enabled, not selected.

The package-name comparison in step 1 is why the receiver of section 17 compares the whole
component instead: after the 2.0 rename the stale 1.x component shares the package name and
would read as "still selected" under a package-only test.

## 9. Settings entry points the shell owns

These rows live on the settings list; their placement is the catalog's business, their
behavior is here.

- **About** ("Version, licence, and credits"): section 15.
- **Updates** ("Check the latest release on GitHub."), shown only when GitHub checks are
  allowed. While a check runs the title reads "Checking for updates…" and the row is disabled.
  It runs the check with "ignore dismissed releases" = false, so a release dismissed with
  "Later" is offered again here. Outcome: no version returned (network or parse failure, or no
  eligible release): toast "Unable to reach GitHub."; newer: the update dialog; otherwise toast
  "App is up to date."
- **Diagnostics** ("Physical key-event logger and debug export"): section 10. The GitHub bug
  template still tells reporters to find it under "Settings → Advanced → Diagnostics → copy";
  "Advanced" was removed in 2.0.0 and the row is on the list itself.
- **Extras**: a hub of three rows, Quick launcher, Input languages ("Custom input styles"), and
  Text expansion, each opening its own screen (expansion/launcher and dictionaries documents).
  Until 2.0.0 the home Extras tile opened the top-level settings; the hub was created then.
- The settings screen itself also runs one automatic update check (ignore dismissed = true)
  each time it is created, exactly like the home (6.5), dialog only, no card.

## 10. Diagnostics screen

### 10.1 Layout

Top bar with back arrow, bug icon and "Diagnostics". Then, scrolling:

1. A one- to two-line text field with sentence capitalization, placeholder "Type here with the
   physical keyboard…". Its purpose is to give the keyboard a field so key events flow.
2. Buttons that wrap across the width: "Record" (becomes "Stop" while recording), "Clear",
   "View", "Share".
3. Two monospace lines: "Recorder: recording|stopped · Events: N" and "Started at:
   <timestamp>" (or "n/a").
4. Three toggle chips, all off by default and remembered across rotation but not across
   leaving the screen: "incl. suggestions", "incl. raw trackpad", "incl. autocorrections".
5. The "Last Keyboard Event" panel with an "Ignore BACK" chip (on by default).
6. When "View" was pressed: a full-screen "Debug Report Viewer" dialog (10.5).

### 10.2 Where the events come from

While the screen is open it registers as the one listener for key events reported by the
keyboard service. Events are reported by the service itself (origin `ime_service`), the key
router (`ime_router`), the strip's decor (`ime_decor`), and the bounce and accidental-press
filters (`bounce_keys`, `accidental_keys`). The home screen's own activity also reports events
it receives directly (origin `activity`), filtering out unmodified DPAD, TAB, PAGE_UP,
PAGE_DOWN and ESCAPE because those are the service's own output; but the home no longer has a
panel to show them, and the Diagnostics screen lives in the settings activity, which does not
report, so in 2.x every event seen on this screen comes through the keyboard service. Nothing is
recorded while the screen is not open; leaving it unregisters.

### 10.3 Last keyboard event panel

Two columns of monospace values for the displayed event, "n/a" when absent:

- left: key name (the Android key-code name for letters, space, enter, delete, back, the
  DPADs, TAB, MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN, ESCAPE, FORWARD_DEL; keycode 63 is
  named `KEYCODE_SYM`; anything else is `KEYCODE_<number>`), "Action: " KEY_DOWN or KEY_UP (or
  `GESTURE_<phase>` for synthetic trackpad reports), "KeyCode: " number, "Origin: ", "Layout: "
  the resolved layout name;
- right: "ScanCode: " number, "Unicode: raw=<n>('c') effective=<n>('c')" where 0 prints as
  `0(n/a)` and control characters print as `\uXXXX`, "Output: " the output key name and code in
  parentheses when the keyboard translated the key into another one (drawn in amber).

Below, chips "Shift", "Ctrl", "Alt" for whichever modifiers the event carries.

"Ignore BACK" (default on): a BACK key event does not replace the panel; the last non-BACK event
stays visible. This exists because closing the keyboard or navigating emits BACK and would
wipe the event the user wanted to read. Recording is not affected by the chip: BACK events are
recorded like any other.

### 10.4 Recording

"Record" clears the list, stamps the start time (wall clock) and starts. Every reported event is
appended with a wall-clock timestamp derived from the event's uptime timestamp when it has one
(now minus the uptime age), else now, and a delta in ms from the previous recorded event
(clamped at 0). "Stop" stops without clearing. "Clear" empties the recorded list, clears the
capture store (section 11), forgets the start time, stops recording, closes the viewer and
blanks the panel. There is no cap on the number of recorded events.

### 10.5 View, copy, share

"View" builds the report (10.6) and opens it in a full-screen dialog with "Close", "Copy" and
"Share" buttons, the text selectable, each line coloured by shape: `===` headers and `sha256=`
in amber, `[section]` lines in tertiary, `(no ...)`, `n/a` and blank lines muted, lines with
` | ` in the normal text colour, other `key=value` lines in the secondary colour.

"Copy" puts the report on the clipboard under the label `physiboard-keyboard-debug` and toasts
"Copied debug report (N events)".

"Share" sends the report as plain text (`text/plain`, subject "PhysiBoard Keyboard Debug
Export") through the system chooser titled "Share debug report". It switches to sharing a
file instead when any of these holds: the raw-trackpad chip is on, more than 250 events are
recorded, or the report exceeds 500 × 1024 bytes in UTF-8. The file path is
`<cache>/debug-reports/physiboard-keyboard-debug-<yyyyMMdd-HHmmss>.txt`; the directory is
wiped and recreated on every file share, and the file is offered through the app's file
provider (`<applicationId>.fileprovider`, cache path `debug-reports/`) with a read grant.

### 10.6 The report

Plain text, `key=value` lines in bracketed sections, `|`-separated event rows, ending with a
SHA-256 of everything above it. Timestamps are `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` in the device
time zone. In order:

```
=== PhysiBoard Debug Export ===
exported_at, timezone_id, timezone_offset (seconds, suffixed s)

[system]      android_release, android_sdk, android_incremental, android_security_patch
[app]         package, version_name, version_code, build_type, release_channel
[device]      brand, manufacturer, model, device, product, fingerprint, hardware, board,
              bootloader, build_display, build_id, build_tags, build_type, supported_abis
              (comma-joined), sku, odm_sku, soc_manufacturer, soc_model (n/a below Android 12),
              physical_keyboard_name, keyboard_family ("Unihertz" or "unknown"),
              profile_override, resolved_physical_profile
[input_devices] one line per input device that has the keyboard source or a keyboard type:
              id, name, descriptor, vendor_id, product_id, keyboard_type, sources,
              sources_hex, external, virtual; or "(no keyboard-like input devices found)"
[recording]   started_at, event_count, include_suggestions, include_raw_trackpad,
              include_autocorrections, suggestions_filter ("empty_hidden,dedupe_consecutive"
              or "disabled"), attempt_logging_supported=true
[ime_context] captured_at, target_package, input_type, ime_options (hex), ime_no_enter_action,
              resolved_editor_action, subtype_locale, resolved_layout, then the same six
              prefixed external_ for the last field from another app, then
              profile_override_snapshot, resolved_physical_profile_snapshot
[settings_snapshot] every preference key=value sorted by key (string sets sorted and
              bracketed, null as "null"), then five resolved_ lines: mid_word_quote_to_
              apostrophe, french_punctuation_spacing, comma_space, auto_space_punctuation,
              space_after_punctuation
[privileged]  broker_paired, wireless_debugging_enabled, broker_blocker (not_paired,
              wireless_debugging_off or none), backlight_enabled, backlight_applied_flag,
              backlight_device_value ("never read" if never), backlight_device_value_at,
              overlay_permission_granted, notification_listener_granted,
              notification_ring_enabled, screen_trackpad_enabled, trackpad_provider,
              ime_enabled, ime_selected, then last_<step>=ok|failed reason='..' at=<time>
              for each of backlight, overlay_grant, notification_ring, ring_backlight that
              has ever run, or "last_outcomes=(no privileged step has run)"
[autocorrections] "(excluded - contains typed words; enable "Include autocorrections" to
              share)" unless the chip is on; then "(no autocorrections recorded)" or rows
              <time> | type= trigger= source= outcome= before='' after='' reason='' [distance=]
              [kind=]
[suggestions] only with the chip: "(no suggestion snapshots recorded)" or rows
              <time> | cand{source/kind}, cand{...} [xN] where consecutive identical
              snapshots collapse into one row carrying the last timestamp and a repeat count,
              and empty snapshots are dropped
[raw_trackpad] only with the chip: rows <time> | provider= origin= phase= action= outcome=
              start=(x,y) xy=(x,y) delta=(dx,dy) threshold= deviceId= source= sourceHex=
              eventUptimeMs=
[events]      "(no recorded events)" or one row per recorded event:
              <time> | start|+<delta>ms | KEY_DOWN|KEY_UP | origin= key=NAME(code) scan=
              deviceId= source= flags= repeat= meta=<n>(0x..) unicode_raw= unicode_effective=
              alt= shift= ctrl= altLatch= altOneShot= shiftLatch= ctrlLatch= symPage= layout=
              eventUptimeMs= sourceHex= flagsHex= [output=NAME(code)]

sha256=<hex of everything above, including the trailing newline after the last event row>
```

The `[settings_snapshot]` block includes every key in the preferences file, so it carries the
user's substitutions, expansion snippets and personal dictionary keys. The autocorrection log
is the only block gated for privacy.

### 10.7 The rule about the last text field

The keyboard records a context snapshot every time it attaches to a field: package, input type,
IME options, the resolved editor action, subtype locale, resolved layout and the physical
profile override. Two slots exist: "last field" and "last field from another app". The second is
updated only when the field's package is not PhysiBoard's own. Opening Diagnostics focuses its
own text field, which overwrites the first slot with PhysiBoard itself; the `external_` lines
of the report are what still describe the app being reported. Evidence: change record 2.0.4,
"A bug report describes the app you were using."

## 11. The debug capture store

An in-memory, process-wide store with fixed capacities, cleared only by "Clear" on the
Diagnostics screen or by the process dying:

| Buffer | Capacity | Dropped | Recorded by |
|---|---|---|---|
| autocorrection events | 100, oldest dropped | attempts with outcome not-applicable, reason `auto_replace_disabled`, blank before and blank after (pure noise) | the autocorrect decision (autocorrect document) |
| suggestion snapshots | 50, oldest dropped | nothing at record time; empties and consecutive duplicates are filtered at export | every suggestion-strip update |
| raw trackpad events | 200, oldest dropped | nothing | the trackpad (trackpad document) |
| IME context, last field | 1 | replaced on every attach | the keyboard service |
| IME context, last field from another app | 1 | replaced on every attach to a non-PhysiBoard field | same |

Autocorrection rows carry type (`commit` or `attempt`), trigger (`space`, `enter`,
`suggestion_tap`, `other`), a source string, outcome (`applied`, `skipped`,
`not_applicable`), before, after, reason, distance and kind. Recording happens regardless of
whether the Diagnostics screen is open; the buffers fill from the moment the keyboard runs.

## 12. The performance logger

A helper that measures elapsed time from a mark and logs "<label> took <n>ms <details>" at
debug level when the duration is at or above a threshold, 16 ms by default. It is a no-op in
every non-debug build, and the release build additionally strips debug logging (section 23.5),
so on a shipped phone it never produces output.

## 13. The update checker

### 13.1 When a check runs

| Trigger | Ignores "Later"-dismissed releases | Shows |
|---|---|---|
| home screen created (6.5) | yes | dialog, plus the "Update available" card |
| settings screen created (9) | yes | dialog |
| "Updates" row tapped (9) | no | dialog, or a toast |
| background job, every 24 h (13.7) | yes | notification |

Every trigger first checks "GitHub checks allowed"; when not allowed the check reports "no
update" without touching the network.

3.0: every trigger also goes through the network gate (section 31.2). In private mode nothing is
sent: the automatic triggers and the daily job find "no update" (the job completes, it does not
retry), and the "Updates" row shows the toast "Private mode is on, so PhysiBoard makes no
network requests."

### 13.2 The request

One request: `GET https://api.github.com/repos/brobata/physiboard/releases?per_page=20` with
header `Accept: application/vnd.github+json`, using default HTTP client timeouts (10 s connect,
10 s read). The result callback is always delivered on the main thread.

### 13.3 Resolving the release

The JSON array is read in order. Each element contributes a release when it has a non-blank
`tag_name`; `prerelease` and `draft` booleans, `html_url`, and its `assets` (each asset's
`name` and `browser_download_url`) are kept. The chosen release is the first one that is
neither a draft nor a pre-release. Pre-releases are therefore never offered by the app; a user
on a pre-release is compared against the newest stable one. The download URL is the
`browser_download_url` of the first asset whose lower-cased name ends in `.apk`, or none. The
release page URL is `html_url` when non-blank.

### 13.4 Comparing versions

Both the tag and the installed version name are normalized by removing one leading `v` or `V`.
Then each is cut at the first `-` and `+`, split on `.`, and each part reduced to its leading
digits; parts with no digits are dropped. If either side has no numeric parts the comparison is
plain string inequality. Otherwise parts are compared left to right with missing parts as 0;
the first difference decides. "Newer" is strict: equal is not an update, and a local build ahead
of the newest release is not an update. Examples: 1.0.2 > 1.0.1; 1.1 > 1.0.9; 1.0 = 1.0.0;
1.0.2 > 1.0.1-dev; 1.0.1 = 1.0.1-dev; "beta" vs "alpha" is an update, "beta" vs "beta" is not.

### 13.5 Dismissal

When the trigger ignores dismissed releases and the tag (as written, with its `v`) is in the
comma-separated string `dismissed_releases`, the check reports "no update". The set only grows;
nothing removes a tag.

### 13.6 The dialog

Title "New update available", message "Version <tag> is available on GitHub." (the tag is
shown as-is, `v` included). Buttons: "Open GitHub" opens the release page URL, or
`https://github.com/brobata/physiboard/releases` when none, in a browser (new task); "Later"
adds the tag to `dismissed_releases`; "Download APK", present only when an APK asset was found,
opens the asset URL in a browser (new task). The app never downloads or installs the APK itself;
the browser and the system installer do it. Dismissing the dialog by tapping outside or Back
records nothing, so it will appear again on the next open.

### 13.7 The background job

When the home screen is created and GitHub checks are allowed, a periodic job named
`pastiera_update_check` is enqueued with a 24-hour period, a "connected network" constraint,
and keep-if-existing policy (so the period is not reset by every launch). When checks are not
allowed the job is cancelled instead. The job runs the same check (ignore dismissed = true)
and blocks for up to 30 s waiting for the result; no result within 30 s asks the scheduler to
retry with its default backoff. A result without an update completes silently. A result with
an update posts the notification below when the notification permission is granted, and logs
a warning and does nothing otherwise. There is no throttling beyond the 24-hour period: while
the user leaves an update uninstalled and undismissed, the notification is re-posted every day
(same id, so it replaces itself).

### 13.8 The update notification

Channel `pastiera_update_channel`, name "PhysiBoard Updates", description "Notifications about
new PhysiBoard versions", default importance, badge on, no light, vibration pattern 0/50 ms, no
sound. Notification id 2, title "PhysiBoard - New update available", text "A new version of
PhysiBoard is available (<tag>)", the mark (`pb_ic_mark`, SS22.6) as small icon, default priority,
category status, public visibility, auto-cancel. Tapping opens, in a browser with a new cleared
task, the APK asset URL when there is one, else the release page, else the releases list. It
does not open the app and does not mark the release dismissed.

### 13.9 Failure handling

A network failure, a non-2xx status, an empty body, a malformed array, no eligible release, or
a dismissed release all report "no update" with no version, download or page. Only the
"Updates" row distinguishes "no version" (toast "Unable to reach GitHub.") from "up to date".
Malformed JSON is caught (change record 2.0.1: "The update check cannot crash the app").

## 14. The release-notes fetcher

A second GitHub client exists for the old multi-page tour's "What's new" page: `GET
https://api.github.com/repos/brobata/physiboard/releases/tags/v<version>` with the same Accept
header. A 404 (tag without a release) or any failure yields nothing. A success is accepted only
when the response's `tag_name`, normalized, equals the requested version; the highlights are
the first 8 body lines that, trimmed, start with `- ` or `* `, with the marker and every `**`
removed and blanks dropped; a response with no such lines yields nothing. The title is the
release `name` or "PhysiBoard <version>"; the docs URL is `html_url` if it starts with
`https://github.com/`, else the releases list. The language tag passed in only selects the
offline fallback sentence (German, Italian, or English: "The full notes for this release are on
the GitHub releases page."). In 2.x nothing reachable calls this fetcher (section 21); it is
documented because the version-matching rule is a data contract worth keeping.

## 15. About screen

Top bar "About". A scrolling column of cards:

1. **Build info card**: the fixed line "PhysiBoard IME - Palsoftware 2026", then
   "Ver. <version> - Physi" (the release-channel string `physi` with its first letter
   upper-cased), then "Device: <brand> <model>; Keyboard: Unihertz|unknown".
2. **"Report a problem"** ("Opens a bug report with your version, phone and firmware already
   filled in. You write what happened."), section 15.1.
3. **"App Language"** with the current choice as description ("System default" or the
   language's own display name), opening the app-language screen (section 25).
4. **"Show Tutorial"** ("Review the introductory tutorial"): resets `tutorial_completed` and
   opens the setup screen (section 3).
5. The credits, rendered from the asset `common/about_credits.md` (section 15.2); a spinner
   while loading, "Unable to load credits information." in error colour if the asset cannot be
   read.

### 15.1 Report a problem

Opens, in a browser (new task), the URL
`https://github.com/brobata/physiboard/issues/new` with query parameters:

- `template=bug.yml`
- `app_version=<version name>`
- `device=` one of "Unihertz Titan 2 Elite", "Unihertz Titan 2 (untested/unsupported)",
  "Something else", chosen from the device detection of section 27
- `diagnostics=` three lines: `android=<release> (sdk <n>)`, `build=<Build.DISPLAY>`,
  `model=<manufacturer> <model>`

The template's fields are: "What happened" (required), "PhysiBoard version" (required, prefilled),
"Phone" dropdown with exactly those three options (required, prefilled), "Which app were you
typing in?", and "Diagnostics" (a text block, prefilled with the three lines; the template
invites pasting the Diagnostics export there). The dropdown labels in the app and in the
template must stay byte-identical for the prefill to land. A companion "idea" template exists
for feature requests, and the issue chooser links to r/unihertz.

### 15.2 The credits markdown dialect

A tiny renderer with these block rules, one per line, leading and trailing whitespace ignored:
`# `, `## `, `### `, `#### ` headings (24, 20, 18, 16 sp bold); `---`, `***`, `___` a divider;
`- ` or `* ` a bulleted item; `![alt](path)` an image loaded from assets by that path, falling
back to "[Image: alt]" in italic error colour; `{{button:Label|https://url}}` a centered
outlined monospace button opening the URL; anything else a paragraph, consecutive lines joined
with a space until a blank line or another block. Inline: `[text](url)` becomes an underlined
amber link opened by tapping that span; `**b**` and `__b__` bold; `*i*` and `_i_` italic;
`` `code` `` monospace on a variant background. The shipped credits name the upstream project
and its author, contributors and beta testers, a Ko-fi button for the upstream author, the
upstream links, and the open-source notices (AOSP LatinIME at a pinned commit, Material
Symbols, JetBrains Mono, three CC0 typing-sound packs, Unicode CLDR, Leipzig corpora, the
eellak Greek dictionary). Nothing in About links to this fork's own repository except the bug
report row.

## 16. Notifications

| Channel id | Name | Importance | Id | Posted by | Content and tap |
|---|---|---|---|---|---|
| `pastiera_update_channel` | "PhysiBoard Updates" | default, badge, vibrate 0/50, silent | 2 | the daily job (13.8) | update available; opens the APK or release page in a browser |
| `physiboard_reselect_channel` | "Setup needed" | high, badge | 3 | package-replaced receiver (17) | "Pick PhysiBoard again"; opens the home screen |
| `pastiera_nav_mode_channel` | "PhysiBoard Fn Layer" | default, no badge, no light, vibrate 0/50, silent | 1 | nobody in 2.x; the Fn layer only vibrates 70 ms and cancels id 1 defensively | none |
| `physiboard_notification_ring` | ring channel | high | 41 | the notification ring (ring document) | full-screen intent, alarm category |
| `adb_pairing` | pairing channel | high, silent, no badge, no bubbles | vendored service's own | the pairing service while armed (4.8) | shows the pairing-code entry |

All app-posted notifications use the mark, the single-colour keycap with the `>_` prompt cut
into its face (`pb_ic_mark`, SS22.6; change record 2.0.2 introduced the keycap), as small icon,
except the notification ring's, which is the ring icon (`pb_ic_ring`, a dot inside a ring). The nav-mode channel is deleted and recreated whenever its creation runs, which
nothing does in 2.x; the update channel is created (idempotently) right before each update
notification.

## 17. The package-replaced receiver

Declared in the manifest for `android.intent.action.MY_PACKAGE_REPLACED` only (exempt from
implicit-broadcast limits). On receipt it reads `default_input_method`; if the part before `/`
equals the app's package and the part after equals the fully qualified service class name, the
app is still the keyboard and nothing happens. Otherwise it creates the channel
`physiboard_reselect_channel` if missing and posts notification 3: title "Pick PhysiBoard
again", big-text body "This update renamed part of PhysiBoard, and Android treats a renamed
keyboard as a new one — so it switched you to a different keyboard. Tap here to set PhysiBoard
back. Your settings are all still here.", high priority (heads-up), status category, public,
auto-cancel; tapping opens the home screen (new task, clear top), where the "Enable" and "Set as
keyboard" cards take over. A missing notification permission makes the post throw; the receiver
logs at error level and gives up, because a receiver cannot ask for permissions; the 2.0.0
release note is the documented backstop.

Why it exists (D4): 2.0 moved the source namespace, the IME service is declared by a relative
name, so its component name changed from
`brobata.physiboard/it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService`
to `brobata.physiboard/brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService`, and
Android silently fell back to another keyboard. Declaring the old component too would have
put a second "PhysiBoard" in the picker forever. Every upgrade after 2.0 keeps the selection,
so the receiver stays silent.

## 18. Feature-status markers

Two markers can sit on a settings row: **Construction** (a hammer-and-wrench icon; tapping
opens a popup "This feature is work in progress or planned. It may be incomplete, unavailable,
or still change.") and **Experimental** (a flask; "This may already work, but it is new and may
soon be changed or improved. Bug reports are welcome."). The popup is at most 300 dp wide and
closes on outside tap. In 2.x only Construction is used, on the SYM customization screen and
the device SYM-layer editor stub; the hardware-keyboard settings rows accept a marker but none
is passed.

## 19. Broadcast actions and intent contracts

| String | Kind | Meaning |
|---|---|---|
| `brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED` | app-internal broadcast | the personal dictionary changed (dictionaries document) |
| `brobata.physiboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE` | exported activity action | flip the temporary on-screen keyboard mode; ignores every extra; used by the dynamic shortcut and automation apps |
| `brobata.physiboard.SETTINGS_DESTINATION` | string extra on the settings activity | one of `customization`, `device_sym_layer_editor`, `modifiers`, `smart_backlight`, `input_languages`, `keyboard_theme_destination`, `smart_features_destination`, `auto_correct_destination`, `voice_destination`, `raw_mode_destination`, `fn_layer_destination`, `screen_trackpad_destination`, `toolbox_destination`, `remove_bloat_destination`, `keyboard_hub_destination`, `extras_destination`, `status_destination` |
| `brobata.physiboard.CUSTOMIZATION_DESTINATION` | string extra | `variations`, `launcher_shortcuts`, `app_enter_behavior`, `status_bar_buttons`, `keyboard_theme`, `sounds` |
| `brobata.physiboard.KEYBOARD_THEME_TARGET` | string extra | `software` |
| `brobata.physiboard.UPDATE_TUTORIAL` | boolean extra on the setup activity | show the what's-new note instead of setup |
| `brobata.physiboard.PREVIEW_UPDATE_TUTORIAL`, `brobata.physiboard.PREVIOUS_VERSION` | extras on the setup activity | read, never sent (5.3) |
| `android.intent.action.MY_PACKAGE_REPLACED` | manifest receiver | section 17 |
| `android.view.InputMethod` | service filter | the IME; settings activity declared in `res/xml/method.xml`, next-IME switching supported, subtypes en_US, it_IT, fr_FR, de_DE, pl_PL, da_DK, no_NO and more, each with `noSuggestions=true` |
| `android.settings.ADB_WIRELESS_SETTINGS` | fired, with fallback | 4.8 |

The settings activity is exported because Android's own settings launch it as the IME's
settings screen; its only inputs are the extras above, matched against those constants.

## 20. The IME test screen

A screen with every Android field type, for exercising the keyboard: a "Text Input Types"
section (plain, all-caps, title-case, sentence-case, email, password, visible password, web
password, URI, person name, postal address, multi-line up to 5 lines, no-suggestions,
auto-complete, auto-correct), "Numeric Input Types" (number, signed, decimal), "Other Input
Types" (phone, datetime, date, time), and "IME Actions" (none, go, search, send, next, done,
previous, unspecified; three lines each). The three capitalization fields are real Android
edit-text widgets with the corresponding input-type flags, the rest are Compose fields. Its
title and every label are hard-coded English. Nothing in 2.x navigates to it; it is
unreachable dead code kept from upstream.

## 21. Dead code inventory

Because the clean-room rewrite must not port what nobody can reach, these are the shell parts
that exist in the source but have no path from any screen:

- **The multi-page tutorial tour** (welcome, enable, select, customization, quick launcher,
  messenger presets, Android 16 IME caption bar, software keyboard mode, nav mode, LED
  indicator, feature statuses, ready, plus a "What's new" first page fed by section 14). The
  setup activity always shows the two-step screen instead. It carried one Titan fact worth
  keeping (D7) and an automatic update check like 6.5.
- **"Dev's choice" settings** (app language system default, profile override `auto`, software
  keyboard mode `auto`, long-press modifier `variations` at 200 ms, trackpad gestures on,
  layout auto-by-locale off, layout list `qwertz`, variations loaded from assets), only called
  by a unit test.
- **A legacy view-based settings activity** with a "check for updates" button (ignore
  dismissed = false, toast "App is up to date." when nothing newer), not declared in the
  manifest.
- **The IME test screen** (section 20).
- **The nav-mode notification** and its "N" bitmap icon (section 16).
- **The old enabled-language count** on the home (6.2).

## 22. Theme and chrome

### 22.1 Visual identity: the terminal skin

Since 3.1 the settings app keeps the modern structure (category index, detail screens,
summaries, collapsing titles, panes, 48 dp targets) and wears it as a terminal: JetBrains Mono
is the voice of the chrome, Inter is kept only for the sentences a person reads through.

The design system this skin belongs to, shared with the keyboard's panels, the launcher icon and
the splash, is written down in `docs/design/design-system.md` (colour, type, spacing, corners,
borders, icons, motion, haptics, copy). Its numbers live in one place, the `:design` module's
`DesignTokens` (pure Kotlin, contrast pairs pinned by a test), with the two typefaces and the
icon family beside it; the settings theme below reads its palette, spacing and corners from
there. Where this section and that document differ, this section describes the settings app and
the document the system.

**Palette.** Every activity uses one theme: Material 3 with dynamic colour disabled, dark or
light following the system. Ink `#0F172A` (dark page), Pane `#111B2E` (dark pane fill, the
`surfaceContainer`; one step above the page so the border carries the edge), Slate `#1E293B`
(dark `surface`), Signal Amber `#F59E0B` (primary and tertiary on dark, `onPrimary` Ink), Amber
Deep `#B45309` (primary and tertiary on light, `onPrimary` white: Signal Amber is about 2:1 on
white), Sky `#38BDF8` (secondary), Cloud `#F1F5F9` (light page; panes are white), Slate400
`#94A3B8` (muted text and outline on dark), Slate550 `#5B6B80` (muted text and outline on light:
Slate500 measured 4.3:1 on Cloud), pane border `#2A3A52` dark / `#CBD5E1` light
(`outlineVariant`, decoration only), comment `#B8925A` dark / `#7A5C2E` light (the section
labels), error `#EF4444` dark / `#DC2626` light. The tonal container (`secondaryContainer`) stays
amber, `#78350F` with `#FDE68A` on dark and `#FEF3C7` with `#78350F` on light; it fills slider
tracks. Every text colour meets WCAG AA (4.5:1) on the surface it sits on in
both themes: muted text 7.0 / 5.0, comments 6.2 / 5.6, the accent 8.3 / 4.6 (Cloud) and 5.0
(white), selected-chip text 8.3 / 5.0. The window theme is no-action-bar Material with status and
navigation bars in the splash colours (dark or light variant), and edge-to-edge is enabled on
every activity.

**Typography.** Two families, both vendored under `res/font` under the SIL Open Font License 1.1
(LICENSING.md, credited on About under "Fonts"):

- **JetBrains Mono** (regular, medium, bold): screen titles as prompts, section labels, row
  labels, row values, category names, buttons, chips, text fields, dialog titles, the
  `physiboard:~$` prompts, and Diagnostics' log and report text.
- **Inter** (regular, medium, semi-bold): descriptions, explanations, dialog bodies, intro
  paragraphs, What's new and setup prose.

One Material 3 type scale, defined in the theme; screens use its roles and never a font size of
their own:

| Role | Family | Size / line | Weight | Used for |
|---|---|---|---|---|
| display large/medium/small | Mono | 48/56, 40/48, 32/40 | bold | (reserved) |
| headline large/medium | Mono | 28/36, 24/32 | bold | (reserved) |
| headline small | Mono | 19/26 | bold | dialog title |
| title large | Mono | 18/24 | bold | large in-page titles |
| title medium | Mono | 15/21 | bold | category names, card titles, the collapsed bar's path |
| title small | Mono | 13/18 | bold | expander rows, sub-headings |
| body large | Mono | 14/20 | medium | row labels, list item titles, field text |
| body medium | Inter | 14/20 | regular | descriptions, dialog text |
| body small | Inter | 13/18 | regular | notes and fine print |
| label large/medium/small | Mono | 14/20, 12/16, 11/16 | medium | buttons, chips, badges, key letters |

Brand roles beside the scale: the prompt (Mono bold 18/24), the section label (Mono medium
13/18), the value (Mono medium 13/18, the accent), code (Mono regular 12/17, Diagnostics) and
reading (Inter 16/23, long prose). Glyph cells that show a character as itself (emoji and
character pickers, accent tiles) use one glyph style (Inter 22/28).

**Shapes and spacing.** Terminal corners: 2 dp (extra small: outlined fields), 4 dp (small:
chips, buttons, keycaps, the switch), 6 dp (medium and large: panes, cards, preset cards), 8 dp
(extra large: dialogs). Spacing steps are 4, 8, 12, 16 and 24 dp; the side margin is 16 dp.

**Screen titles as prompts.** Every settings screen shares one top bar: status-bar and cutout
insets, the page colour, a 56 dp row with the back arrow (content description "Back", full
contrast) and the trailing actions in the same colour. Below it the title is a prompt,
`physiboard:~/<dir>$`, where `<dir>` is the title lower-cased with every run of non-letters and
non-digits made one `-` ("Sym pages" is `sym-pages`, "Look & feel" is `look-feel`): `physiboard`
and `$` in the accent, `:` muted, `~/<dir>` in full contrast, up to 20 sp and stepped down to
fit one line (never under 14 sp). As the content scrolls up the prompt folds away (Material's
exit-until-collapsed nested scroll over its 52 dp) and the short path `~/<dir>` fades into the
bar in the accent (title medium), which then takes a 1 dp rule in the pane-border colour along
its bottom edge. The title is one accessibility heading read as the plain title: on the prompt
while the bar is more than half expanded, on the bar's short copy once it is more than half
collapsed (the other copy is hidden then), so a scrolled screen still has its heading. The
scroll-driven fades and the fold are applied in layout and draw, so scrolling never recomposes
the bar.

**Panes.** Every settings list draws its rows on panes: the pane fill, a 1 dp border in the
pane-border colour, 6 dp corners, 16 dp in from each side. A run of rows is one pane; its border
is drawn as one outline across the rows, with no seams between them. A section label between two
panes is a shell comment, `# capitals`: the `#` in the accent, the words lower-cased in the
comment colour (read aloud as the label, marked a heading); the same style names a group inside
a pane. A full-width element (the theme preset carousel, the Sym page preview, the device setup
card, search fields, empty states) sits between panes. Cards that are not lists (the device setup
card, setup, Diagnostics' event card, Remove bloat's presets, theme presets) use the same pane:
surface fill, 1 dp border, 6 dp corners. The featured pane on Home (the Titan toolbox) and the
status card's action panes take the border in the accent instead.

**Rows.** A row is at least 56 dp tall (the 48 dp target plus room). Its label is body large
(mono), its description body medium (Inter, muted). Every row that opens another screen carries
a leading icon in a 36 dp keycap (4 dp corners, a 1 dp border in the pane-border colour, a faint
wash of the glyph's hue, the glyph in the accent; outlined Material icons) and a trailing chevron
in the muted colour; its current value ("On", "Slate Light", "3 apps") sits at its right as
command output, in the value style in the accent. A destructive row (reset, delete) has its
label and icon in the error colour, no chevron, and acts at once with an Undo (22.4); only the
two device-level resets still ask for confirmation. Buttons inside rows
are terminal buttons: the label in the accent inside a 1 dp accent outline, 4 dp corners, at
least 48 dp tall. Long explanations sit behind a collapsed
"About ..." row under a one-line summary (the Terminal-mode pattern), so the controls stay near
the top; expanders open and close on a spring (height and fade) with a turning chevron, and stay open
or closed for as long as their screen is on the back stack (22.5).
An intro paragraph sits above the first pane as plain muted text. An empty list shows an icon in
a 56 dp keycap and one line that says how to fill it.

**Controls.** Switches are rounded pills (amended 2026-10-09: the square terminal switch read as
clunky): a 46 x 26 dp track, fully rounded, with a 20 dp round thumb inset 3 dp and a 1 dp
shadow; on, the track is the accent and the thumb the accent's ink; off, the track is the outline
colour, filled, and the thumb the surface colour (both 3:1 or better); the thumb is thrown on a
spring with a touch of bounce (damping 0.72) while the colours ease over 180 ms, and the tap is
felt (`TOGGLE_ON` / `TOGGLE_OFF`, keys-and-modifiers.md 13.5). A switch row's whole surface toggles it, and a switch that takes taps itself has a
56 x 48 dp target with the switch role. Chips are square-ish (4 dp) with mono labels, and a row
of them that wraps keeps the same 8 dp gap between lines as between chips; a selected chip is highlighted as a
terminal highlights a selection, the accent as the fill and the page ink as the text. Buttons
are 4 dp. Search fields are prompts: an outlined field on the pane fill, a `$` in the accent
where the magnifier was, the placeholder lower-cased in mono ending in `_` (`$ search
settings_`), the border amber while focused, and a clear button once something is typed.

**Dialogs and sheets.** 8 dp corners on the raised surface, the title in mono (headline small),
the body in Inter, buttons in mono.

**Home header.** `physiboard:~$` in the prompt style in the theme's accent (Signal Amber dark,
the deeper amber light), on the page's own background, followed by a 12 x 3 dp accent underscore
cursor (the same `_` the search field ends in) that breathes between full and 25 % opacity over
1.2 s each way; held steady when the system animator duration scale is 0 (reduced motion). A 1 dp
accent rule at 45 % sits under the header, inset 16 dp like the panes. No dark band and no status
bar overlay: the status bar shows the page (amended 2026-10-09: the maintainer found the Ink band
and hard-blinking block out of keeping with the light theme's skin).
Setup, What's new and About keep their own `physiboard:~$ <command>` prompt lines in the page.

### 22.2 Transitions, feel and sizing

Opening a screen slides it in from the right while the screen underneath drifts a fifth of the
way to the left and dims to 60 %; going back reverses both. Finishing the settings activity
slides out to the right (also on Android 14 and later through the newer API).

**Springs (3.2).** Every movement is a spring, not a timed curve, so an interrupted transition
carries its speed into the next one: pages critically damped at stiffness 380 (about 350 ms, no
overshoot), expanders and list rows critically damped at Material's medium-low stiffness, fades
at medium. Rows of a keyed list (the Sym page order, the personal dictionary, the app pickers)
glide to their new place when the list is reordered, and fade in or out when one is added,
deleted or put back by Undo.

**Predictive back (3.2).** The application opts into Android 14's predictive back
(`android:enableOnBackInvokedCallback`). Holding the back swipe on any screen shows the screen
underneath sliding into place under the finger (the navigation host scrubs its pop transition
with the gesture), and letting go either finishes the pop or springs back. A screen with an
inner page (Sym pages and the page being edited; Customize Variations and one letter) pushes and
pops it the same way, and during the back swipe the inner page shrinks to 90 % toward the far
edge and shifts 24 dp with the finger (Material's in-app predictive back); system Back closes the
inner page, not the whole screen.

**Haptics.** The settings app speaks the keyboard's haptic language (keys-and-modifiers.md 13.5)
behind the same switches: switches and check boxes, a different chip or dropdown entry, slider
detents, reorder arrows, the destructive confirm of the two device resets and every undoable
delete or reset, Undo itself, and a folded title pulled all the way back into view (once per
fold).

**Reduced motion.** With the system animator duration scale at 0 every spring lands at its end at
once (Compose scales them by it), the page transitions are not drawn at all, the predictive back
gesture still follows the finger, and the home cursor holds still (22.1). Screens that need the
window's size read it from the window, not the display configuration, so multi-window and the
near-square Titan screen get the bounds the content is actually in.

### 22.3 App language

Every activity wraps its base context with the locale from `app_language_tag` when that key is
non-blank; blank or absent means the system locale. Because it is applied at activity creation,
a language change takes effect when the screen is recreated. Options: system default, then
`en`, `it`, `de`, `es`, `fr`, `pl`, `ru`, `uk`, `vi`, `hy`.

### 22.4 Undo instead of confirm (3.2)

A reset of one section or a delete happens at once, and a snackbar at the bottom of the app,
above the navigation stack, says what happened ("Cleared My page 1", "Deleted “teh”",
"Moved GIFs up") with an Undo action, for 8 seconds; it survives going back to another
screen. Undo puts back exactly the prior state of what the action touched and nothing else: a
change made elsewhere while the snackbar was up is kept. A second action of the same kind while
it is up (three arrow taps on one Sym page) extends it, and Undo returns to where that run of
changes began; any other action replaces it, and the earlier one stands. The snackbar is a raised
pane with the 1 dp border, the message in mono and Undo in the accent; the action plays
`CONFIRM_DESTRUCTIVE` (or `REORDER`, or nothing for a switch that already gave its tick), Undo
plays `UNDO`.

| Action | What Undo restores |
|---|---|
| Punctuation spacing's reset | both character lists |
| Fn layer's "Reset these switches" | the three switches |
| Customize Variations' "Reset every letter to default"; one letter's "Reset to default" | every stored list; that letter's list (or its absence) |
| Sym pages: a page switched off, a page moved | the whole order and every page's switch, as before the run |
| "Clear page" on My page 1 to 3 | that page's keys (its name was never touched) |
| "Reset to Default" on the Emoji or Symbols layer | that layer's custom map |
| Deleting a personal-dictionary word | the word with its count and last use, read back into the current file (a word re-added meanwhile keeps the newer entry); the read and the write happen under the lock every writer of the file takes (dictionaries-languages.md SS7), so Undo never writes over a word saved meanwhile; a default word goes back in its old place |

Only "Reset all settings" and "Reset device settings to stock" (Backup & restore) keep their
confirmation dialog, with the confirm button in the error colour; they reach outside one
section (the whole store, or the phone's own settings through the broker). Clearing the
clipboard history is on the keyboard's Clipboard page, which shows no snackbar; it is not part of
this.

### 22.5 Remembered place (3.2)

Coming back to a settings screen finds it as it was left: its scroll position (and how far its
title had folded), its open expanders, the inner page it was on (22.2) and what was typed into a
search field. Each is saved with the screen's own entry on the back stack, so it survives a trip
to another screen, rotation and the process being reclaimed while the app is in the background;
it is forgotten when the screen is popped. Open expanders are kept per screen by title rather
than in the row, so a row scrolled out of the list keeps its state too. A fresh visit opens every
expander collapsed again.

### 22.6 Identity: the launcher icon, the splash and the small icons

**The mark** is a Titan keycap seen face on, its skirt and its dished face, with a terminal
prompt (`>_`) in Signal Amber on the face: "a physical keyboard that is also a terminal".

- **Launcher icon** (`@mipmap/ic_launcher`, `ic_launcher_round`): an adaptive icon, every layer a
  vector in `:design`. Background `pb_launcher_background`, flat Ink. Foreground
  `pb_launcher_foreground`, the cap (skirt `#334155`, face `#1E293B`) and the prompt in Signal
  Amber (8.3:1 on Ink), inside the 66 dp safe zone so any mask keeps the whole cap. Monochrome
  `pb_launcher_monochrome` (Android 13 themed icons): the mark's silhouette, the cap's bezel with
  the prompt in it, scaled into the safe zone. The round icon is the same layers.
- **The sideload build** (`brobata.physiboard.dev3`) uses `pb_launcher_foreground_dev`: the same
  cap, lifted and at 90 %, over a Sky (`#38BDF8`) pill reading DEV in Ink; its app label and its
  keyboard's name in the input-method picker are "PhysiBoard Dev" (`src/sideload/res`). Its
  themed icon stays the plain mark; the label tells the two apart there. Release and debug keep
  the plain mark and "PhysiBoard".
- **Small icons**, from the same family (24 dp, white, tinted by the system): `pb_ic_mark` (every
  notification, SS16), `pb_ic_ring` (the notification ring), `pb_ic_backlight` (the Keyboard
  light Quick Settings tile: the keycap with light rising off it).

**Splash.** MainActivity starts on `Theme.PhysiBoard.Starting` (AndroidX core-splashscreen on
the platform's Android 12 splash): the foreground mark on an Ink disc, centred on the page colour
(Cloud in light mode, Ink in dark), then `Theme.PhysiBoard`. The splash is held until launch
routing (SS3) has picked the first screen, and never longer than 1500 ms, so the app never
flashes an empty page between the splash and its first screen. The window background before
Compose draws is the page colour too.

### 22.7 The keyboard's panels wear the same skin

Every surface the keyboard draws (the Sym key pages and My pages, the emoji, kaomoji and
symbol picker, the GIF page, the clipboard, the Fill page, the Sym page chooser, the accent and
skin-tone bars, the expansion popup, the quick launcher, the trackpad hint and the caret badge)
uses the design system (`docs/design/design-system.md`, "Panels"); `:ime`'s `PanelSkin` builds
it. The colours stay the user's keyboard theme (status-bar.md SS9.4: Background, Keys, Buttons,
Key outlines, Text and icons, Accent); the skin decides only type, borders, corners, spacing,
icons and motion. Nothing about what a panel holds or does changed (amended 2026-10-09):

- **Type.** JetBrains Mono for every label, key letter, tab, chip, button, field and title;
  Inter for what is read through (a clip's text, status lines, the codes' source lines) and for
  a character shown as itself (Sym page characters, symbol and kaomoji cells, accent tiles).
  Emoji draw in the system's emoji font. The keyboard loads both faces off the main thread when
  it starts, so the first Sym page never waits on the disk.
- **Surfaces.** A panel is the theme's Background with a 1 dp Key-outline rule along its top
  edge. Keycaps and chrome keys are 4 dp with a 1 dp Key-outline stroke; cards, clips, GIF tiles
  and popups 6 dp; fields 2 dp. Pressed, a key or card takes the accent at 25 % over its fill
  and an accent outline, and dips to 94 % before springing back.
- **Titles and fields.** A panel's title is a shell comment, `# clipboard history`: `#` in the
  accent, the words lower-cased in the text colour at 72 %, read aloud as the plain title and
  marked a heading. A search field is a prompt: `$` in the accent, the hint lower-cased ending in
  `_`, a 1 dp outline that turns the accent while hardware keys go into it.
- **Close.** Every panel's close button is the same: the shared close icon (a line X) on a
  36 by 32 dp chrome key, read as "Close". The Sym page's pencil, globe and search keys and the
  picker's search toggle are the shared edit, globe and search icons.
- **Theme-less surfaces** (the expansion popup, the quick launcher sheet, the trackpad hint)
  wear the design scheme for the system's light or dark mode: pane, hairline, accent.
- **Motion.** A panel rises 28 dp into place on a spring (stiffness 600, damping ratio 0.86, no
  visible bounce) while fading in over 140 ms; it closes by dropping 16 dp and fading over
  140 ms, taking no touches from the moment it starts to go. A panel swapped for another in
  place (the Sym key stepping pages, the accent bar reopening, the Fill page redrawing for a new
  response) fades in over 110 ms instead. The expansion popup, redrawn on every highlight move,
  never animates. With the system's animator duration scale at 0 nothing moves at all.
- **Insets.** The Titan's rounded-corner insets (SS4 of status-bar.md, `BottomOverlay`) are
  unchanged: the panel's own background fills the padding they add.

## 23. Build configuration as behavior

### 23.1 Identity

- Application id `brobata.physiboard`; it has always been this and never changes, because it is
  what Android uses to identify the installed app and what upgrades are keyed on. The source
  namespace was `it.palsoftware.pastiera` until 2.0 (D4).
- App and IME labels "PhysiBoard".
- Build-time constants: release channel `physi`, GitHub repository `brobata/physiboard`,
  F-Droid build false, GitHub update checks true. A unit test pins all four and the
  application id (with a `.sideload` suffix tolerated).
- `minSdk` 29, `targetSdk` and `compileSdk` 36, Java 17. The README says "Android 11 (API 30)
  or higher"; the build allows 29.
- Native libraries for `arm64-v8a` only (D3).
- Version code 20007 and version name `2.0.7` as defaults; a CI or release run overrides them
  with the Gradle properties `PHYSIBOARD_VERSION_CODE` and `PHYSIBOARD_VERSION_NAME` (the
  `PASTIERA_` spellings are accepted as fallbacks). The convention is code = major × 10000 +
  minor × 100 + patch.

### 23.2 Build types

| Type | Application id | Label | Shrinking | Logging | Signing |
|---|---|---|---|---|---|
| debug | `brobata.physiboard` | PhysiBoard | none | kept | debug key |
| release | `brobata.physiboard` | PhysiBoard | R8 minify and resource shrink, optimize defaults | every level below error stripped (23.5) | the release key |
| sideload | `brobata.physiboard.dev3` | "PhysiBoard Dev" for app and IME, with the DEV launcher icon (SS22.6) | none (it starts from debug) | kept | the debug key |

In 3.x the sideload type starts from debug and installs as `brobata.physiboard.dev3`, so it
sits beside both the release app and the 2.x test build (the row above, amended 2026-10-09);
the 2.x reasoning follows. The sideload type exists so the release R8 pipeline can be exercised on the maintainer's own
phone without touching the daily driver's data or IME registration: a different application id
installs side by side, appears as a second keyboard in the picker, and keeps logs for
debugging. CI builds it on every push to catch ProGuard breakage before release day.

### 23.3 Signing

The release and sideload types are signed only when all four values are present: store file,
store password, key alias, key password, read first from `release/keystore.properties` (or
`keystore.properties` at the root), then from the environment variables
`PHYSIBOARD_KEYSTORE_PATH`, `PHYSIBOARD_KEYSTORE_PASSWORD`, `PHYSIBOARD_KEY_ALIAS`,
`PHYSIBOARD_KEY_PASSWORD`, then the `PASTIERA_` spellings of the same. A relative store path
resolves against the properties file's directory. Any Gradle invocation whose task names contain
assembleRelease, bundleRelease, packageRelease, publishRelease or installRelease (or no task
names at all) fails at configuration time with an explanatory message when the four values are
missing, unless `-PPHYSIBOARD_FDROID_BUILD=true` is passed, which also leaves the release type
unsigned. Play-only dependency metadata is not included in the APK or bundle.

### 23.4 Assets and packaging

Asset files matching `*_base.json` (the dictionary source word lists) are excluded from every
APK; 2.0.6 shipped 13 of them, 27.4 MB of a 52.6 MB APK, because an earlier exclusion was
placed in the Java-resources filter, which does not apply to assets. Duplicate OSGi metadata
under `META-INF/versions/**/OSGI-INF/**` is excluded. Lint runs against a baseline and fails on
anything new; release lint is off.

### 23.5 Log stripping

The release type applies a rule that treats `isLoggable`, verbose, info, debug and warning
logging calls as side-effect free, so R8 removes them; only error-level logging survives in the
shipped build. Consequences the spec depends on: the settings migration logs its one-line
result at error level so that it survives; the diagnostics export exists because nothing else
does; the sideload type keeps all levels. Line numbers are kept for stack traces, the source
file attribute is renamed. The build-config class, the vendored pairing package (JNI-bound
class name), native-method classes, the hidden-API bypass and BouncyCastle are kept
unminified.

### 23.6 Backup

Auto backup and device transfer include every preferences file except `recent_emojis_prefs.xml`,
the `keyboard_layouts/` directory, `ctrl_key_mappings.json`, `variations.json` and
`user_defaults.json`. The same set is declared for Android 11 and below and for 12 and above.

## 24. The release process as documented

### 24.1 Steps

1. Set `defaultVersionName` and `defaultVersionCode` in the app build file.
2. Add a `## <version> (<yyyy-mm-dd>)` section at the top of `PHYSIBOARD_CHANGES.md` (24.3).
3. Run `scripts/build-release.sh <version> [--publish]`. It refuses to run unless the build
   file declares exactly that version name and the change record has a section for it; then it
   runs unit tests, lint and the release assembly; verifies with apksigner that the APK's
   certificate SHA-256 is `89a050fcb37aa14a16d77c737c70caaebdc4c7f10156f9dc8933adb3499c3261`
   (otherwise installed copies would refuse the update); copies the APK to
   `release/dist/physiboard-<version>.apk` with a `.sha256` next to it; extracts the change
   record section as release notes; and with `--publish` creates the annotated tag
   `v<version>`, pushes main and the tag, and creates the GitHub release with the APK, the
   checksum, title "PhysiBoard <version>", the notes, marked latest.
4. `scripts/verify-release-apk.sh <apk>` re-checks the certificate subject
   `CN=PhysiBoard, O=Brobata, C=US` and the same digest.

The repository memory rule applies: no version bump, tag or publish without an explicit
go-ahead. CI (`.github/workflows/ci.yml`) runs on pushes to main and pull requests: a grep that
fails the build if `it.palsoftware` or `pastiera_prefs` appears as a live identifier outside the
migration file and the workflow (comments exempt), unit tests, lint, and the debug and sideload
assemblies. Fastlane metadata (`fastlane/metadata/android/en-US/`) holds the title, a short
description and a full description for a store listing; no changelogs directory exists.

### 24.2 Requirements stated to users

Android 11 or later, a Unihertz Titan 2 Elite (a plain Titan 2 "will probably work, but is
untested and unsupported"), no other device. Installation: install the APK from Releases, enable
PhysiBoard under Android's on-screen keyboard settings, select it from the input switcher.

### 24.3 The change record's structure

`PHYSIBOARD_CHANGES.md` opens with a preamble naming upstream and the GPLv3 §5(a) duty. Then
sections newest first, each `## <version> (<date>)`, a one- or two-sentence user-facing
summary paragraph, the line `<!-- /card -->`, then `- **Lead-in.** prose` bullets that may wrap
over many lines. Only the part above the marker reaches the in-app card; everything below is the
licence-required record and the release notes on GitHub (the release script takes the whole
section). Sections before 2.0.1 have no marker and would ship whole; the card only ever shows
the current version's section, so this does not matter in practice.

## 25. Translations

Ten locales: English (source) plus `de`, `es`, `fr`, `hy`, `it`, `pl`, `ru`, `uk`, `vi`. All
nine non-English catalogues are machine-translated (commit "i18n: translate the backlight and
diagnostics strings", 2026-08-31: "Machine-translated, like the rest of the non-English
catalogue"), brought to the full string set on 2026-08-29 after having been frozen at about
half. Italian was upstream's original human translation but was regenerated with the rest. The
hard-coded English strings are: every string on the first-run pages (4.2), the IME test screen
(20), the "Debug Export" share subject, the report's section and key names, the terminal
prompts, and the About build-info first line. Release-notes fallback sentences exist in German,
Italian and English only (14). The app-language override (22.3) offers exactly the ten
locales.

## 26. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `tutorial_completed` | boolean | false | whether the launcher icon may open the first-run pages (3, 4.1); in backups, so a restore does not reopen them | written by first-run Skip/Done, by launch routing when the keyboard is already set up (3), and by what's-new Done; never reset in 3.2 (Help "Show the tutorial" opens the pages without clearing it; 3.0 and 3.1 reset it to false there) | none |
| `last_seen_whats_new_version` | string | absent | the version whose what's-new note has been seen; a mismatch with the build shows the note (3); non-blank also counts as setup recorded (4.1) | written by setup and what's-new Done, always together with `tutorial_completed` true; never cleared | none |
| `impact_defaults_applied` | boolean | false | one-shot first-run defaults have been stamped (2, 27) | process start | none |
| `alt_shift_default_initialized` | boolean | false | the Alt+Shift default rule has run (2) | process start | none |
| `untested_device_notice_seen` | boolean | false | suppresses the untested-device dialog (7) | home dialog | "Got it" |
| `v2_migration_notice_seen` | boolean | false | suppresses the migration dialog (7) | home dialog | "Got it" |
| `dismissed_releases` | string, comma-separated tags | absent | releases the automatic checks and the daily job stay silent about (13.5) | update dialog | "Later" |
| `app_language_tag` | string, BCP-47 | absent (system) | the app UI locale (22.3) | About → App Language | "App Language" |
| `smart_backlight_enabled` | boolean | false; first-run defaults set true | shown as On/Off on Status (8.1) | Status (read only here) | "Smart backlight" |
| `privileged_broker_status`, `privileged_broker_status_at` | string, long | absent | the seed for the verified broker status before the first live check (1) | internal | none |
| `privileged_<step>_ok`, `_reason`, `_at` for backlight, overlay_grant, notification_ring, ring_backlight | boolean, string, long | absent | the last outcome of each privileged step, printed in the export (10.6) | internal | none |
| `privileged_backlight_device_value`, `_at` | string, long | absent | the backlight value last read from the device, printed in the export | internal | none |
| `private_mode` (3.0) | boolean | false | private mode: no learning, no network (31) | Privacy; the "Private mode" command | "Private mode" |

Values written once by the first-run defaults (section 2, step 3), for the record:
`auto_capitalize_first_letter` true, `fn_long_press_speech` true, `dictation_haptics` true,
`dictation_mask_offensive` false, `smart_backlight_enabled` true,
`titan2_elite_rounded_corner_insets` true, `use_keyboard_proximity` true,
`alt_shift_layout_switch` true, `alt_ctrl_speech_shortcut` false, `auto_show_keyboard` true,
`emoji_picker_expanded_height` false, `screen_trackpad_enabled` true,
`auto_replace_on_space_enter` true, `side_key_assistant` true, `notification_ring_enabled` true,
`dictation_end_silence_ms` 2000, `status_bar_height_dp` 56, `screen_trackpad_step_px` 32,
`notification_ring_minutes` 2, `status_bar_visibility` "ALWAYS", `modifier_indicator_mode`
"menu_bar", `status_bar_slot_left` "clipboard", `status_bar_slot_right_1` "microphone",
`status_bar_slot_right_2` "none", `physical_keyboard_currency_symbol` "$", `keyboard_layout`
"qwerty", `software_keyboard_mode` "auto", `app_enter_behavior_preset`
"enter_send_shift_newline", `status_bar_slots_left` `["clipboard"]`, `status_bar_slots_right`
`["microphone","none"]`, a `sym_pages_config` JSON (emoji off, symbols on, clipboard off,
emoji picker on, order emoji_picker, symbols, clipboard, emoji), and an
`app_enter_behavior_overrides` JSON with WhatsApp, Discord, Google Messages and Instagram on
"enter_send_shift_newline". They were re-captured from the maintainer's phone on 2026-08-27
(app 1.2.3). The catalog document owns their meaning.

## 27. Titan-specific facts

| Id | Fact | Evidence |
|---|---|---|
| D1 | A Titan 2 Elite is recognized when the lower-cased brand, manufacturer, model, device, product, board or display contains `titan2elite_qwerty`, `titan2elite-qwerty` or `titan2eliteqwerty`; failing that, when any of those fields contains `unihertz` or `titan` and the display contains `elite` or the board contains `g72` (reviewer units expose Titan 2-like model strings but leak Elite traits through board or display). | Device-detection comment in the source; About and bug report use it |
| D2 | A plain Titan 2 is recognized when a field contains `unihertz` or `titan` and one contains `titan 2` or `titan2` without D1 matching; it gets the one-time untested-device notice and "Unihertz Titan 2 (untested/unsupported)" in bug reports. | README "Requirements"; the notice strings |
| D3 | The APK carries native code for arm64 only, because the embedded ADB library is arm64 and so is the Titan; an x86 install would silently lack it. | Build file comment |
| D4 | The 2.0 namespace move changed the IME component name and Android deselected the keyboard on upgrade; hence the receiver (17), the migration dialog (7) and the CI grep against the old identifiers. | Change record 2.0.0; receiver comment; manifest comment |
| D5 | Android turns wireless debugging off across reboots on this ROM, so a paired phone is routinely unable to connect; that is why the home badge and the setup card use a verified connection, not "key stored". | Strings "Android turns it off after a restart"; privileged-diagnostics comment |
| D6 | The Do Not Disturb / Bedtime state (`zen_mode` != 0) can hide the pairing-code notification, so the setup card warns about it. | Setup card comment and string |
| D7 | Unihertz's Android 16 firmware has a gesture-navigation settings page reachable by the action `com.android.settings.GESTURE_NAVIGATION_SETTINGS` with fragment argument `:settings:fragment_args_key` = `agui_hide_ime_caption_bar`, which hides the IME caption bar. | The dead tutorial page's constants (21) |
| D8 | The screen is near-square and short; the first-run pages pin Skip and Next below a scrolling page (3.2; 3.0 scrolled its buttons into view after 360 ms), tiles wrap two per row, and window size is read from the window rather than the display. | Comments in the setup and window-size code |

## 28. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| User presses Back on the first first-run page | `tutorial_completed` stays false; the next launcher tap shows the pages again while the keyboard is not set up, and goes home (writing the flag) once it is | completion is written by Done/Skip, or by launch routing (3) |
| App data cleared while PhysiBoard stays the keyboard | home, no first-run pages, no what's-new note | the keyboard check of 4.1 settles setup |
| Updating from 3.0 or 3.1 | no first-run pages; the what's-new note as usual | their own setup already wrote `tutorial_completed` (4.1) |
| First key on a hardware keyboard after touching a first-run page | only shows the focus on the page's button; the next Enter presses it | Android leaves touch mode on that key and consumes it |
| Fresh install, first launch | setup only; no what's-new note for the installed version | setup stamps `last_seen_whats_new_version` |
| Update installed while the app is not opened for weeks | the note shows once on the next open, for the current version only; intermediate versions' notes are never shown | the card is the current section of the change record |
| Enabled from Android settings without ever opening the app | first-run defaults still apply | they are stamped at process start, not on the home |
| Android 14+ refuses the secure setting read | the home and setup report "selected" only if PhysiBoard is the sole enabled IME with a current subtype; the Status screen reports "No" | two probe variants differ here |
| "Later" on a release, then "Updates" row | the dialog shows again | the manual check does not ignore dismissed releases |
| Daily job finds an update the user ignores | notification re-posted every 24 h, replacing itself | no per-release throttle beyond dismissal |
| Notification permission denied | update notification and re-selection notice are silently skipped (warning log, stripped in release) | receivers and jobs cannot prompt |
| Only pre-releases newer than the install exist | "up to date" | pre-releases and drafts are skipped |
| Tag with no `.apk` asset | dialog without "Download APK"; notification opens the release page | download URL is the first `.apk` asset only |
| Release list contains an entry with a blank `tag_name` | that entry is skipped | tag is required |
| GitHub returns HTML or a rate-limit object | "no update" (manual check: "Unable to reach GitHub.") | parse failure is caught |
| Update dialog dismissed by tapping outside | nothing recorded; shown again next open | only "Later" records |
| Diagnostics opened to export after a problem in another app | `[ime_context]` describes PhysiBoard's own field; `external_` lines describe the other app | 10.7 |
| More than 250 events, or > 500 KiB, or raw trackpad on | share goes as a file, not text | intent size limits |
| Two file shares in a row | the previous file is deleted before the new one is written | the directory is wiped each time |
| "Clear" on Diagnostics | also wipes the autocorrection, suggestion, trackpad and context buffers for the whole process | one store |
| Autocorrections chip off (default) | the export says the section is excluded and why | before/after words are typed text |
| Migration dialog and untested-device dialog both due | both appear, migration first | drawn in that order |
| "Restore my old settings" tapped | the entire 1.x file replays, including behavior settings; dropped keys stay dropped, renamed keys move | by design |
| Home "T2E Tools" tile before the first verified check lands | no badge | unknown status never raises it |
| Paired key stored, wireless debugging turned off | badge "needs pairing" on the tile; setup card says "Cannot reach the system" and offers the wireless toggle, not re-pairing | the pairing is still valid |
| Preview extra on the what's-new note | the version is stamped anyway | 5.3 |
| "Show the tutorial" while setup is already complete | the first-run pages open on page 3 with pages 1 and 2 already done (Back reaches them); Skip or Done returns home; Back out of page 1 returns to Help; the flag stays true throughout | the probe runs live; the pages open at the first step still to do |
| App language changed | takes effect when each activity is recreated | applied at context wrap |
| Bug report from a non-Titan phone | `device=Something else` | D1, D2 fail |
| Bug template's Diagnostics hint | points to "Settings → Advanced → Diagnostics", a path that no longer exists | stale template text |
| README minimum Android | says 11; build allows 10 (API 29) | documentation drift |
| `whats_new.md` for a version whose section has no marker | the whole section ships | marker is optional |

## 29. Test cases

Encodable as JVM tests without a device.

| # | Input | Expected |
|---|---|---|
| T1 | version compare latest "1.0.2", current "1.0.1" | update |
| T2 | "1.0.1" vs "1.0.1" | no update |
| T3 | "1.0.1" vs "1.0.2"; "1.0.1" vs "1.1" | no update (local ahead) |
| T4 | "1.1" vs "1.0.9" | update; "1.0" vs "1.0.0" no update |
| T5 | "1.0.2" vs "1.0.1-dev" | update; "1.0.1" vs "1.0.1-dev" no update |
| T6 | "beta" vs "alpha" | update; "beta" vs "beta" no update |
| T7 | normalize "v2.0.7", "V2.0.7", "2.0.7" | all "2.0.7" |
| T8 | releases [pre v2.1.0, draft v2.0.8, v2.0.7 (assets a.txt, physiboard-2.0.7.APK), v2.0.6] | chosen v2.0.7 with the APK's URL and its page |
| T9 | releases [pre, pre] | none |
| T10 | release with assets [readme.txt] | download URL none |
| T11 | dismissed set "v2.0.7,v2.0.6", latest "v2.0.7", ignore dismissed | no update; with ignore false: update |
| T12 | what's-new markdown "- **Settings had two of several things.** The Extras button opened the same page as All settings." | one note, title "Settings had two of several things", body as given |
| T13 | "- **Turn the accent row off again** — it had no switch." | title "Turn the accent row off again", body "it had no switch." |
| T14 | "- **Accent row** — Show variations is back — off stays off, including after a reset." | body keeps its inner em-dash |
| T15 | preamble "Line one.\nLine two.\n\n- **First bullet** body" | two notes: ("", "Line one. Line two."), ("First bullet", "body") |
| T16 | "- **Sym+C / Sym+V.** Use `Sym+A` for *select all*; 2*3 stays." | body "Use Sym+A for select all; 2*3 stays." |
| T17 | "\n\n" | no notes |
| T18 | change record text with `## 2.0.7 (...)`, two paragraphs, `<!-- /card -->`, bullets, `## 2.0.6` | generated file = the two paragraphs, trimmed, plus newline |
| T19 | change record with `## Unreleased` first then `## 2.0.7` | the 2.0.7 section |
| T20 | IME id match: "brobata.physiboard/brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService" and "brobata.physiboard/.inputmethod.PhysicalKeyboardInputMethodService" | both match; blank and "other/x" do not |
| T21 | receiver with `default_input_method` = "brobata.physiboard/it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService" | notification 3 posted; with the current component: nothing |
| T22 | release-notes JSON `tag_name` "v2.0.6" requested for "2.0.7" | nothing |
| T23 | release-notes body with a heading, 10 bullets, prose | 8 highlights, `**` stripped |
| T24 | build constants | application id "brobata.physiboard" (suffix ".sideload" allowed), channel "physi", repo "brobata/physiboard", F-Droid false, checks true |
| T25 | What's New card decision with tutorial incomplete | false; complete and last seen "2.0.6", current "2.0.7": true; equal: false; current blank: false |
| T26 | bug-report URL for a Titan 2 Elite on Android 15 | query `template=bug.yml`, `app_version`, `device=Unihertz Titan 2 Elite`, `diagnostics` with three lines |
| T27 | export with 251 recorded events, chips off | shared as a file; with 250 and a 400 KiB report: text |
| T28 | autocorrection attempt: not-applicable, reason `auto_replace_disabled`, before "", after null | not recorded; before "teh": recorded |
| T29 | 101 autocorrection records | 100 kept, the oldest gone |
| T30 | suggestion snapshots [A,B], [A,B], [], [C] | export rows: "A,B x2", "C" |
| T31 | context updates: app X, then PhysiBoard | last field = PhysiBoard, external = X |
| T32 | credits markdown "{{button:Coffee|https://k.o}}" | one button element labelled "Coffee" |
| T33 | Alt+Shift default rule on an empty preferences file | `alt_shift_layout_switch` false; on a non-empty file without the key: true; with the key already set: unchanged |
| T34 | launch with `tutorial_completed` false, no version stamp, keyboard not enabled | first-run pages |
| T35 | `tutorial_completed` false, no version stamp, enabled, not selected | first-run pages |
| T36 | `tutorial_completed` false, no version stamp, enabled and selected | home, and setup recorded as finished |
| T37 | `tutorial_completed` true, any keyboard state | never the first-run pages |
| T38 | a 3.1 store (`tutorial_completed` true, last seen "3.1.0") opened by 3.2 with the keyboard switched away | the what's-new note, not the pages |
| T39 | a backup holding `tutorial_completed` true restored onto a fresh install | home, not the pages |
| T40 | `tutorial_completed` false, no version stamp, current "3.2.0" | first-run pages before any what's-new note |
| T41 | `tutorial_completed` false (reset by 3.1's Help "Show the tutorial"), last seen "3.1.0", keyboard not selected, opened by 3.2 | the what's-new note, not the pages |

## 30. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| Two-step setup with live polling | keep | it is the whole first run on a Titan; translate the four hard-coded strings |
| Essentials card | keep | three lines, cheap, tells a new user the two things they cannot discover |
| What's-new note generated from the change record at build time | keep | the only way the card never lies; keep the `<!-- /card -->` contract |
| Multi-page tutorial tour, release-notes fetcher, dev's-choice settings | drop | unreachable in 2.x; the tour taught the soft keyboard and nav mode |
| Home as action surface with six tiles | keep | but the tile set is a product decision; the enabled-language count goes |
| Migration notice and "Restore my old settings" | drop | 3.0 is a new app; there is no 1.x file to migrate from |
| Untested-device notice and D1/D2 detection | keep | still the honest answer for a plain Titan 2 |
| Status screen | keep | answers "why is nothing working" in five rows |
| Single IME probe | keep | implement once; decide the Android 14 fallback deliberately |
| Diagnostics: recorder, panel, export, file share, external-field rule | keep | it is the bug-report pipeline for a build with stripped logs |
| Raw trackpad and suggestion sections | undecided | depends on whether the screen trackpad and the suggestion strip survive as specified elsewhere |
| Debug capture buffers (100/50/200) | keep | sizes are fine |
| Perf logger | drop | a debug-only no-op with stripped output |
| GitHub update checks, dialog, "Later", daily job, notification | keep | the only distribution channel; keep the F-Droid installer exemption |
| Pre-release skipping | keep | but decide whether a pre-release channel is wanted for testers |
| Bug report with prefilled template | keep | keep the three device labels identical to the template |
| About credits markdown renderer | drop | render a fixed screen; 3.0 has no upstream credits to ship, only the licences of what it still bundles (fonts, sounds, CLDR, dictionaries) |
| Package-replaced receiver | drop | D4 was a one-time event; 3.0 must instead decide its own component name and keep it |
| Nav-mode notification channel | drop | never posted |
| Feature-status markers | drop | only Construction on soft-keyboard-adjacent screens |
| Software keyboard mode shortcut and action | drop | no on-screen keyboard in 3.0 |
| IME test screen | drop | unreachable; a field gallery is a dev tool, not an app screen |
| Terminal theme, JetBrains Mono, amber palette | keep | brand |
| App-language override | undecided | ten machine-made translations for a one-device app; decide whether to ship English only |
| Sideload build type | keep | the release pipeline test that catches shrinking breakage |
| Log stripping below error | keep | but write the diagnostics export first |
| `*_base.json` asset exclusion | drop | 3.0 should not have build-only assets in the source set at all |
| Release script with certificate pin | keep | the pin is what keeps updates installable |
| F-Droid build flag | undecided | no F-Droid listing exists today |
| Backup rules | keep | adjust file names to 3.0's |
| Private mode, the network gate, honouring "no personalized learning" (section 31) | add (3.0) | a keyboard sees everything typed; the user needs one switch that provably stops it remembering anything or going online |

## 31. Private mode and the network gate (3.0)

New in 3.0; 2.x had no such mode. One setting, `private_mode` (boolean, default false; settings
catalog section 2.17), stored like every other row, so it survives a restart and a backup.

### 31.1 What stops learning

Learning is off while either is true:

- `private_mode` is on, or
- the field being typed in has Android's "no personalized learning" flag
  (`EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` in its IME options), as incognito browser tabs
  and some banking apps set. This needs no setting and cannot be switched off. The flag is read
  when the keyboard attaches to a field, before the field's first keystroke or debug record, and
  is dropped when the field finishes. It stops learning only: it does not take PhysiBoard
  offline and shows no indicator.

While learning is off, nothing new is remembered:

| Path | While learning is off |
|---|---|
| Next-word pairs (`user_ngrams.db`, autocorrect-suggestions.md section 4) | no pair is learned, not even in memory for the session; a mix-up fix takes back no earlier learn. The context still follows the typing, so pairs already known are still offered. A pair whose first word was typed while learning was off is never learned afterwards: the context restarts at "sentence start" when learning comes back |
| Personal words (autocorrect-suggestions.md section 6.2) | the add-word slot or gesture still types the word but does not save it; toast "Private: the word was typed but not saved" |
| Clipboard history capture (expansion-clipboard-pickers-launcher.md section 3.1) | a copy is not kept |
| Recent emoji (expansion-clipboard-pickers-launcher.md section 4.4) | choosing an emoji leaves the recents as they were |
| Debug capture (sections 10 and 11) | no key event, no field attach and no autocorrection record reaches the store, whether Diagnostics is open or not; the developer log trail (`ime_overlay_debug_logging`) is silent too |

What is already known keeps working: the dictionaries, personal and default words, learned
pairs, text replacements, autocorrect and the clipboard history already stored. Deleting (a
personal word, a clip, a hidden suggestion) still works, since it removes rather than remembers.
There are no kaomoji recents in 3.0.

### 31.2 The network gate

Only the user's switch takes PhysiBoard offline. Every network request PhysiBoard makes passes
one gate, which reads `private_mode` from the store at the moment of the request:

| Purpose | Who asks | Refused |
|---|---|---|
| update check | the four triggers of section 13.1 | "no update"; the "Updates" row toasts "Private mode is on, so PhysiBoard makes no network requests." |
| dictionary list | Installed dictionaries screen (dictionaries-languages.md section 5.2) | installed files still listed; the reason as a snackbar, or as the screen's error when nothing is installed |
| dictionary download | the same screen's download button (section 5.3) | snackbar with the reason; nothing written |
| GIF search | the GIF page's trending and search lists (layers-sym-alt.md section 4.5) | the page shows the reason in place of results |
| GIF download | the GIF page's previews and the GIF being sent | previews stay blank; a send toasts the reason |

When the store cannot be read, the gate also refuses ("PhysiBoard could not read its settings,
so it makes no network requests."): not knowing whether the user asked for offline is treated
as yes. A store file that is corrupt is not "unreadable" here: it is replaced by an empty one and
reads as the defaults (settings-catalog.md section 4.2), so private mode reads as off.

The keyboard applies the same rule to learning: until the store's first value has arrived, nothing
is learned, because `private_mode` might be on. A re-application of the built-in defaults before
then (a layout switch) does not count as having read the store. A
build-time test fails if any file other than the gate's one HTTP opener opens a network
connection, so a future feature has to name its purpose and pass the gate. The keyboard reaches
the gate through a seam the app hands it (a gated GET built on the same opener), so the GIF
page's requests are opened in that one file too.

Not PhysiBoard's network, and not changed by private mode: links the user taps in the app (About,
the update dialog's "Open GitHub", "Report a problem") open in the browser; dictation uses the
phone's own speech service, which private mode asks to keep on the phone (dictation.md 4.3:
`EXTRA_PREFER_OFFLINE` is forced on and the online fallback is refused), though the service
itself is not PhysiBoard's and the Privacy screen says so, which is why every wording says "no
network requests" rather than "offline";
the embedded ADB
broker talks only to the phone's own adbd over loopback, and only during the privileged setup the
user starts (broker-privileged-toolbox.md section 1).

### 31.3 Switching it from the keyboard

The command `physiboard.toggle_private_mode` ("Private mode", PhysiBoard source, every surface;
expansion-clipboard-pickers-launcher.md section 8.2) flips the setting. It can be put on a key
under Assigned launcher keys (fired with Sym + that key, or bare on the home screen when that is
enabled), found in the quick launcher, or bound on the Fn layer / in nav mode as a `command`
mapping with that id. The keyboard applies the new state at once, writes it to the store, and
until the store's own update arrives (at most 5 s; a write that never lands stops counting then)
an older update in flight does not switch it back. A toast says which way it went: "Private mode
on: PhysiBoard learns nothing and makes no network requests" or "Private mode off". Before the
store's first value has arrived the command changes nothing and toasts "PhysiBoard is still
loading its settings; try again in a moment". Changing the switch in the settings app shows no
toast.

### 31.4 How it shows

There is no bar (status-bar.md); the indicator lives where the keyboard already draws:

- **Caret badge** (trackpad-caret-nav.md section 4): while `private_mode` is on, the badge shows
  "PRIVATE" in its locked colour after any modifier glyphs, so it is beside the caret for every
  keystroke. It needs what the badge always needs: `caret_modifier_badge` on, the "Display over
  other apps" grant, and an app that reports its caret. A refresh that moves nothing costs no
  window update.
- **Clipboard page header**: "Clipboard History · private, new copies not saved" while learning
  is off for either reason (expansion-clipboard-pickers-launcher.md section 3.5), updated in
  place when that changes while the page is open.
- **Toast** on every toggle from the keyboard (31.3).
- **Settings**: the Privacy row's description on the Settings screen says "Private mode is on:
  nothing is learned, no network requests".

### 31.5 Test cases

| # | Situation | Expected |
|---|---|---|
| P1 | private mode off, ordinary field | learning on, network on, no indicator |
| P2 | private mode on | learning off, network refused for every purpose, indicator shown |
| P3 | field with the "no personalized learning" flag, private mode off | learning off, network on, no indicator |
| P4 | private mode on, type "smoked brisket. the brisket " | no pair learned |
| P5 | pair smoked > brisket known, private mode on, type "the smoked " | "brisket" is the first next-word suggestion |
| P6 | learning on, type "it is bigger then ", turn private mode on, type "mine " | the text is fixed to "than"; the earlier learn bigger > then stays; nothing new learned |
| P7 | private mode on, a text copy | not captured |
| P8 | private mode on, choose an emoji | recents unchanged |
| P9 | private mode on, a key event, a field attach, an autocorrection | none reaches the debug store |
| P10 | `private_mode` unreadable | network refused |
| P10a | stored `private_mode` true, a layout switch before the store's first value | nothing learned |
| P11 | any Kotlin source outside the gated opener and the ADB broker opens a connection | the build fails |
| P12 | private mode in a backup | restored |
| P13 | private mode on, a notification with a one-time code arrives | no code is kept; codes already held are dropped when private mode comes on |

### 31.6 Notification access for one-time codes, and what it means for privacy

The Fill page (layers-sym-alt.md 4.7) offers sign-in codes that arrive by text message, e-mail
or an app's notification. To see them PhysiBoard needs **notification access**, one of
Android's special permissions: an app holding it is shown every notification's content. It is
sensitive, so:

- **Asked for by the user, never taken.** The listener ("PhysiBoard one-time codes") works only
  after the user allows it in Android's settings. Sym pages > Fill page > "Notification access" (also Privacy > "Notification access for codes")
  says what it is for and opens Android's own page for this one listener
  (`Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with
  `Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME`, falling back to
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). The row re-reads the grant every time the
  screen resumes. It is a separate entry from the notification ring's listener
  (device-backlight-ring.md), and the privileged setup pass never grants it: allowing the ring
  does not allow code reading. An install made outside a store may find the entry marked
  "Restricted setting" on Android 13 and later; App info > ⋮ > "Allow restricted settings"
  lifts that.
- **What is read.** The title and text of each notification posted while access is given (and
  those already showing when it connects), only while `otp_from_notifications` is on and
  private mode is off; never PhysiBoard's own. Each text is searched for a code and then
  dropped.
- **What is kept.** Only the code, the posting app's name and package, and when it arrived: at
  most 3, in memory, for at most 10 minutes; gone sooner when typed, when the screen turns off,
  when private mode comes on, when the switch goes off or access is withdrawn, or when the
  process ends.
- **What never happens.** No text and no code is written to disk, to the settings, to a backup,
  to the debug capture store or a diagnostics report, to the log or a trace, and nothing is
  sent anywhere: the listener makes no network request (31.2's gate has no purpose for it).
- **Turning it off.** The switch "One-time codes from notifications" makes the listener inert
  and drops every code; withdrawing access in Android's settings unbinds it.

**Store compliance notes.** Google Play: notification access is not a runtime permission and is
not on Play's restricted-permissions list, but the listing and the in-app row must say plainly
what is read and why (the row above is the in-app disclosure, shown before the user goes to
Android's page), and the Data safety form answers "no data collected or shared" for it, since
nothing leaves the phone and nothing is kept beyond 10 minutes in memory. The privacy policy
needs a sentence: "If you allow notification access, PhysiBoard reads incoming notifications on
your phone only to find one-time sign-in codes, keeps a found code in memory for up to 10
minutes, and never stores or sends notification content." The inline-suggestions switch reads
no data at all: the suggestions are drawn by the password manager.

## 32. Provenance

- app/src/main/java/brobata/physiboard/MainActivity.kt
- app/src/main/java/brobata/physiboard/OnboardingScreen.kt
- app/src/main/java/brobata/physiboard/TutorialActivity.kt
- app/src/main/java/brobata/physiboard/StatusScreen.kt
- app/src/main/java/brobata/physiboard/ImeStatus.kt
- app/src/main/java/brobata/physiboard/ImeIdentity.kt
- app/src/main/java/brobata/physiboard/FeatureStatus.kt
- app/src/main/java/brobata/physiboard/DiagnosticsScreen.kt
- app/src/main/java/brobata/physiboard/DeviceSetupCard.kt
- app/src/main/java/brobata/physiboard/AboutScreen.kt
- app/src/main/java/brobata/physiboard/ExtrasScreen.kt
- app/src/main/java/brobata/physiboard/ImeTestScreen.kt
- app/src/main/java/brobata/physiboard/SettingsActivity.kt
- app/src/main/java/brobata/physiboard/SettingsScreen.kt
- app/src/main/java/brobata/physiboard/SettingsManager.kt
- app/src/main/java/brobata/physiboard/SettingsMigration.kt
- app/src/main/java/brobata/physiboard/PackageReplacedReceiver.kt
- app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- app/src/main/java/brobata/physiboard/AppBroadcastActions.kt
- app/src/main/java/brobata/physiboard/AppLocaleManager.kt
- app/src/main/java/brobata/physiboard/AppPackageChangeMonitor.kt
- app/src/main/java/brobata/physiboard/AppLanguageSettingsScreen.kt
- app/src/main/java/brobata/physiboard/CustomInputStylesScreen.kt
- app/src/main/java/brobata/physiboard/ActivityTransitionCompat.kt
- app/src/main/java/brobata/physiboard/LocalizedComponentActivity.kt
- app/src/main/java/brobata/physiboard/BuildInfo.kt
- app/src/main/java/brobata/physiboard/inputmethod/DebugCaptureStore.kt
- app/src/main/java/brobata/physiboard/inputmethod/ImePerfLogger.kt
- app/src/main/java/brobata/physiboard/inputmethod/NotificationHelper.kt
- app/src/main/java/brobata/physiboard/inputmethod/KeyboardEventTracker.kt
- app/src/main/java/brobata/physiboard/inputmethod/PrivilegedDiagnostics.kt
- app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt
- app/src/main/java/brobata/physiboard/inputmethod/EmbeddedAdbShell.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- app/src/main/java/brobata/physiboard/update/UpdateChecker.kt
- app/src/main/java/brobata/physiboard/update/UpdateCheckWorker.kt
- app/src/main/java/brobata/physiboard/update/UpdatePolicy.kt
- app/src/main/java/brobata/physiboard/update/UpdateReleaseResolver.kt
- app/src/main/java/brobata/physiboard/update/ReleaseNotesFetcher.kt
- app/src/main/java/brobata/physiboard/ui/WhatsNewNotes.kt
- app/src/main/java/brobata/physiboard/ui/SettingsActivity.kt
- app/src/main/java/brobata/physiboard/ui/SettingsTopBar.kt
- app/src/main/java/brobata/physiboard/ui/CustomTopBar.kt
- app/src/main/java/brobata/physiboard/ui/WindowSize.kt
- app/src/main/java/brobata/physiboard/ui/BrokerStatus.kt
- app/src/main/java/brobata/physiboard/ui/theme/Color.kt
- app/src/main/java/brobata/physiboard/ui/theme/Theme.kt
- app/src/main/java/brobata/physiboard/ui/theme/Type.kt
- app/src/main/java/brobata/physiboard/ring/NotificationRingLauncher.kt
- app/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt
- app/src/main/AndroidManifest.xml
- app/src/main/res/xml/method.xml
- app/src/main/res/xml/backup_rules.xml
- app/src/main/res/xml/data_extraction_rules.xml
- app/src/main/res/xml/file_provider_paths.xml
- app/src/main/res/values/strings.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/values-night/themes.xml
- app/src/main/res/values-*/ (directory listing)
- app/src/main/assets/common/about_credits.md
- app/build.gradle.kts
- app/proguard-rules.pro
- app/proguard-rules-strip-logs.pro
- app/src/test/java/brobata/physiboard/update/VersionComparisonTest.kt
- app/src/test/java/brobata/physiboard/update/UpdateCheckerFlavorLogicTest.kt
- app/src/test/java/brobata/physiboard/ui/WhatsNewNotesTest.kt
- app/src/test/java/brobata/physiboard/ImeIdentityTest.kt
- app/src/test/java/brobata/physiboard/BuildConfigTest.kt
- app/src/test/java/brobata/physiboard/TutorialDevsChoiceSettingsTest.kt
- .github/ISSUE_TEMPLATE/bug.yml
- .github/ISSUE_TEMPLATE/idea.yml
- .github/ISSUE_TEMPLATE/config.yml
- .github/workflows/ci.yml
- scripts/build-release.sh
- scripts/verify-release-apk.sh
- fastlane/metadata/android/en-US/title.txt
- fastlane/metadata/android/en-US/short_description.txt
- fastlane/metadata/android/en-US/full_description.txt
- README.md
- CONTRIBUTING.md
- NOTICE.md
- THIRD_PARTY_NOTICES.md
- PHYSIBOARD_CHANGES.md
- docs/spec/README.md
- docs/plans/rebuild-from-scratch.md
- docs/release-signing-certificate-attestation.md
- git history of app/src/main/res/values-*/strings.xml (commits b527b56, a7a4507)
