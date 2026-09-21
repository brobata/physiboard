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
  only what needs attention, then a grid of six tiles.
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

1. If `tutorial_completed` is false: open the setup screen and close the home screen. Nothing
   else in this list runs.
2. Else if the what's-new note is due: it is due when `tutorial_completed` is true, the build's
   version name is non-blank, and `last_seen_whats_new_version` differs from it (including
   being absent). Open the what's-new note (the setup activity with the boolean extra
   `brobata.physiboard.UPDATE_TUTORIAL` = true) and close the home screen.
3. Else, if GitHub checks are allowed, (re)schedule the daily background update job
   (section 13.7).
4. Draw the home screen.

A consequence of 1: the "Show Tutorial" row in About resets `tutorial_completed` to false and
opens the setup screen directly, so the setup flow is also what a user sees when they ask to
review the tutorial. Another consequence: pressing Back on the setup screen instead of
finishing it leaves `tutorial_completed` false, so the next tap on the icon opens setup again.

## 4. First-run setup

The setup screen is one scrolling page with a terminal-style header (`physiboard:~$ setup`
with a cursor that fades between opaque and transparent every 650 ms), the line "Two quick
steps to start typing.", and two step cards.

### 4.1 The two steps

| Step | Card | Done when | Button | Button does |
|---|---|---|---|---|
| 1 | "1. Enable PhysiBoard" | enabled (section 8.2) | "Open settings", enabled while not done | Opens Android's input-method settings (`android.settings.INPUT_METHOD_SETTINGS`) |
| 2 | "2. Set as keyboard" | selected (section 8.2) | "Switch", enabled only while step 1 is done and step 2 is not | Shows Android's input-method picker |

Step 2's whole card is drawn at 45 % opacity until step 1 is done. A done card shows a check
icon, an amber border, and the word "done" under its title; its button disappears. The four
strings on the step cards ("Enable PhysiBoard", "Open settings", "Set as keyboard", "Switch")
are hard-coded English; every other string on the screen is translated.

The screen polls the probe of section 8.2 immediately and then every 1800 ms for as long as it
is showing, so the cards flip to done on their own when the user returns from Android's
settings; there is nothing to tap to confirm.

### 4.2 "You're set"

When both steps are done, a section animates in below the cards: a check icon, "You're set.",
and two buttons: "Show me the essentials" (filled) and "Skip" (outlined). 360 ms after both
steps become done the page scrolls to its bottom so the buttons are never below the fold on
the short square screen; the same scroll happens when the essentials expand.

"Show me the essentials" replaces the two buttons with a card of three lines and a "Done"
button:

- microphone icon: "Hold Fn to talk (dictation)"
- sun icon: "Backlight can light the dark (one-time setup)"
- gear icon: "Everything else lives in the Settings tile"

"Skip" and "Done" do the same thing: mark setup complete and open the home screen.

### 4.3 What "complete" writes

Completing setup writes `tutorial_completed` = true and `last_seen_whats_new_version` = the
current version name, in one commit. Because the version is stamped here, a fresh install never
sees the what's-new note for the version it was installed with.

### 4.4 Permissions

The setup screen asks for nothing. The first time the home screen is drawn on Android 13 or
later without the notification permission, it launches the system `POST_NOTIFICATIONS` prompt
once per home screen creation and ignores the answer; this is what lets the update
notification and the re-selection notice (sections 13.7 and 17) be shown. The toolbox's device
setup card asks for the same permission again when it appears un-granted, because the pairing
code arrives as a notification (section 4.5). Microphone and overlay permissions are asked by
their own features (dictation and trackpad documents).

### 4.5 The device setup card

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

Top to bottom: the terminal header (section 22.1), then a 16 dp-padded column: zero or one
setup action card, zero or one update action card, the all-clear line, and the six tiles in
rows of two (an odd last row keeps tile width by leaving the second slot empty). The whole
page scrolls. A translucent overlay (black at 30 % in dark theme, white at 20 % in light)
covers the status-bar area.

### 6.2 What the home probes, and how often

On creation and then every 2000 ms while visible: the enabled/selected probe (section 8.2) and
a count of distinct languages among PhysiBoard's enabled subtypes (computed but not shown
anywhere in 2.x). The verified broker status is observed as in section 1 and refreshed when
wireless debugging flips, polled every 1500 ms.

### 6.3 Action cards

| Condition | Card title | Subtitle | Tap |
|---|---|---|---|
| not enabled | "Enable PhysiBoard" | "Turn it on in system keyboard settings" | opens `android.settings.INPUT_METHOD_SETTINGS` |
| enabled, not selected | "Set as keyboard" | "Pick PhysiBoard from the input switcher" | shows the input-method picker |
| an update was found on this open (section 13.4) | "Update available" | "Version X is ready to install" | reopens the update dialog for that version |

