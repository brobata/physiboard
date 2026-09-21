# Per-app behavior

Behavior that changes depending on which app owns the text field: how the keyboard finds out
which app that is, what Enter does in messengers, which apps get "exact typing" (no smart
features), the shared list and picker screens, how the lists are stored, what happens when a
listed app is removed, and the on-screen-keyboard mode machinery that reacts to devices coming
and going. The status bar "dip" list is another per-app list; it is specified in
`status-bar.md` and only referenced here.

## 1. Scope and vocabulary

- **Current app**: the package name reported by the text field the keyboard is attached to.
- **Editor action**: the action an app declares on its text field (Send, Go, Search, Next,
  Done, Previous). It is what the on-screen keyboard's blue button would trigger. Requesting it
  is a one-way call: Android reports only that the connection was alive, never whether the app
  did anything with it.
- **Plain Enter**: a synthetic Enter key press (key down then key up, keycode 66) delivered to
  the app's text field as if a keyboard with no smart features had typed it.
- **Newline**: the keyboard itself inserts the character `\n` into the field.
- **Per-app list**: a set of package names stored in preferences, edited on a list screen.
- **WebAPK**: an installed web app (PWA) whose package name begins `org.chromium.webapk.`;
  its pages run inside a host browser.

## 2. Identifying the current app

### 2.1 The package name comes from the editor

Every time a text field takes focus, Android hands the keyboard the field's description, which
includes the package name of the app that owns it. The keyboard remembers this as the current
package for the whole time that field is focused. It is re-read on every field start (including
restarts of the same field). When no field is focused the current package is whatever the last
field reported; the diagnostics snapshot keeps the last non-PhysiBoard package on purpose, so a
bug report describes the app the user was in and not the settings screen they opened to file it
(changelog 2.0.4).

The package name is used, in this order of importance, for:

1. Enter behavior (section 3), matched by exact string.
2. Exact typing (section 4), matched by exact string and then by WebAPK host expansion.
3. The status bar's per-app visibility and dip lists (`status-bar.md`), exact string.
4. Dictation: a session started in one app ends if another app takes the field
   (`dictation.md`).
5. Diagnostics: the snapshot records the package, the raw `inputType`, the raw `imeOptions`,
   and the resolved editor action name (`go`, `search`, `send`, `next`, `done`, `previous`,
   or `none`).

A null package (no field) matches nothing: every per-app lookup returns "not listed".

### 2.2 WebAPKs report as their browser

An installed web app is a thin shell package. Its text fields live in the host browser's
process, so the keyboard sees the browser's package name, never the shell's. Concretely, with
PersaLink installed from Chrome as a WebAPK, the field reports `com.android.chrome`.

Rules:

- A package is a WebAPK when its name starts with `org.chromium.webapk.`.
- The host browser of a WebAPK is read from the shell package's manifest metadata entry
  `org.chromium.webapk.shell_apk.runtimeHost`. If the package is not installed, the metadata
  is missing, or the value is blank, the host is assumed to be `com.android.chrome`.
- A package that is not a WebAPK has no host (the lookup answers "none").
- "Expanding" a set of packages means adding the host browser of every WebAPK in it. A set with
  no WebAPKs is returned unchanged. Example: {`com.termux`, `org.chromium.webapk.x`} expands to
  {`com.termux`, `org.chromium.webapk.x`, `com.android.chrome`}; {`com.termux`} stays as it is.

Only the exact-typing list is expanded (section 4.3). The Enter behavior overrides, the status
bar lists, and the dip list are matched by exact package name; a WebAPK entry in those lists
will never match because the field never reports the shell's name. The exact-typing list screen
tells the user about the expansion on web-app rows; no other screen does.

## 3. Enter key behavior

### 3.1 Vocabulary

Four things are chosen per app, plus one global preset:

| Term | Values (stored strings) | Meaning |
|---|---|---|
| Wanted behavior | `app_default`, `enter_newline`, `enter_send_shift_newline`, `enter_newline_ctrl_send` | What Enter, Shift+Enter and Ctrl+Enter should do in that app |
| Send method | `auto`, `editor_action`, `ctrl_enter`, `plain_enter` | How a "send" is delivered to the app |
| Extra send shortcut | `none`, `sym_enter` | Whether Sym+Enter also sends |
| Override | one row per package holding the three above | The user's (or the preset's) choice for one app |
| Messaging preset | `app_default`, `enter_send_shift_newline`, `enter_newline_ctrl_send`, `custom` | A blanket default for the shipped list of tested messengers |

User-facing labels: wanted behavior "App default", "Enter newline", "Enter sends, Shift+Enter
newline", "Enter newline, Ctrl+Enter sends"; send method "Auto / not tested", "Editor action",
"Ctrl+Enter", "Plain Enter"; extra shortcut "None", "SYM + Enter"; preset "App default", "Enter
sends, Shift+Enter newline", "Enter newline, Ctrl+Enter sends", "Custom".

A fifth preset value `enter_newline_only` ("Enter newline only") exists in the resolution rules
but is not offered in the preset dropdown and is rejected by the store (it is normalized to
`app_default` when written and when read). It is unreachable in 2.x.

### 3.2 The shipped app lists

Four fixed lists ship in the app. They are not editable.

| List | Packages | Used for |
|---|---|---|
| Preset packages (9) | `com.whatsapp`, `org.telegram.messenger`, `org.thoughtcrime.securesms`, `com.discord`, `im.vector.app`, `com.google.android.apps.messaging`, `ch.threema.app`, `ch.threema.app.libre`, `com.instagram.android` | The only apps the messaging preset may apply to without an override |
| Send-action packages (8) | the preset packages minus `com.discord` | Apps allowed to receive an editor action for "send" without the user having configured them |
| Favourites (10) | the 9 preset packages plus `com.facebook.orca`, in this order: WhatsApp, Telegram, Signal, Discord, Element, Google Messages, Threema, Threema Libre, Instagram, Messenger | Surfaced at the top of the override list; rows for these show a status card instead of editable controls; the preset writes an override row for each installed one |
| Tested packages (7) | `com.whatsapp`, `org.telegram.messenger`, `im.vector.app`, `com.google.android.apps.messaging`, `ch.threema.app`, `ch.threema.app.libre`, `com.instagram.android` | Only affects the wording of the status card ("Active" versus "Experimentally active") |

Discord is in the preset and favourite lists but excluded from the send-on-Enter preset and from
the send-action list, because its compose box ignores the editor action (it only hides the
keyboard); Discord already sends on Enter and inserts a newline on Shift+Enter by itself.
Messenger (`com.facebook.orca`) is a favourite only: no preset ever applies to it, and it only
gets an editor action once it has an override row (section 3.13).

### 3.3 Resolving what applies to the current app

All three lookups return "nothing" when the master switch `app_enter_behavior_enabled` is off
or there is no current package.

**Wanted behavior** for package P:

1. If an override row for P exists and its behavior is not `app_default`, that behavior wins.
   This applies to any package whatsoever; the user naming an app is the authority.
2. Otherwise, if P is not one of the 9 preset packages, nothing applies (Enter is left to the
   generic handling in section 3.9).
3. Otherwise the preset decides: `enter_send_shift_newline` gives that behavior, except for
   Discord, which gets nothing; `enter_newline_ctrl_send` gives that behavior;
   `enter_newline_only` would give `enter_newline`; `app_default` and `custom` give nothing.

An override row with behavior `app_default` therefore falls through to the preset (a WhatsApp
row set to "App default" under the send-on-Enter preset still sends on Enter).

**Send method** for P: the override row's send method, else `auto`.

**Extra send shortcut** for P: the override row's value, else `none`.

**Editor action allowed** for P: true when P is one of the 8 send-action packages, or when any
override row exists for P (regardless of its content, even `app_default`).

History: until 2.0.3 the "is P a preset package" test ran before the override lookup, so an
override on WhatsApp Business, a Signal fork or Slack was stored, displayed, and ignored; and
the send method was stored and never read (commit 8b03e80, changelog 2.0.3).

### 3.4 The four delivery mechanisms

**Newline**: finish any composing text, commit `\n` at the cursor, run the after-Enter
auto-capitalization check (`text-input.md`), reset the suggestion context. Always reported as
handled.

**Editor action**: finish composing, run the after-Enter auto-capitalization check, then request
the editor action with the resolved action id. The action id is the field's own action (section
3.9) if it declares one, else Send (id 4). If Android reports the request as delivered: when the
send was triggered by Ctrl, clear the Ctrl state (latch, one-shot, nav-mode latch; a nav-mode
latch also cancels the nav-mode notification and refreshes nav mode) and refresh the status bar;
reset the suggestion context. Handled if and only if the request was delivered.

**Plain Enter**: finish composing, send Enter key down and key up (keycode 66, meta state 0)
through the field. If both events were delivered: clear Ctrl state the same way as above
(always, not only for Ctrl-triggered sends), refresh the status bar, reset the suggestion
context. Handled if and only if both events were delivered.

**Ctrl+Enter**: identical to Plain Enter but both key events carry the Ctrl meta bits
(META_CTRL_ON | META_CTRL_LEFT_ON).