At most one setup card shows (enable wins). When enabled, selected and no update is pending,
the monospace amber line "✓ all set" is shown instead.

### 6.4 Tiles

| Order | Label | Sublabel | Attention dot | Opens |
|---|---|---|---|---|
| 1 | "T2E Tools" | "needs pairing" when the verified broker status is known and not `OK` | same condition | settings, destination `toolbox_destination` |
| 2 | "Keyboard" | none | no | settings, `keyboard_hub_destination` |
| 3 | "Status Bar Theme" | none | no | settings, `keyboard_theme_destination` |
| 4 | "Status" | "all good" when enabled and selected, else "needs setup" | when not (enabled and selected) | settings, `status_destination` |
| 5 | "Extras" | none | no | settings, `extras_destination` |
| 6 | "Settings" | none | no | settings, top level |

The attention dot is a 9 dp amber circle at the tile's top-right. The broker badge deliberately
stays off while the status is unknown: flashing "needs attention" at every launch would train
the user to ignore it. Settings is opened with a slide-in-from-right animation and closes with
a slide-out-to-right; the destination travels in the string extra
`brobata.physiboard.SETTINGS_DESTINATION`.

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
PhysiBoard is available (<tag>)", the keycap silhouette as small icon, default priority,
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
| `adb_pairing` | pairing channel | high, silent, no badge, no bubbles | vendored service's own | the pairing service while armed (4.5) | shows the pairing-code entry |

All app-posted notifications use the single-colour keycap silhouette as small icon (change
record 2.0.2). The nav-mode channel is deleted and recreated whenever its creation runs, which
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
| `android.settings.ADB_WIRELESS_SETTINGS` | fired, with fallback | 4.5 |

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

### 22.1 Visual identity

Every activity uses one theme: Material 3 with dynamic colour disabled, dark or light following
the system. Palette: Ink `#0F172A` (dark background), Slate `#1E293B` (dark surfaces), Signal
Amber `#F59E0B` (primary and tertiary in both modes, `onPrimary` Ink), Sky `#38BDF8`
(secondary), Cloud `#F1F5F9` (light background, dark text), Slate500 `#64748B` (outline, muted
text), error `#EF4444` dark / `#DC2626` light. Typography is JetBrains Mono throughout (regular,
medium, bold). The window theme is no-action-bar Material with status and navigation bars in
the splash colours (dark or light variant), and edge-to-edge is enabled on every activity.

The home header is an Ink band with a 2 dp amber hairline on top and `physiboard:~$` in bold
18 sp amber followed by a 10 × 20 dp amber block cursor that fades between opaque and
transparent every 600 ms; the fade is held static when the system animator duration scale is
0 (reduced motion). The settings screens share one top bar: status-bar inset, 1 dp tonal
elevation, back arrow (content description "Back"), the title as a heading in headline-small
semi-bold, trailing actions.

### 22.2 Transitions and sizing

Opening settings from home slides in from the right; finishing the settings activity slides out
to the right (also on Android 14 and later through the newer API). Screens that need the
window's size read it from the window, not the display configuration, so multi-window and the
near-square Titan screen get the bounds the content is actually in.

### 22.3 App language

Every activity wraps its base context with the locale from `app_language_tag` when that key is
non-blank; blank or absent means the system locale. Because it is applied at activity creation,
a language change takes effect when the screen is recreated. Options: system default, then
`en`, `it`, `de`, `es`, `fr`, `pl`, `ru`, `uk`, `vi`, `hy`.

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
| sideload | `brobata.physiboard.sideload` | "PhysiBoard (sideload)" for app and IME | same as release | kept | the release key |

The sideload type exists so the release R8 pipeline can be exercised on the maintainer's own
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
hard-coded English strings are: the four setup step-card strings (4.1), the IME test screen
(20), the "Debug Export" share subject, the report's section and key names, the terminal
prompts, and the About build-info first line. Release-notes fallback sentences exist in German,
Italian and English only (14). The app-language override (22.3) offers exactly the ten
locales.

## 26. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `tutorial_completed` | boolean | false | whether the launcher icon opens setup or home (3) | written by setup Done/Skip and what's-new Done; reset by About "Show Tutorial" | "Show Tutorial" |
| `last_seen_whats_new_version` | string | absent | the version whose what's-new note has been seen; a mismatch with the build shows the note (3) | written by setup and what's-new Done | none |
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
| D8 | The screen is near-square and short; the setup page scrolls its buttons into view after 360 ms, tiles wrap two per row, and window size is read from the window rather than the display. | Comments in the setup and window-size code |

## 28. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| User presses Back on the setup screen | `tutorial_completed` stays false; next launcher tap shows setup again | completion is only written by Done/Skip |
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
| "Show Tutorial" while setup is already complete | setup screen shows with both steps already done; "Skip" or "Done" returns home | the probe runs live |
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

## 31. Provenance

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