**Unsupported send**: used when the send method resolves to an editor action but the app is not
allowed one (not in the send-action list and no override row). The key is swallowed: nothing
is inserted, nothing is sent. If any Ctrl state was active it is cleared as above and the status
bar refreshed. Reported as handled. This is the "Enter does nothing at all" outcome the 2.0.3
changelog describes. In 2.x it is no longer reachable: a behavior resolves only for an app with
an override row (which makes the editor action allowed) or for a preset package (all of which
are send-action packages except Discord, and Discord's `auto` is Plain Enter). A rewrite that
keeps the rule should keep the branch anyway, as the guard against a future list mismatch.

### 3.5 Order of events on an Enter key down

Steps before the per-app logic are specified elsewhere; they are listed so the order is exact.

1. Alt+Enter layout switch (`layers-sym-alt.md`, `dictionaries-languages.md`): if enabled, Alt
   held, repeat count 0, and a field is editable, Enter is consumed for cycling the subtype and
   never reaches the steps below. Repeats of a consumed Alt+Enter are swallowed until key up.
2. A Sym page open with `sym_auto_close` closes (`layers-sym-alt.md`).
3. Deferred punctuation space debt is cancelled; Alt is cleared on boundary keys if configured;
   a Shift one-shot is consumed (cleared) and the status bar refreshed (`text-input.md`).
4. **Per-app Enter** (this section). "Ctrl active" means: the event carries Ctrl, or Ctrl is
   held, physically down, latched, one-shot, or latched by nav mode, evaluated both before the
   nav-mode prelude and again here. "Shift active" means: the event carries Shift, or Shift is
   held, one-shot (already consumed in step 3, so effectively only when the event itself says
   so), or the Shift layer is latched.
   a. If Sym is being held (a Sym chord is pending) and the app's extra shortcut is `sym_enter`:
      the Sym chord is marked as used (so releasing Sym will not toggle the Sym page), and the
      configured send method fires. Done.
   b. Wanted behavior `enter_newline`: if nav mode is active and Ctrl is active, send (Ctrl
      consumed); otherwise newline. Done.
   c. Wanted behavior `enter_newline_ctrl_send`: Ctrl active sends (Ctrl consumed); otherwise
      newline. Done.
   d. Wanted behavior `enter_send_shift_newline`: Ctrl active sends (Ctrl consumed); else Shift
      active gives a newline; else send. Done.
   e. No wanted behavior: if nav mode is active, the per-app step declines and nav mode's own
      Enter mapping applies (DPAD center by default, `trackpad-caret-nav.md`). Otherwise, if
      the field declares an editor action (section 3.9), request it (no Ctrl consumption).
      Otherwise decline.
5. When step 4 declines, Enter continues as ordinary text input (`text-input.md`): autocorrect
   on Enter, deferred punctuation, then the key passes to the app.

"Send" in b, c, d means: pick the send method (section 3.6) and run the mechanism from 3.4.

### 3.6 Choosing the send mechanism

| Send method | Mechanism |
|---|---|
| `plain_enter` | Plain Enter |
| `ctrl_enter` | Ctrl+Enter |
| `editor_action` | Editor action if allowed for this app, else Unsupported send |
| `auto` | Discord: Plain Enter. Every other app: Editor action if allowed, else Unsupported send |

`auto` is the pre-2.0.3 behavior kept as the default. The explicit methods exist because an app
that ignores the editor action swallows the key silently and the keyboard cannot detect it.

### 3.7 Behavior tables

With the send-on-Enter behavior (`enter_send_shift_newline`):

| Keys | Result |
|---|---|
| Enter | send |
| Shift+Enter | newline inserted by the keyboard |
| Ctrl+Enter | send, Ctrl state cleared afterwards |
| Sym+Enter with extra shortcut `sym_enter` | send; Sym release does not open the Sym page |
| Sym+Enter with extra shortcut `none` | Sym chord resolution happens first (section 3.8); if it produces nothing, Enter behaves as plain Enter above |

With `enter_newline_ctrl_send`:

| Keys | Result |
|---|---|
| Enter | newline |
| Shift+Enter | newline (Shift has no separate meaning) |
| Ctrl+Enter | send, Ctrl cleared |
| Enter in nav mode | nav mode has Ctrl latched, so Ctrl counts as active: send |

With `enter_newline` (only reachable through a stored override, section 3.11):

| Keys | Result |
|---|---|
| Enter, Shift+Enter, Ctrl+Enter outside nav mode | newline |
| Enter in nav mode with Ctrl active | send |

With `app_default` and no preset behavior: section 3.5 step e.

### 3.8 Sym+Enter as an extra send

Pressing Sym (keycode 63, scancode 253) with a field focused arms a pending Sym chord. Any key
pressed while it is pending is a chord and marks the chord as used; releasing Sym after an unused
chord toggles the Sym page, after a used chord it does nothing (`layers-sym-alt.md`). The
Sym+Enter send is checked inside the Enter handler, which runs after these earlier Sym chord
consumers, in this order: Sym edit shortcuts (Sym+C/V/X/A, only for those keys), launcher
shortcuts bound to the key (Sym+Enter can be a QuickLauncher trigger; when it is, and power
shortcuts are on, the launcher wins and the send never runs), and the Sym chord symbol lookup
(Enter has no chord symbol, so this falls through). The strings for a "SYM + Enter is already
used by QuickLauncher" conflict dialog exist but nothing shows them; the conflict is silent.

The extra shortcut is read only from override rows, so an app has it only when the user set it
(or the preset wrote a row and the user changed the shortcut field afterwards; the preset itself
always writes `none`).

### 3.9 The field's own editor action, and multi-line fields

The editor action of a field is resolved as: if the field's `imeOptions` has the "no Enter
action" flag, none; else the field's explicit `actionId` if non-zero, else the action bits of
`imeOptions`; the result counts only if it is one of Go, Search, Send, Next, Done, Previous
(ids 2, 3, 4, 5, 6, 7). Unspecified and None give "none".

There is no separate multi-line rule. A multi-line field that declares a Send action gets the
action on Enter exactly like a single-line one (upstream commit a2836f0 "detect masked IME
actions and fix multiline autocorrect"); a multi-line field with no action or the no-Enter-action
flag gets the generic newline path. This applies to every app, not only messengers: a search box
with a Search action runs the search on Enter, a login form with Next moves focus. The per-app
override changes only whether Enter should send, newline, or need Ctrl, and how the send is
delivered.

### 3.10 Nav mode

Nav mode (`trackpad-caret-nav.md`) latches Ctrl. Consequences in this section: under
`enter_newline_ctrl_send` and `enter_send_shift_newline`, Enter in nav mode sends (Ctrl is
active) and the send clears the Ctrl latch, which cancels the nav-mode notification and ends nav
mode. Under `enter_newline`, the same only when nav mode is active. With no per-app behavior,
nav mode keeps its own Enter mapping. A mapping "Ctrl+B" in the nav-mode command file is seeded
to the software keyboard mode toggle (section 9) when the slot is empty.

### 3.11 The settings screen

Reached from Settings > Keyboard hub row "Enter key behaviour" ("Configure app-specific Enter
and newline handling"), and from the tutorial's Enter card. It is one scrolling page:

1. Top bar with a "+" (Add app) icon.
2. Master switch "App-specific Enter behaviour", description "Use PhysiBoard overrides for
   selected messaging apps. Tested apps show the strategy PhysiBoard actually uses." Writes
   `app_enter_behavior_enabled`.
3. "Messaging preset" dropdown (labels in 3.1). Choosing a preset: stores it; then rewrites the
   override list so that every installed favourite has a row with the preset's behavior (send
   method `auto`, shortcut `none`), replacing any existing row for a favourite and keeping rows
   for non-favourites; the result is sorted and saved. Choosing "Custom" stores `custom` and
   leaves rows alone.
4. "App overrides" heading, then one card per row. A favourite's card is not editable: it shows
   the app icon (36 dp), label, package, and a status block, plus a "Manual override" text
   button that reveals the three dropdowns and a note "The curated strategy is usually the
   better default. Override only for app updates or special cases." Any other app's card shows
   the three dropdowns directly and a red delete icon ("Remove app"). Changing "Wanted
   behaviour" on any row also sets the preset to `custom`. Changing send method or extra
   shortcut does not.
5. An "Add app" row at the end ("Any app on your phone - Messenger, Slack, a fork of one of
   these"), which opens the same dialog as the "+" (added in 2.0.4 because nobody found the "+").

The status block on a favourite's card, in priority order:

| Condition | Badge | Line 1 | Line 2 |
|---|---|---|---|
| Discord, behavior send-on-Enter | "No override" | "App default: PhysiBoard does not intervene." | "Tested: Discord already behaves correctly for Enter sends and Shift+Enter newline. PhysiBoard does not intervene here." |
| Discord, behavior newline/Ctrl-send | "Plain Enter" | "Active: Enter newline, Ctrl+Enter sends." | "Tested: Newlines are inserted directly. Ctrl+Enter sends a plain Enter without Ctrl meta because Discord's app action only hides the keyboard." |
| behavior `app_default` | "No override" | "App default: PhysiBoard does not intervene." | "The app decides whether Enter sends or inserts a newline." |
| tested package | "App action" | "Active: <behavior>." | one of three "PhysiBoard inserts newlines directly and sends through the app's send action." / "PhysiBoard sends through the app's send action. Shift+Enter is inserted directly as a newline." / "Tested: PhysiBoard inserts Enter directly as a newline and does not trigger send." |
| other favourite (Signal, Messenger) | "App action" | "Experimentally active: <behavior>." | "Not confirmed: PhysiBoard is trying the app's send action and direct newline insertion." |

Note the Discord badge says "No override" while an override row with send-on-Enter exists and
does apply (override wins, `auto` gives Plain Enter, Shift+Enter gives a keyboard newline). The
text describes the intent (Discord's native behavior is the same) rather than the mechanism.

The **Add app** dialog is two steps. Step 1: title "Add app", a "Search apps" field, and a list
(240 dp to 420 dp tall) of every launchable app not already in the list, sorted by label
(case-insensitive), filtered live by label or package containing the query (case-insensitive,
trimmed); "No apps match that search." when empty. Tapping an app goes to step 2, whose title
is the app's label, with the note "For additional apps this is a target configuration first. The
curated messaging list uses tested strategies." and the three dropdowns, defaulting to
`enter_send_shift_newline`, `auto`, `none`. "Back" returns to step 1 (keeping the query),
"Cancel" closes in step 1, "Add app" saves the row and closes. The search query survives
rotation; the chosen app does not.

The "Wanted behaviour" dropdown offers only `app_default`, `enter_send_shift_newline`, and
`enter_newline_ctrl_send`; `enter_newline` can only arrive through a preset that no longer
exists in the UI, a backup, or a hand-written preference.

On opening, the list shown is: the stored rows, minus any whose package is no longer installed;
if that leaves nothing, the rows the current preset would create for installed favourites
(computed for display; nothing is saved until the user changes something). Rows are sorted
favourites first in favourite order, then the rest by label (case-insensitive), package name
when the label cannot be read; duplicates by package are dropped and uninstalled packages are
dropped on every save.

### 3.12 Storage

| Key | Type | Shape |
|---|---|---|
| `app_enter_behavior_enabled` | boolean | default true |
| `app_enter_behavior_preset` | string | one of the preset values; anything else reads and writes as `app_default` |
| `app_enter_behavior_overrides` | string | JSON array of objects `{"packageName": "...", "behavior": "...", "sendStrategy": "...", "additionalSendShortcut": "..."}` |

Reading the array: objects that are not JSON objects are skipped; a blank or repeated
`packageName` is skipped (first occurrence wins); a missing or unknown `behavior` reads as
`app_default`, `sendStrategy` as `auto`, `additionalSendShortcut` as `none`; a string that is not
valid JSON reads as an empty list. Writing: blank packages and duplicates are dropped, all three
values normalized as above, and the array is written in list order. The key is one of the
"content" keys the settings migration and reset preserve verbatim (`settings-catalog.md`).

First-run defaults (the one-shot baseline written before any settings are read, guarded by
`impact_defaults_applied`): preset `enter_send_shift_newline` and overrides
`[{"packageName":"com.whatsapp","behavior":"enter_send_shift_newline","sendStrategy":"auto","additionalSendShortcut":"none"},{"packageName":"com.discord",...same...},{"packageName":"com.google.android.apps.messaging",...same...},{"packageName":"com.instagram.android",...same...}]`.
These four rows exist whether or not the apps are installed; the screen hides the uninstalled
ones and drops them on the next save, but the resolver still honours them, so installing
WhatsApp later gets send-on-Enter immediately.

### 3.13 Facebook Messenger, unverified

`com.facebook.orca` is a favourite (top of the list, "Experimentally active" status) but is in
neither the preset list nor the send-action list. Consequences:

- Fresh install, Messenger installed, user never opens the Enter screen: no override row exists
  (the baseline has none for Messenger), the preset does not apply, editor action not allowed.
  Enter falls to section 3.5 step e: if Messenger's compose field declares a Send action it is
  requested; otherwise Enter goes through as a normal key. This is the state in which "Enter did
  nothing there" was reported (changelog 2.0.4).
- User opens the screen and picks any preset: an override row for Messenger is written with the
  preset's behavior. From then on the override wins, `auto` requests the editor action (allowed
  because a row exists), with Send (4) as the fallback id when the field declares none.

Whether Messenger's compose box acts on that request has not been verified on the device; the
status card says so ("Not confirmed"). The delivery that is known to work in apps that ignore
the action is `plain_enter`, which the user can choose per row.

## 4. Exact typing (raw mode)

### 4.1 What it turns off and what it keeps

Meant for terminals, SSH clients and code editors, where a correction turns `ls -la` into
`Ls -la.` In a listed app, every field that has no field-type restriction of its own is treated
as restricted with reason "app raw mode", which turns off:

| Feature | In a raw-mode app |
|---|---|
| Word suggestions on the bar | off (the bar shows none) |
| Autocorrect (on space, Enter, punctuation) | off |
| Auto-capitalization (sentence start, after Enter, at line start) | off, and it stays off even when `auto_capitalize_restricted_fields` ("Shift in all text fields") is on; that setting is about field types, not a whole-app opt-out (commit 863f2f4) |
| Double-space to period | off |
| Text expansion (abbreviation triggers, `expansion-clipboard-pickers-launcher.md`) | off: expansion refuses any restricted field, and raw mode is a restriction |
| Deferred punctuation spacing, comma space | governed by the same smart-feature flags, off |

Kept: character variations on long press (only email fields disable those), the Alt and Sym
layers, Sym chords, clipboard, dictation, per-app Enter behavior (a raw-mode messenger still
sends on Enter), the caps-lock key state, and the field's own `textCapCharacters` /
`textCapWords` / `textCapSentences` flags as reported (they are read, but the keyboard's own
auto-cap is what acts on them and it is off).

### 4.2 Precedence with field types

A field's own variation is classified first: URL fields, password fields (text, visible, web,
numeric), email fields and filter fields each get their own restriction reason. Raw mode is the
reason only when none of those match. The practical difference is auto-capitalization: with
"Shift in all text fields" on, a URL, email or filter field inside a raw-mode app is
auto-capitalized again (its reason is the field type, not raw mode), while a plain text field in
the same app is not. Password fields never are.

### 4.3 WebAPKs

The raw-mode check tests the exact package first, then the list expanded with WebAPK hosts
(section 2.2). Adding PersaLink (a WebAPK) therefore also puts `com.android.chrome` in raw mode,
and every Chrome tab with it. The list screen says so under the web-app row: "Web app - types
inside Chrome, so Chrome is excluded too" (host label resolved from the installed browser's
name, package name if it cannot be read). The reason this matters on the Titan: terminal-style
web apps return nothing when the keyboard asks for the text around the cursor, so every keystroke
looked like a sentence start and auto-cap produced `lIKE tHIS` until the host browser was
matched (D3).

### 4.4 The screen

Settings > Keyboard hub row "Exact typing" ("For terminals, SSH and code: what you type is what
goes in, nothing corrected or capitalised"), also reachable from settings search. It uses the
shared toggle-list screen (section 6.1) with title "Exact typing" and the long description
starting "In the apps you pick here, PhysiBoard sends every keystroke as-is...". Toggling a row
writes immediately.

### 4.5 Storage

`app_raw_mode_packages`: a string set of package names, default empty. Adding puts the exact
package chosen (the WebAPK shell name for a web app, never the host). Removing removes that
name. Preserved verbatim by settings migration and reset.

## 5. The other per-app lists

For completeness, the package-name lists that exist elsewhere, so a rewrite builds one list
mechanism:

| List | Key | Default | Specified in |
|---|---|---|---|
| Status bar shown only in these apps | `status_bar_apps` | messaging, mail and social apps | `status-bar.md` |
| Text box under the bar (dip) | `app_keyboard_nudge_packages` | `com.microsoft.teams`, seeded the first time the list is read | `status-bar.md` |
| Notification ring colour per app | `notification_ring_app_colors` | none | `device-backlight-ring.md` |
| Launcher shortcuts that open an app | `launcher_shortcuts` | none | `expansion-clipboard-pickers-launcher.md` |
| Exact typing | `app_raw_mode_packages` | none | this document |
| Enter overrides | `app_enter_behavior_overrides` | four rows | this document |

The dip list and exact typing share the toggle-list screen; status bar apps and ring colours use
the picker dialog.

## 6. Shared list screen and app picker

### 6.1 Toggle-list screen (exact typing, text box under the bar)

- Top bar with back arrow and the title; a description paragraph (16 dp side padding, 12 dp
  vertical) at the top of the list.
- While the app list loads, a centered spinner. The list is every app with a launcher activity,
  one entry per package, sorted by label case-insensitively; each entry carries its label, icon
  and, for a WebAPK, its host browser.
- Rows: 56 dp minimum height, 36 dp icon (rendered from a 96 px bitmap; blank space if none),
  label in one line, package name in one line in the secondary colour, an optional web-app note
  in the primary colour (up to two lines) when the screen supplies one, and a switch on the
  right. The whole row is tappable and toggles the switch.
- Enabled rows sort to the top (stable within each group), re-sorted after every toggle, so a
  row moves to the top the moment it is switched on and back to its alphabetical place when
  switched off.
- No search field, no "add" button, no "remove": everything installed is listed with a switch.
- The screen re-reads the stored set after every toggle; it does not listen for changes made
  elsewhere while open.

### 6.2 App picker dialog (status bar apps, ring colours)

A full-width dialog 90 % of the screen height: header "Select an app" with a "Cancel" text
button, a divider, a single-line search field with placeholder "Search apps...", and the list.
The list is the cached installed-app list (section 7): launchable apps, one per package, sorted
by label case-insensitively, each with a 48 dp icon, label and package name; filtered live by
label or package containing the query (case-insensitive; blank query shows all). Tapping a row
returns it and closes the dialog. System apps are flagged in the data but not shown differently.

## 7. Installed, uninstalled and updated apps

The installed-app list used by the picker is cached in memory. A broadcast receiver registered
at process start (exported, package scheme) listens for `android.intent.action.PACKAGE_ADDED`,
`PACKAGE_REMOVED`, `PACKAGE_REPLACED` and `PACKAGE_CHANGED`. On any of them:

1. The in-memory list is invalidated.
2. If the action is `PACKAGE_REMOVED` and the `EXTRA_REPLACING` extra is false (a true uninstall,
   not an update), every launcher shortcut whose target package is the removed one is deleted
   from `launcher_shortcuts`.
3. In the background, the package-change sequence is synced and the list rebuilt.

The sequence sync exists for changes missed while the process was dead (Android 8 and later):
the last seen change sequence number is stored in a separate preference file
`app_list_cache_prefs` under `package_change_sequence`, together with the boot count under
`package_change_boot_count` (`Settings.Global.BOOT_COUNT`). On a new boot the sequence restarts
at 0. When the system reports changed packages since the stored sequence, the cache is
invalidated and the new sequence stored; when it reports none, only the boot count is updated.
It also runs once at registration.

What is not done:

- Exact-typing entries are never removed on uninstall. An uninstalled package simply stops
  appearing in the list screen (it is no longer launchable) and stops matching. Reinstalling it
  restores the behavior with no user action. A WebAPK that is uninstalled keeps mapping its host
  to Chrome (the fallback), so the host browser stays in raw mode until the user removes the
  entry, which they cannot see any more (edge case E7).
- Enter override rows are not removed on uninstall either; the screen filters them on display
  and drops them on the next save. Until then the resolver still honours them (harmless, since
  the package cannot be current).
- An update (`PACKAGE_REPLACED`) changes nothing in any list.
- The dip and status-bar lists are not pruned.

## 8. App language

There is no per-app typing language. The only language-shaped per-app-looking key,
`app_language_tag` (a BCP-47 tag, blank meaning "follow the system"), is the language of
PhysiBoard's own settings UI (`app-shell.md`). Typing language and dictionaries are global and
switched by subtype (`dictionaries-languages.md`).

## 9. Software keyboard mode: auto-detection, device transitions, mode actions

This machinery decides whether the on-screen keyboard is shown. 3.0 has no on-screen keyboard;
it is described so the decision to drop it is informed.

### 9.1 Modes

`software_keyboard_mode`: `auto` (default), `force_hardware`, `force_virtual`. Labels "Auto",
"Hardware", "Virtual"; the tutorial's keyboard-mode dropdown is the only place that writes it
(the settings hub has no row for it), and a tutorial reset writes `auto`. Writing the mode also
clears the temporary override. A temporary override `software_keyboard_mode_runtime_override`
holds `force_hardware` or `force_virtual` (`auto` or absent means none) and always wins.

Effective mode = temporary override, else the configured mode if not `auto`, else the
auto-detector's answer.

### 9.2 Auto-detector

Answers `force_hardware` when the device is recognised as having a built-in keyboard (D1);
that identity is stable, so it is preferred over Android's own "should the input view be shown"
recommendation, which may briefly ask for the full on-screen keyboard while the keyboard service
starts. Otherwise: use Android's latest recommendation if one has been received since the last
input-device change; if none, show the virtual keyboard unless an alphabetic, non-virtual,
keyboard-like input device is connected. Any input device added, removed or changed forgets the
last recommendation. A "suppress until the input window hides" latch also forces hardware; it is
cleared when the input window hides and is never set in 2.x.

### 9.3 Device transitions

Every input-device add, remove or change: reset the accidental-press filter for that device
(remove and change only), forget the recommendation, and schedule a refresh 120 ms later
(re-scheduling cancels the pending one). The refresh re-runs the auto-detector and compares
with the last observed auto answer (first observed at service creation). If unchanged, nothing.
If changed: the temporary override is cleared, and 250 ms later the keyboard surface is switched
to the configured mode when it is not `auto`, else to the new auto answer. The surface switch:
clears pending text expansion, invalidates the rendered status bar snapshot, and asks the
visibility controller to show the input view for `force_virtual` or hide it otherwise, without
requiring an active text field. A change to either preference key from anywhere causes the
same surface switch after 32 ms (two UI frames, so a status-bar tap finishes dispatching before
its own surface is replaced).

### 9.4 Mode actions

"Toggle Keyboard Mode" flips the temporary override: from virtual to hardware; from hardware or
auto to virtual. It is reachable from:

- the status bar button "Keyboard mode" ("Temporarily open / close the on-screen keyboard
  (Ctrl+B)"), specified in `status-bar.md`;
- the command `pastiera.toggle_software_keyboard_mode` in the command palette and, via the nav
  mode mapping file, Ctrl+B in nav mode when that slot was empty at migration;
- the exported activity action `brobata.physiboard.action.TOGGLE_SOFTWARE_KEYBOARD_MODE`, also
  published as a launcher dynamic shortcut "Toggle Keyboard Mode" / "Temporarily open / close
  the on-screen keyboard". The activity performs the toggle and finishes at once.

Each path shows a toast "Keyboard mode: Virtual" / "Keyboard mode: Hardware" for a short
duration when `software_keyboard_mode_toggle_toasts` (default true, label "Mode toggle toast",
"Show a toast when switching between Virtual and Hardware.") is on.

On a Titan the auto-detector always answers hardware, so the only way to see the on-screen
keyboard is the toggle or `force_virtual`, and the next input-device event that changes the auto
answer (none does on a Titan, since the built-in keyboard never disappears) would clear it.

## 10. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `app_enter_behavior_enabled` | boolean | true | Master switch for every per-app Enter rule; off means Enter follows section 3.5 step e only, and send method / extra shortcut read as `auto` / `none` | Enter key behaviour | App-specific Enter behaviour |
| `app_enter_behavior_preset` | string | `enter_send_shift_newline` | Blanket behavior for the 9 preset packages (Discord excluded from send-on-Enter); choosing one rewrites favourite rows | Enter key behaviour | Messaging preset |
| `app_enter_behavior_overrides` | string (JSON array) | four rows, section 3.12 | Per-app wanted behavior, send method, extra shortcut | Enter key behaviour | App overrides |
| `app_raw_mode_packages` | string set | empty | Apps (plus WebAPK hosts) where suggestions, autocorrect, auto-cap, double-space period and expansion are off | Exact typing | Exact typing |
| `app_keyboard_nudge_packages` | string set | {`com.microsoft.teams`} seeded on first read | Apps whose keyboard request dips the bar | Text box under the bar | Text box under the bar (see `status-bar.md`) |
| `software_keyboard_mode` | string | `auto` | Whether the on-screen keyboard is shown | Tutorial keyboard-mode step | Keyboard Mode |
| `software_keyboard_mode_runtime_override` | string | absent | Temporary mode until the next device transition or configured-mode write | none (set by the toggle actions) | Toggle Keyboard Mode |
| `software_keyboard_mode_toggle_toasts` | boolean | true | Toast on toggle | Software keyboard settings | Mode toggle toast |
| `app_language_tag` | string | absent | Settings UI language (not per app) | Appearance (`app-shell.md`) | App language |
| `impact_defaults_applied` | boolean | false | Guards the one-shot first-run baseline that writes the preset and the four override rows | none | none |

Internal state, not settings: `app_list_cache_prefs` / `package_change_sequence` (int, 0) and
`package_change_boot_count` (int).

## 11. Titan-specific facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Titan 2 and Titan 2 Elite are recognised as having a built-in keyboard from the build fingerprint (brand, manufacturer, model, device, product, board or display containing "titan 2" or "titan2", Elite QWERTY detected separately); on them the software keyboard auto mode is always hardware, regardless of Android's input-view recommendation | commit 9f3c232 "stabilize keyboard mode transitions"; the auto-detector test pins unihertz/"Titan 2" to hardware and a Pixel 7a to virtual |
| D2 | PersaLink, the maintainer's main messenger, is a Chrome WebAPK; its fields report `com.android.chrome` | rules handed to this spec; changelog 1.0.3 |
| D3 | Terminal-style web apps hosted by Chrome return nothing for surrounding-text reads, so auto-cap fired on every key (`lIKE tHIS`) until the host browser was included in raw mode | changelog 1.0.3; commit ae92ece "apply Exact typing to a web app's host browser" |
| D4 | Sym is keycode 63 / scancode 253 and Fn arrives as Ctrl with scancode 251 and never sends key-up; "Ctrl active" for Enter therefore includes a held Fn, so Fn+Enter sends under the Ctrl-sends behaviors | rules handed to this spec; `docs/titan2elite/DEVICE.md` |
| D5 | Microsoft Teams leaves its compose box under the bar on a hardware-keyboard phone and is the seeded dip entry | changelog 2.0.7; commit 45ac5fc |
| D6 | On the Titan the built-in keyboard never disappears, so the device-transition refresh never changes the auto answer and a temporary virtual override survives until the next configured-mode write or toggle | inference from D1 and section 9.3; not observed |

## 12. Edge cases, quirks, known bugs

| # | Situation | Behavior | Why |
|---|---|---|---|
| E1 | Tap Shift (one-shot), then Enter, in a send-on-Enter app | The one-shot is consumed before the per-app step; unless the Enter event itself carries Shift, Enter sends rather than inserting a newline | The Shift-consume step precedes the Enter handler; upstream commit 110c58e "consume Shift on Enter" was about auto-cap. Needs device confirmation |
| E2 | Enter in a messenger that ignores the editor action, send method `auto` | Nothing happens: no send, no newline, no error | Android does not report whether the app acted; the user must pick "Plain Enter" |
| E3 | Preset chosen, app not in the 9 preset packages and no row | Preset does not apply; Enter is generic | Presets are limited to verified apps; the user adds a row to opt in |
| E4 | Override row set to "App default" for a preset package | The preset still applies | An `app_default` row falls through to the preset |
| E5 | Discord under preset "Enter newline, Ctrl+Enter sends" with no row (row deleted by hand) | Enter inserts a newline; Ctrl+Enter sends a plain Enter | Discord is excluded only from the send-on-Enter preset, not from this one; `auto` for Discord is Plain Enter whether or not a row exists, so Discord never receives an editor action under `auto` |
| E6 | Sym+Enter bound as a QuickLauncher trigger and an app with extra shortcut `sym_enter` | The launcher wins; the send never fires; no warning shown | The launcher check runs earlier; the conflict dialog strings are unused |
| E7 | WebAPK in the exact-typing list is uninstalled | The entry disappears from the screen but stays stored; its host falls back to `com.android.chrome`, which stays in raw mode | Host lookup of a missing package returns the default host; no pruning on uninstall |
| E8 | URL, email or filter field inside a raw-mode app, "Shift in all text fields" on | Auto-cap is on in that field | Field-type reason takes precedence over raw mode |
| E9 | Preset value `enter_newline_only` written by a backup | Reads as `app_default` | Not in the normalizer's accepted set |
| E10 | Behavior `enter_newline` in a row | Honoured by the resolver, displayed with its label, but cannot be chosen in the dropdown | Only three options are offered |
| E11 | Fresh install, WhatsApp not installed | Baseline still holds a WhatsApp row; the screen hides it; installing WhatsApp later gets send-on-Enter at once | Rows are filtered for display only |
| E12 | Toggling a row on the toggle-list screen | The row jumps to the top of the list immediately | Enabled-first sort re-runs after each toggle |
| E13 | Discord favourite card under send-on-Enter | Badge says "No override" while an override row exists and applies (Plain Enter on Enter, keyboard newline on Shift+Enter) | The card describes the visible result, which matches Discord's native behavior |
| E14 | Enter behavior master switch off | Send method also reads as `auto` and the extra shortcut as `none`; editor action eligibility still counts existing rows | Eligibility checks rows via the same switch, so it too is off; the field's own action still runs via step e |
| E15 | Nav mode active, no per-app behavior | Enter is nav mode's DPAD center, never the field's editor action | The per-app step declines in nav mode |
| E16 | Nav mode active, send-on-Enter app | Enter sends and nav mode ends (Ctrl latch cleared) | Ctrl active because of the nav-mode latch; sends consume Ctrl |
| E17 | Fn held (Ctrl scancode 251) then Enter in a send-on-Enter app | Sends, Ctrl consumed; the still-missing Fn key-up leaves the physical-Ctrl flag as `keys-and-modifiers.md` describes | D4 |
| E18 | App reports an empty package name (some system dialogs) | Treated as not listed | Blank package matches nothing |
| E19 | Editor action Send requested for an app with a row, field declares Go | Go is requested, not Send | The field's own action wins; Send is only the fallback |
| E20 | Dip/status-bar lists contain a WebAPK | Never matches | Only raw mode expands hosts |

## 13. Test cases

Package names below are the shipped lists; "rows" means the override list; the master switch is
on unless stated.

| # | Setup | Input | Expected |
|---|---|---|---|
| T1 | rows: WhatsApp Business (`com.whatsapp.w4b`) send-on-Enter; preset send-on-Enter; current app WhatsApp Business | resolve behavior | `enter_send_shift_newline` |
| T2 | no rows; preset send-on-Enter; current app WhatsApp Business | resolve behavior | nothing |
| T3 | rows: WhatsApp `enter_newline`; preset send-on-Enter; current WhatsApp | resolve behavior | `enter_newline` |
| T4 | no rows; preset send-on-Enter; current WhatsApp | resolve behavior | `enter_send_shift_newline` |
| T5 | no rows; preset send-on-Enter; current Discord | resolve behavior | nothing |
| T6 | rows: Discord send-on-Enter; current Discord | resolve behavior | `enter_send_shift_newline` |
| T7 | rows: WhatsApp `app_default`; preset send-on-Enter; current WhatsApp | resolve behavior | `enter_send_shift_newline` |
| T8 | rows: WhatsApp send-on-Enter; master switch off | resolve behavior | nothing |
| T9 | rows: WhatsApp; current package null | resolve behavior | nothing |
| T10 | no rows; current WhatsApp | resolve send method | `auto` |
| T11 | rows: WhatsApp with `plain_enter` | resolve send method | `plain_enter` |
| T12 | rows: WhatsApp `plain_enter`; master switch off | resolve send method | `auto` |
| T13 | no rows; current WhatsApp | editor action allowed? | true |
| T14 | no rows; current WhatsApp Business | editor action allowed? | false |
| T15 | rows: WhatsApp Business (any behavior) | editor action allowed? | true |
| T16 | rows: WhatsApp Business with `sym_enter` | resolve extra shortcut | `sym_enter` |
| T17 | no rows; current WhatsApp | resolve extra shortcut | `none` |
| T18 | current WhatsApp, behavior send-on-Enter, method `auto`, field declares Send | Enter down | one editor-action request with id 4; composing finished first; no newline committed |
| T19 | same as T18, field declares Go (2) | Enter down | editor-action request with id 2 |
| T20 | same as T18 | Shift+Enter (event carries Shift) | `\n` committed; no editor action |
| T21 | same as T18 | Ctrl+Enter | editor action 4; Ctrl latch, one-shot and nav latch cleared afterwards |
| T22 | current Discord, row send-on-Enter, method `auto` | Enter | Enter key down and up (keycode 66, meta 0) sent to the field; no editor action |
| T23 | current WhatsApp, row method `ctrl_enter` | Enter | Enter down/up with META_CTRL_ON and META_CTRL_LEFT_ON |
| T24 | current Slack (`com.slack`), row `enter_newline_ctrl_send`, method `editor_action` | Enter | `\n` committed |
| T25 | same as T24 | Ctrl+Enter | editor action (Send fallback 4 if Slack's field has none) |
| T26 | current `com.example.unknown`, no row, preset send-on-Enter, method resolves `auto`, field declares Send | Enter | editor action 4 (generic step e), Ctrl not touched |
| T27 | current `com.example.unknown`, no row, field declares nothing | Enter | not handled by the per-app step; generic newline path |
| T28 | current `com.example.unknown`, no row, field has the no-Enter-action flag and Search bits | Enter | not handled; generic path |
| T29 | current WhatsApp, row with `sym_enter`, Sym held (chord pending) | Enter | send via configured method; Sym chord marked used; releasing Sym does not toggle the Sym page |
| T30 | current WhatsApp, row `enter_newline`, nav mode active (Ctrl latched) | Enter | send; nav mode cancelled |
| T31 | current WhatsApp, row `enter_newline`, nav mode off, Ctrl held | Enter | `\n` committed |
| T32 | no per-app behavior, nav mode active, field declares Send | Enter | per-app step declines; nav mode mapping runs |
| T33 | preset write `enter_newline_only` | read preset | `app_default` |
| T34 | rows JSON `[{"packageName":"a"},{"packageName":"a","behavior":"enter_newline"},{"packageName":""},7]` | read rows | one row: `a`, `app_default`, `auto`, `none` |
| T35 | rows JSON `not json` | read rows | empty list |
| T36 | write rows [`b` (send-on-Enter, `ctrl_enter`, `sym_enter`), `b` (other), ` ` (blank)] | stored string | `[{"packageName":"b","behavior":"enter_send_shift_newline","sendStrategy":"ctrl_enter","additionalSendShortcut":"sym_enter"}]` |
| T37 | `org.chromium.webapk.a5d49fddf77614419_v2` | is WebAPK? | true; `com.termux` false |
| T38 | `com.termux` | host browser | none |
| T39 | `org.chromium.webapk.missing` (not installed) | host browser | `com.android.chrome` |
| T40 | expand {`com.termux`, `org.chromium.webapk.x`} | result | {`com.termux`, `org.chromium.webapk.x`, `com.android.chrome`}; {`com.termux`} unchanged |
| T41 | raw list {`org.chromium.webapk.x`}; current `com.android.chrome` | raw mode? | true |
| T42 | raw list {`com.termux`}; current `com.android.chrome` | raw mode? | false |
| T43 | raw list {`com.termux`}; current null or "" | raw mode? | false |
| T44 | raw-mode app, plain text field | flags | suggestions off, autocorrect off, auto-cap off, double-space off, variations on, reason "app raw mode" |
| T45 | raw-mode app, email field | flags | reason email; variations off; auto-cap follows `auto_capitalize_restricted_fields` |
| T46 | raw-mode app, plain field, `auto_capitalize_restricted_fields` on | auto-cap disabled? | true |
| T47 | non-raw app, email field, `auto_capitalize_restricted_fields` on | auto-cap disabled? | false |
| T48 | raw-mode app, text expansion configured, abbreviation typed | expansion | none |
| T49 | installed-app cache built; `PACKAGE_ADDED` for `com.example.new` | cache | invalidated (null until rebuilt) |
| T50 | launcher shortcut on key B targets `com.example.removed`; `PACKAGE_REMOVED` without replacing | shortcut | removed |
| T51 | same, but `EXTRA_REPLACING` true | shortcut | kept |
| T52 | raw list {`com.example.removed`}; `PACKAGE_REMOVED` | raw list | unchanged |
| T53 | build fingerprint unihertz / "Titan 2", Android recommends the input view | auto mode | hardware |
| T54 | build fingerprint google / "Pixel 7a", no recommendation, no external keyboard | auto mode | virtual |
| T55 | configured `auto`, previous auto answer virtual, new answer hardware | transition | to hardware, temporary override cleared |
| T56 | previous and new auto answers equal | transition | none |
| T57 | configured `force_virtual`, auto changed virtual to hardware | transition | to virtual, override cleared |
| T58 | first observation (no previous), new answer hardware | transition | to hardware |
| T59 | effective mode virtual | toggle | override `force_hardware`; effective hardware | 
| T60 | effective mode hardware or auto | toggle | override `force_virtual` |

## 14. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| Package name from the editor, remembered per field | keep | Everything per-app depends on it |
| WebAPK host expansion | keep | PersaLink is a WebAPK; without it exact typing cannot target it (D2, D3) |
| Expand hosts for the Enter overrides too | undecided | Not done in 2.x; a WebAPK messenger would need it, but it would also hit every Chrome tab |
| Per-app Enter: behaviors, send methods, override-beats-preset, Ctrl/Shift rules | keep | The maintainer's daily messengers depend on it; the precedence bug cost a release |
| Messaging preset and the shipped tested/favourite lists | keep, shrink | Keep the preset as "apply to installed favourites"; the tested/favourite/send-action triad can collapse to one list with a per-app "verified" flag |
| `enter_newline_only` preset and `enter_newline` behavior | drop | Unreachable in the UI; Ctrl-send with newline covers the need |
| Sym+Enter extra send | undecided | Cheap, but it silently loses to a QuickLauncher trigger; keep only if the conflict is surfaced |
| Editor action for unconfigured apps (search on Enter, Next) | keep | Generic Android behavior every app relies on |
| Discord special case (`auto` gives Plain Enter) | keep | Verified on Discord; costs one comparison |
| Facebook Messenger as a favourite | undecided | Unverified on the device; keep it as an "add app" candidate rather than a favourite until tested |
| Exact typing list and its effects, including expansion off | keep | Terminals and SSH on a hardware keyboard are the point of the device |
| Field-type precedence over raw mode (E8) | undecided | Arguably raw mode should win inside a raw app; decide when rewriting auto-cap |
| Toggle-list screen (enabled first, no search) | keep, add search | Several hundred apps with no search is slow; the picker already has one |
| App picker dialog | keep | Shared by status bar apps and ring colours |
| Package-change monitor and installed-app cache | keep | Needed for the picker and shortcut cleanup; small |
| Sequence-number sync across process death | undecided | Only matters if the cache is persisted; drop if the list is rebuilt on open |
| Pruning per-app lists on uninstall | undecided | 2.x keeps entries so reinstalls "just work"; E7 argues for pruning WebAPK entries at least |
| Per-app typing language | not present | Nothing to carry over |
| Software keyboard mode, auto-detector, device-transition policy, toggle activity, dynamic shortcut, `pastiera.toggle_software_keyboard_mode`, toast setting | drop | 3.0 has no on-screen keyboard; on the Titan the detector always answers hardware (D1, D6) and the toggle only ever shows a keyboard 3.0 will not have |
| Nav-mode Ctrl+B seeded to the mode toggle | drop | Same reason; free the slot |
| Status bar "Keyboard mode" button | drop | Same (`status-bar.md` decides the slot list) |
| Diagnostics snapshot of package, `inputType`, `imeOptions`, resolved action | keep | The only way an Enter-to-send report can be diagnosed (changelog 2.0.3, 2.0.4) |

## 15. Provenance

- app/src/main/java/brobata/physiboard/inputmethod/EnterBehaviorPolicy.kt
- app/src/main/java/brobata/physiboard/inputmethod/WebApkHost.kt
- app/src/main/java/brobata/physiboard/inputmethod/SoftwareKeyboardAutoDetector.kt
- app/src/main/java/brobata/physiboard/inputmethod/SoftwareKeyboardDeviceTransitionPolicy.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (constants, editor start, Enter handling, Sym chord handling, device listener, surface transitions, diagnostics snapshot, raw-mode auto-cap guard)
- app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt (built-in keyboard detection)
- app/src/main/java/brobata/physiboard/inputmethod/KeyboardVisibilityController.kt (input view evaluation)
- app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionController.kt (restricted-field guard)
- app/src/main/java/brobata/physiboard/core/InputContextState.kt
- app/src/main/java/brobata/physiboard/core/ModifierStateController.kt (Shift one-shot consume)
- app/src/main/java/brobata/physiboard/AppEnterBehaviorScreen.kt
- app/src/main/java/brobata/physiboard/AppRawModeScreen.kt
- app/src/main/java/brobata/physiboard/AppKeyboardNudgeScreen.kt
- app/src/main/java/brobata/physiboard/AppListHelper.kt
- app/src/main/java/brobata/physiboard/AppPickerDialog.kt
- app/src/main/java/brobata/physiboard/AppPackageChangeMonitor.kt
- app/src/main/java/brobata/physiboard/SoftwareKeyboardModeActions.kt
- app/src/main/java/brobata/physiboard/SoftwareKeyboardModeActionActivity.kt
- app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- app/src/main/java/brobata/physiboard/SettingsManager.kt (per-app keys, Enter storage and normalizers, raw-mode and nudge sets, software keyboard mode, first-run baseline, nav-mode Ctrl+B seed)
- app/src/main/java/brobata/physiboard/SettingsMigration.kt (content keys)
- app/src/main/java/brobata/physiboard/SettingsScreen.kt (Keyboard hub rows)
- app/src/main/java/brobata/physiboard/CustomizationSettingsScreen.kt, SettingsActivity.kt, TutorialActivity.kt (entry points, keyboard-mode dropdown)
- app/src/main/java/brobata/physiboard/commands/PhysiBoardCommandSource.kt, commands/CommandExecutor.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/SoftwareKeyboardModeButtonFactory.kt, StatusBarButtonsScreen.kt, NotificationRingScreen.kt (picker callers)
- app/src/main/AndroidManifest.xml (exported toggle activity)
- app/src/main/res/values/strings.xml
- app/src/test/java/brobata/physiboard/EnterBehaviorPolicyTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/WebApkHostTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SoftwareKeyboardAutoDetectorTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SoftwareKeyboardDeviceTransitionPolicyTest.kt
- app/src/test/java/brobata/physiboard/AppPackageChangeMonitorTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodServiceDeviceBehaviorTest.kt (Enter-related cases)
- PHYSIBOARD_CHANGES.md (1.0.0, 1.0.3, 1.2.1, 2.0.2, 2.0.3, 2.0.4, 2.0.7)
- README.md, docs/plans/rebuild-from-scratch.md, docs/plans/physiboard-roadmap.md, docs/spec/README.md, docs/spec/text-input.md, docs/spec/layers-sym-alt.md
- git log (commits 4a4ecab, ba5cc90, b161a4e, 67333a7, 863f2f4, ae92ece, 8b03e80, 266c1a5, 9f3c232, 45ac5fc, a2836f0, 110c58e)
