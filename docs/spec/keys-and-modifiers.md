# Keys and modifiers

This document specifies how PhysiBoard receives a physical key press on the Unihertz Titan 2
Elite, filters it, tracks modifier state (Shift, Ctrl, Alt, Sym, Fn), and decides whether the
key is handled by the keyboard, forwarded to the app, or dropped. It stops where a key has been
resolved to "type this character through the text pipeline" (see `text-input.md`), "look this key
up in a Sym or Alt layer" (see `layers-sym-alt.md`), or "move the cursor" (see
`trackpad-caret-nav.md`). Where the status bar and caret badge show modifier state, the rules for
what is shown are here; how it is drawn is in `status-bar.md`.

Terminology used throughout:

- **Held**: the key is physically down right now (a key-down was seen, no key-up yet).
- **One-shot** (also "armed", "sticky"): the modifier applies to the next key only, then clears.
- **Latched** (also "locked"): the modifier applies to every key until it is turned off.
- **Caps lock**: the latched form of Shift.
- **Editable field**: an app text box is focused and reports a non-null input type. Everything
  else (launcher, browser chrome, a list, the lock screen) is "no editable field".
- **Pass to app**: hand the event to the system's default handling, which delivers it to the
  focused app as if the keyboard had not intervened.

## 1. Event intake

### 1.1 What arrives

Every hardware key event carries: an Android keycode, a hardware scancode, an input device id,
a repeat count (0 for the initial press, 1, 2, ... for system auto-repeat while held), a meta
state bit set (Shift, Ctrl, Alt, Meta, Sym flags as the system computed them), the event time
(system uptime, milliseconds), and a unicode character the system derives from the keycode and
meta state via the device's key character map. PhysiBoard uses all of these. It reads the
scancode for one thing only: recognising the Fn key (section 3). Everything else keys off the
keycode.

### 1.2 Device identification

On start-up PhysiBoard classifies the phone from its build fingerprint (brand, manufacturer,
model, device, product, board, display strings, compared case-insensitively):

| Result | Rule |
|---|---|
| Titan 2 Elite QWERTY, profile id `titan2elite_qwerty` | any field contains `titan2elite_qwerty`, `titan2elite-qwerty` or `titan2eliteqwerty`; or the fingerprint is in the Titan family (contains `unihertz` or `titan`) and the display string contains `elite` or the board string contains `g72` |
| Titan 2 (untested), profile id `titan2` | Titan family and any field contains `titan 2` or `titan2`, and the Elite rule did not match |
| Unknown, profile id `unknown` | anything else |

The profile id selects the device Alt-layer asset (`devices/<profile>/alt_key_mappings.json`,
falling back to `devices/titan2/alt_key_mappings.json` for `unknown`). The preference
`physical_keyboard_profile_override` (values `auto`, `titan2`, `titan2elite_qwerty`; any other
value normalises to `auto`) replaces the detected profile. No profile causes any key event to be
rewritten: the Titan family delivers usable keycodes as-is, and the "remap" step in the input
path is an identity operation (D1).

An external keyboard is recognised as such (the input device reports itself as external), but it
receives no special treatment beyond the accidental-press filter's per-device bookkeeping.

### 1.3 Order of processing on key-down

The stages below run in this order. The first stage that says "consume" ends processing; the
event is reported to the app as handled and nothing later runs. "Pass to app" also ends
processing. Stages that neither consume nor pass simply fall through.

1. **Accidental-press filter** (section 11) on the raw event, before anything else.
2. **Bounce filter** (section 10).
3. **Screen trackpad trigger** (`trackpad-caret-nav.md`): the chosen trigger key (Space, a
   Shift, or Sym) is swallowed for up to 250 ms while the keyboard waits to see whether it is a
   hold; a release before that replays the original down (and up) through this same list with
   the trackpad step skipped, so a tap of the trigger key still types normally.
4. **Sym assistant timer**: a Sym down with repeat count 0 arms a 600 ms timer (section 4.4).
   Any other non-modifier key down while the timer is armed cancels it.
5. **Fn burst detection** (section 3), only while `fn_long_press_speech` is on.
6. **Field check**: decide whether an editable field is focused. If so, mark the input view as
   active and clear any suggestion action mode on the strip.
7. **Typing sound**: played for every repeat-0 key-down in an editable field except Back
   (sound choice is in `app-shell.md`).
8. **Emoji picker search capture**: when Sym page 4 (the emoji picker) is open with its search
   box focused, letters, Backspace and Ctrl shortcuts are redirected into that search box and
   consumed (details in `layers-sym-alt.md`).
9. **Text expansion**: in an editable field with no modifier active in any form (no meta bit,
   nothing held, armed or latched), the expansion engine gets a look at the key
   (`expansion-clipboard-pickers-launcher.md`).
10. **Sym toggle pending**: a Sym down with repeat count 0 in an editable field records "Sym
    toggle pending" and "no chord yet" (section 4.1).
11. **Sym edit chords**: Sym+C, Sym+V, Sym+X, Sym+A (section 4.2).
12. **Sym launcher chords**: Sym plus an assigned launcher key (section 4.3).
13. **Sym symbol chords**: Sym plus any other key (section 4.3).
14. **Back with an overlay open**: in an editable field, Back first closes a clipboard or
    picker overlay on the strip, then closes an open Sym page; either case consumes Back.
15. **Modifier bookkeeping** (section 5.2): the latched-layer tap-off check, the pre-hold
    snapshot, and the "other key pressed during hold" flag.
16. **Multi-tap cycle reset** for a different key (section 9).
17. **No editable field** branch (section 15). Nothing below runs without a field.
18. **Prelude**: if Ctrl is latched because nav mode is on, nav mode exits (a text field has
    focus now). Back passes to the app.
19. **Layout-switch chords**: Alt+Shift, Alt+Enter, Ctrl+Space (section 7.5).
20. **Boundary clean-up**: Enter and Backspace clear the deferred-punctuation state; Space and
    Enter clear Alt per `clear_alt_on_space` (section 6.4); Enter consumes a Shift one-shot.
21. **Enter as editor action** (`per-app-behavior.md`).
22. **Diagnostics notification** of the key event (section 16).
23. **Deferred space before text** (`text-input.md`) for keys with no Alt or Ctrl active.
24. **Forward-delete alternatives** (section 7.7).
25. **Text pipeline** (`text-input.md`, `autocorrect-suggestions.md`) for keys with no Alt
    active in any form: backspace undo, double-space period, smart quotes, auto-cap, boundary
    handling. Consumes if it handled the key.
26. **Vietnamese Telex** rewrite for keys with no Alt or Ctrl active.
27. **Main resolution** (section 7): modifier keys, Sym, swipe-to-delete keys, Alt layer, Ctrl
    mappings, multi-tap, long press, Shift one-shot and caps, variations, layout letter, fallback
    letter, else pass to app.

### 1.4 Order of processing on key-up

1. Accidental-press filter: swallow the up of a key whose down was suppressed.
2. A pending consumed Alt+Enter clears its "swallow repeats" flag and consumes this up.
3. Bounce filter: swallow the up of a suppressed down (with the exception in section 10.3).
4. Screen trackpad trigger release.
5. Sym release while the assistant timer is armed or has fired (section 4.4).
6. Fn release, only when `fn_long_press_speech` is on (section 3.3).
7. Emoji picker search: the matching key-up of a captured key is consumed.
8. No editable field: a Sym up clears the pending toggle flags, then nav mode gets Ctrl and
   nav keys (section 15); everything else passes to the app.
9. If there is no input connection at all, pass to app.
10. Diagnostics notification of the key-up.
11. Shift, Ctrl, Alt release handling (section 5), then pass to app.
12. Sym release: toggle a Sym page if the toggle is still pending and no chord was used
    (section 4.1). Always consumed.
13. Long-press timer cancellation for the released key (section 8.3). Consumed if a press was
    being tracked and no Sym page is open.
14. Otherwise pass to app, then ask the text expansion engine to refresh.

## 2. Modifier state model

Each of Shift, Ctrl and Alt has these independent facts:

| Fact | Meaning |
|---|---|
| pressed | a key-down was seen and no key-up yet (the keyboard's own bookkeeping) |
| physically pressed | same as pressed, kept separately for the status display |
| one-shot | next key only |
| latched | until turned off (for Shift this is caps lock) |
| last release time | the uptime of the last key-up, used for double-tap detection |
| latch from nav mode (Ctrl only) | the latch was created by nav mode, not by the user typing |
| layer latched (Shift and Alt only) | a visual "layer" latch produced by a quick double tap on release; see section 5.6 |

Shift is a three-state machine: OFF, ONE_SHOT, CAPS. Ctrl and Alt each have one-shot and
latched as separate booleans (one-shot is cleared whenever latched is set).

A single "last key was a modifier" memory records which modifier key was pressed last. A tap is
**consecutive** when the previous key event was the same modifier keycode and no other key
intervened. Double-tap detection requires a consecutive tap; any non-modifier key between two
taps of the same modifier breaks the pair (D-independent behaviour, tested).

Two timing constants govern every modifier:

| Constant | Value | Used for |
|---|---|---|
| Double-tap window | 500 ms | second tap must land within this of the first tap (Shift: measured down-to-down; Ctrl/Alt: measured from the first tap's release to the second tap's down; layer latch: measured release-to-release) |
| Hold threshold | 300 ms | a press held longer than this with no other key is an "intentional hold" |

## 3. The Fn key on the Titan

### 3.1 What the hardware delivers

D2 through D6 (section 19) describe the vendor's delivery model. In short: Fn is scancode 251;
it reaches apps as Ctrl (keycode 113, CTRL_LEFT) only after the user has enabled the vendor's
Fn-to-Ctrl remap; it never delivers a repeat-0 down or a key-up; a hold produces auto-repeat Ctrl
downs (repeat 1, 2, 3, ...) about every 50 ms starting about 400 ms into the hold; a quick tap
delivers nothing; a quick Fn+X chord delivers only X with the Ctrl meta bit set.

Consequences: PhysiBoard cannot tell "Fn went down" or "Fn came up". It can only see "Fn is
being held" (a burst of repeats) and "a key arrived with Ctrl meta" (a chord).

### 3.2 Fn recognition

A key event is treated as Fn-origin when its scancode equals `fn_speech_scan_code` (default
251) or its keycode is KEYCODE_FUNCTION (119). The scancode match is what makes the feature
survive the vendor remap: whatever keycode the vendor chooses, scancode 251 is still Fn.

### 3.3 Hold-Fn-to-dictate (burst detection)

Active only while `fn_long_press_speech` is on. For every Fn-origin key-down:

1. A 200 ms "burst reset" timer is restarted. When it expires with no further Fn event, the
   burst count returns to 0 and the "blocked" flag clears.
2. If the burst is not blocked, the count increases by one. When the count reaches 5, the
   burst becomes blocked, all Ctrl state and all Alt state (including "pressed") are cleared,
   the status display refreshes, and speech recognition starts (or stops, if it was already
   running; see `dictation.md`). Timing: 5 repeats at about 50 ms after a 400 ms onset is
   roughly 600 ms of hold.
3. Every Fn-origin down is consumed, whether or not it triggered anything. This is deliberate:
   letting the remapped Ctrl downs reach the modifier state machine leaves Ctrl "pressed" for
   ever, because the release never comes (D4, changelog 0.86 "Alt/Ctrl chords breaking after
   Fn presses").

Any non-Fn key-down while the count is above 0 sets the burst to blocked without resetting the
count. The Fn hold that continues afterwards is therefore never mistaken for a dictation request;
the chord key itself arrives with Ctrl meta and is handled as a Ctrl chord in the normal way.

An Fn-origin key-up (never delivered on the Titan, but handled for other hardware): the burst
timer is cancelled and the count reset immediately; if the burst had reached the trigger count,
the up is consumed so the modifier cannot be armed as a one-shot.

### 3.4 Fn with the feature off (default)

With `fn_long_press_speech` off (the default), Fn-origin events are not intercepted. A held Fn
then reaches the modifier state machine as a Ctrl key-down with repeat count above 0:

- The first repeat is treated as a Ctrl press: Ctrl becomes "pressed", "physically pressed",
  and one-shot armed, and the event passes to the app.
- Subsequent repeats are ignored because Ctrl is already pressed.
- No key-up ever arrives, so "pressed" and "physically pressed" stay set until something resets
  modifier state (field change, keyboard window hidden, Ctrl+Space, starting dictation, or the
  Alt+Ctrl chord).

While stuck: the status icon shows Ctrl as active; the next key is treated as a Ctrl one-shot
shortcut (for a letter, the Fn Layer mapping runs, for example E moves the cursor up); in a
numeric field every key is treated as a Ctrl chord; text expansion does not fire. This is the
bug the burst logic was written to contain, and it is only contained when the dictation setting
is on. Whether stock users ever hold Fn alone long enough to hit it needs device evidence.

### 3.5 Fn chords

A quick Fn+X chord arrives as X with Ctrl meta and no Fn event. It is handled as a physically
held Ctrl combination (section 7.3): by default passed to the app as Ctrl+X, or looked up in the
Fn Layer mappings when `nav_mode_ctrl_hold_enabled` is on.

### 3.6 Enabling the vendor Fn-to-Ctrl remap

The Fn Layer screen ("Fn Layer", the nav mode settings) has a "Set Fn key to Ctrl" card. Tapping
"Set Fn → Ctrl" writes `Settings.System` `fn_programmable_key_enable` = 1 and
`fn_programmable_key_function` = 1 (D7). Before the first write, the original values are captured
into `fn_ctrl_prev_captured` (boolean), `fn_ctrl_prev_enable` and `fn_ctrl_prev_function` (int;
the sentinel value Int.MIN_VALUE means "the system had no value"). The write goes through the
Settings API when "Modify system settings" is granted, otherwise through the paired embedded
ADB broker (`broker-privileged-toolbox.md`) as `settings put system <key> <value>`; with neither
available the card reports that permission is needed and opens the grant screen. Success is
confirmed by reading both keys back as 1. "Reset Fn key to default" writes the captured values
back (or 0/0 when nothing was captured) and clears the capture. The card shows "Fn is set to
Ctrl ✓" when both keys already read 1. The user is told a reboot may be needed. The same revert
is part of "Reset device settings to stock".

## 4. The Sym key

Sym is keycode 63 (KEYCODE_SYM), scancode 253 (D8). Unlike Fn it delivers a clean down and up,
so plain timers work (D9). Sym is treated as a "pure modifier" for the purposes of chord
detection, bounce-filter category, accidental-press exemption and the assistant timer.

### 4.1 Tap: open or cycle the Sym pages

In an editable field, a Sym down with repeat count 0 records "toggle pending" and "no chord
used". The down itself is always consumed. On the Sym up, if toggle is still pending and no
chord was used, the Sym page cycles (page 0 = closed, then the enabled pages in the user's
order; see `layers-sym-alt.md`). The up is always consumed. Sym pages therefore open on
release, not on press, so that Sym-plus-key chords can be typed without the page flashing open.

If Sym is pressed while Alt is physically held (Alt meta set on the Sym event), all Alt state is
cleared first, including "pressed", and the status display refreshes. Alt+Sym is the system's
language-switch chord and Alt must not stay armed after it.

Without an editable field a Sym up only clears the two pending flags (section 15 covers what
Sym does there).

### 4.2 Sym edit chords

With `sym_edit_shortcuts` on (default true), in an editable field, when Sym is pending or the
event carries the Sym meta bit, the key has repeat count 0 and Alt meta is not set:

| Chord | Effect |
|---|---|
| Sym+C | copy (the editor's context-menu copy) |
| Sym+V | paste |
| Sym+X | cut |
| Sym+A | select all |

The chord is marked used (so the Sym release will not open a page) and the key is consumed. With
the setting off these four chords fall through to section 4.3.

### 4.3 Sym chords: launcher keys and symbols

Still in an editable field, in this order:

1. If `power_shortcuts_enabled` (default true) and the key has an assigned launcher shortcut
   (`expansion-clipboard-pickers-launcher.md`), the chord is marked used and the shortcut runs;
   consumed when the shortcut handled it.
2. Otherwise, for any key that is not Sym and not a pure modifier, with repeat count 0, the
   chord is marked used and the key is looked up in the current Sym page (when the Device, Emoji
   or Symbols page is open) or the user's preferred chord page (when no page is open). Shift for
   the lookup is true when the event has Shift meta, Shift is one-shot, or caps lock is on. A
   found symbol is committed as text and the key is consumed. A key with no symbol on that page
   falls through to normal handling, but the Sym release still opens no page because the chord
   was marked used.

Sym chord detection relies on "toggle pending" (Sym is down right now) or on the system's Sym
meta bit. Whether the Titan sets the Sym meta bit on chorded keys has not been verified; the
pending flag alone is sufficient for the behaviour above.

### 4.4 Hold Sym for the assistant

With `sym_long_press_assistant` on (default false) and Sym not chosen as the screen trackpad
trigger, a Sym down with repeat count 0 arms a 600 ms timer. The 600 ms is chosen to sit well
above the trackpad's 250 ms hold and above any tap meant as "open the Sym page". The timer is
cancelled by the Sym release or by any non-modifier key down (that is a chord). Arming always
clears stale armed/fired flags first, so a hold whose release was lost (the assistant took
focus and the keyboard was torn down) cannot swallow the next Sym tap.

When the timer fires: the armed flag clears, "fired" is set, the pending toggle is cancelled,
and the assistant is launched already listening (`dictation.md`). If no assistant is available,
"fired" is cleared and a toast says "No voice assistant is set up on this device." The Sym
release after a fired hold is consumed without toggling a page.

The Key mapping screen lists Sym as "Symbol and emoji pages", adding "hold for the assistant"
and/or "hold for the trackpad" when those are configured.

## 5. Shift, Ctrl and Alt

### 5.1 Common rules

- A modifier key-down with repeat count above 0 (system auto-repeat while held) changes
  nothing: the modifier bookkeeping stage skips it and the state machine ignores a down while
  already pressed.
- Shift downs and ups, and Ctrl downs and ups, are passed to the app after the state update
  (the app sees the modifier as the system delivered it). Alt downs are consumed; Alt ups are
  passed to the app.
- The "last key was a modifier" memory is set by each modifier down and cleared by every
  non-modifier down (Sym counts as non-modifier here).

### 5.2 Bookkeeping at key-down (repeat 0)

For Shift, Ctrl or Alt:

1. **Latched-layer tap-off**: if this is a Shift key and the Shift layer latch is set, or an
   Alt key and the Alt layer latch is set, the layer latch clears, the release-to-release timer
   resets, that modifier's logical state is cleared entirely (Shift: OFF, no pressed flags; Alt:
   no one-shot, no latch, no pressed flags), the status display refreshes, and the key is
   consumed. Nothing else happens for this press. The pre-hold snapshot is discarded so a stale
   one-shot cannot be resurrected.
2. Otherwise a **snapshot** of all logical modifier state (Shift state, Ctrl one-shot, Ctrl
   latch, Ctrl latch-from-nav, Alt one-shot, Alt latch) is taken, the flags "status-bar
   interaction during hold" and "other key during hold" are cleared, and the key's down time is
   recorded.

For any other key with repeat count 0: "other key during hold" is set and both
release-to-release timers (Shift layer, Alt layer) reset.

### 5.3 Shift

**Down** (not already pressed): pressed and physically pressed become true. A tap is registered
(consecutive or not). Transition:

| Current | Condition | New |
|---|---|---|
| any | consecutive tap and this down is less than 500 ms after the previous Shift down | CAPS if current is not CAPS, else OFF |
| any | `shift_tap_latches` on | CAPS if current is not CAPS, else OFF |
| OFF | plain tap | ONE_SHOT |
| ONE_SHOT | plain tap | OFF |
| CAPS | plain tap | OFF |

If the transition took Shift out of ONE_SHOT, the auto-capitalisation suppression for the
current field session is set (`text-input.md`). The status display refreshes when the state
changed. The down passes to the app.

**Up** (was pressed): hold duration = this event's time minus the recorded down time.

- **Intentional hold** = a status-bar button was used during the hold, or (duration above
  300 ms and no other key was pressed during the hold). The logical state is restored from the
  snapshot (so holding Shift for a while and releasing it leaves exactly what was there before:
  a hold is not a tap), the Shift layer latch clears, pressed flags clear, status refreshes.
- Otherwise pressed flags clear (normal release). If the release is a **quick tap** (duration
  under 300 ms, no other key, no status-bar interaction): when the previous quick Shift release
  was at most 500 ms ago, the **Shift layer latch** is set (section 5.6) and the timer resets;
  otherwise this release time is remembered. A non-quick release resets the timer.

The up passes to the app.

Net user experience: tap Shift = next letter uppercase; tap Shift twice quickly = caps lock
(and the layer latch); tap Shift once more = everything off; hold Shift and type = the held
letters are uppercase via the system meta bit and the one-shot armed by the down is consumed
by the first letter; hold Shift and release without typing = nothing changes.

### 5.4 Ctrl

**Down** (editable field): if the event carries Alt meta (Alt physically held), Ctrl is not
already pressed, and `alt_ctrl_speech_shortcut` is on (default true), speech recognition
starts (or stops) and the key is consumed; no state change. Otherwise, if not already pressed:

| Current | Condition | New | Note |
|---|---|---|---|
| latched, from nav mode, input view not active | | off, nav mode deactivated and its notification cancelled | consumed |
| latched, not from nav mode | | off | passes to app |
| latched, from nav mode, input view active | (should not happen) | off, nav mode deactivated | passes to app |
| one-shot | consecutive tap, and now minus last release is under 500 ms, and last release is recorded | latched, one-shot off | |
| one-shot | otherwise | off | |
| off | `ctrl_tap_latches` on | latched | |
| off | consecutive tap within 500 ms of last release | latched | |
| off | otherwise | one-shot | |

A non-consecutive tap wipes the remembered release time first, so a Ctrl tap after typing a
letter can never pair with an older Ctrl tap. Pressed becomes true; the down passes to the app
unless consumed above.

**Up** (was pressed): the same intentional-hold rule as Shift (restore snapshot, clear pressed
flags, refresh). Otherwise a normal release: last release time = now, pressed flags clear,
refresh. In both cases, if another key was pressed during the hold and Ctrl is one-shot but not
latched, the one-shot clears: the down armed it, the chord used it, the release must not leave
Ctrl waiting for one more key. The up passes to the app.

Ctrl has no layer latch of its own; a third tap on a latched Ctrl simply un-latches it via the
table above.

### 5.5 Alt

**Down**: if the event carries Ctrl meta, Alt is not already pressed, and
`alt_ctrl_speech_shortcut` is on, speech starts/stops and the key is consumed. Otherwise an open
Sym page closes (status refresh), and if not already pressed the Ctrl table above applies with
Alt substituted (there is no nav-mode row; `alt_tap_latches` plays the role of
`ctrl_tap_latches`). The down is always consumed: the app never sees Alt go down, because on the
Titan Alt is the symbol-layer key and the system's Alt behaviours (the symbol picker popup) are
unwanted.

**Up**: as Ctrl, plus the quick-tap **Alt layer latch** on the second quick release within
500 ms, mirroring Shift (section 5.6). The up passes to the app.

Alt is also cleared by Space and Enter (section 6.4), by Ctrl+Space, by the Alt+Shift and
Alt+Enter layout chords, by Sym while Alt is held, by starting dictation, and by the Fn burst.

### 5.6 Layer latches (Shift layer, Alt layer)

The layer latch is a second, visual-level latch produced only by two quick releases of the same
modifier within 500 ms. Because the second down of that pair also produces the logical latch
(caps lock for Shift, Alt latch for Alt) through the down-side double-tap rule, a normal double
tap sets both. The layer latch matters in three places:

- A further tap of that modifier is the explicit "off" (section 5.2 step 1), consuming the key
  rather than passing it to the app.
- For Shift, the layer latch forces uppercase resolution of typed letters and long-press
  lookups exactly like caps lock, and is reported to the status bar as "shift layer latched".
- Adding a word from the suggestion strip clears both layer latches and restores the pre-hold
  snapshot.

Both layer latches, both release timers and the snapshot are cleared whenever modifier state
is reset (section 6.5).

## 6. Modifier consumption and clearing

### 6.1 Shift one-shot

Consumed (returns to OFF) by: the first letter typed through the layout (the committed text is
uppercase); a multi-tap commit; a long-press-capable key commit; a Telex rewrite; Enter (before
the editor-action decision); a Sym chord does not consume it. Requested by auto-capitalisation
(`text-input.md`): an auto-cap request arms ONE_SHOT only when the state is OFF, never over
CAPS, and never re-arms an existing one-shot. Backspace after auto-cap is handled in
`text-input.md`.

### 6.2 Ctrl one-shot

Consumed by the first key that goes through Ctrl resolution when Ctrl is not physically held and
not latched from nav mode (section 7.3), and by the release of a Ctrl that was used as a held
chord (section 5.4). It is not consumed by a key that reaches the "physical combo" pass-through
branch, because a physical press arms one-shot internally and the release clears it.

### 6.3 Alt one-shot

Consumed by the first key that enters Alt resolution (section 7.2) unless that key is the Space
or Enter that is allowed through by `alt_latch_stays_on_space`.

### 6.4 Space and Enter clear Alt

With `clear_alt_on_space` on (default true), a Space or Enter while Alt is one-shot or latched:

- If Alt is latched and `alt_latch_stays_on_space` is on (default false): only the one-shot
  clears; the latch survives and the Space or Enter is typed as a plain space or newline (it
  does not go through the Alt layer).
- Otherwise Alt one-shot and latch both clear and the key is typed plainly.

The status display refreshes either way. With the setting off, Alt+Space commits a single space
(section 7.2) and Alt stays as it was.

### 6.5 Full modifier reset

All modifier state resets (Shift to OFF, Ctrl and Alt one-shot and latch off, pressed flags
off, last-modifier memory cleared, layer latches and timers cleared, snapshot discarded, Sym
pages closed, pending long presses cancelled, variations dismissed, status refreshed) when:

- a text field is left (finish input, and finish input view when the input is finishing),
- the keyboard window is hidden (except during a status-bar nudge blink, `status-bar.md`),
- explicitly via the Ctrl+Space chord (partial, section 7.5).

"Preserve nav mode" applies to all three of the above: if Ctrl is latched (or was latched from
nav mode), the latch is kept and marked as from nav mode so nav mode survives the field change;
otherwise nav mode's notification is cancelled. Bounce and accidental-press filter state resets
on every start of input; the accidental filter also resets on finish input and whenever an input
device is added, removed or changed.

## 7. What modifiers do to a key

This section describes the main resolution stage (1.3 step 27) in order. Alt-layer content, Sym
page content and layout tables are in `layers-sym-alt.md`; only the decision order is here.

### 7.1 Modifier keys and Sym

Shift, Ctrl, Alt and Sym downs are handled as in sections 4 and 5. Then the two Titan
swipe-to-delete keycodes 322 and 404 (D10): when `swipe_to_delete` is on and
`swipe_to_delete_provider` is `titan2_keycode`, the word before the cursor is deleted and the key
consumed; in every other configuration the key is consumed and reported to diagnostics as
ignored, so those keycodes never reach the app. A key whose long press is already being tracked
(a repeat of a held key) is consumed.

### 7.2 Alt active

Alt is active when the event has Alt meta, or Alt is latched, or Alt is one-shot. In a numeric
field (`per-app-behavior.md`) every key uses the Alt layer even without Alt, unless Ctrl is
active in any form. When a Sym page is open and Ctrl is not active, the Sym page handles the key
first (`layers-sym-alt.md`).

For an Alt-active key (other than the Space/Enter allowed through by section 6.4): any pending
long press for that key is cancelled; the one-shot is consumed; Back passes to the app; Space
commits a single plain space (suppressing the system symbol picker); a layer value of
`__DPAD_UP__`, `__DPAD_DOWN__`, `__DPAD_LEFT__` or `__DPAD_RIGHT__` sends that D-pad key to the
editor; any other mapped value is committed as text (with auto-space and French-spacing rules
from `text-input.md`); an unmapped key passes to the app. Handled keys are consumed.

### 7.3 Ctrl active

Ctrl resolution runs when the event has Ctrl meta, or Ctrl is latched, or Ctrl is one-shot, or
(numeric field) Ctrl is active in any form including merely pressed.

Definitions: **physical combo** = Ctrl meta on the event or Ctrl pressed/physically pressed.
**Nav grid** = Ctrl latched from nav mode, or (physical combo and `nav_mode_ctrl_hold_enabled`
on). **Shortcut keycode** = with `layout_aware_ctrl_shortcuts` on, the keycode whose QWERTY
letter equals the letter the active layout prints on this key (so Ctrl+Z on QWERTZ sends the
app Ctrl+Z, not Ctrl+Y); otherwise the physical keycode. **Mapping keycode** = the physical
keycode on the nav grid, else the shortcut keycode.

1. Physical combo, not nav grid, and not a numeric field forcing a basic edit action: the event
   is forwarded to the editor as a Ctrl+shortcut-keycode key event (the original event with the
   keycode replaced and Ctrl meta added). Consumed. This is the default for a held Fn chord: the
   app's own shortcuts win.
2. Otherwise a Ctrl one-shot (not from nav mode) is consumed now and the status refreshes.
3. The mapping for the mapping keycode (section 12) is applied:

| Mapping type | Effect |
|---|---|
| `command` | run the named command if it is allowed on the nav-mode surface (`expansion-clipboard-pickers-launcher.md`); otherwise pass to app |
| `action` `expand_selection_left` / `expand_selection_right` | extend the selection one character |
| `action` `move_word_left` / `move_word_right` | move the caret one word; with Shift active (Shift pressed or Shift meta) extend the selection one word instead |
| `action` `expand_selection_word_left` / `expand_selection_word_right` | extend the selection one word |
| `action` `page_start` / `page_end` | send Ctrl+Home / Ctrl+End to the editor (with Shift meta when Shift is active); status refresh 50 ms later |
| `action` `media_play_pause` / `media_previous` / `media_next` | dispatch the media key through the audio service |
| `action` `copy` / `paste` / `cut` / `undo` / `select_all` | the editor's context-menu action |
| `action` anything else | pass to app |
| `native_ctrl` | forward as a Ctrl combo (as in step 1) |
| `keycode` DPAD_UP/DOWN/LEFT/RIGHT/CENTER, TAB, MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN, ESCAPE, FORWARD_DEL | send that key's down and up to the editor; for the eight navigation keys, Shift meta is added when Shift is active and the status refreshes 50 ms later |
| `keycode` anything else | pass to app |
| no mapping, Backspace | delete the selection if there is one, else delete the word before the caret |
| no mapping, Enter or Back | pass to app |
| no mapping, physical combo not from nav | forward as a Ctrl combo |
| no mapping, otherwise | pass to app |

In a numeric field, a mapped `copy`, `cut`, `paste` or `select_all` runs even for a physical
combo (so Fn+V pastes instead of typing the Alt-layer digit or sending Ctrl+V); a `native_ctrl`
mapping in a numeric field still forwards the raw combo.

### 7.4 Neither Alt nor Ctrl

1. The typed character is resolved: uppercase when Shift is one-shot, or the Shift layer latch
   is set, or caps lock is on and Shift meta is not set, or Shift meta is set. (Caps lock with
   Shift held gives lowercase.)
2. Long-press eligibility is computed (section 8.2).
3. Smart punctuation replacements (`text-input.md`) may consume the key.
4. Multi-tap keys (section 9): a repeat is consumed; a tap commits and consumes.
5. A long-press-capable key commits its character immediately and schedules the long-press
   timer (section 8.3); a Shift one-shot is consumed; consumed.
6. Shift one-shot on a layout letter: commit the uppercase letter, consume the one-shot.
7. Caps lock on a layout letter: commit the uppercase letter.
8. A character with variations (`layers-sym-alt.md`): commit it (the variation row appears on
   the strip).
9. An alphabetic keycode mapped in the layout: commit the layout character.
10. Any key whose system unicode character is a letter: commit it.
11. Otherwise pass to app (digits from the number row, punctuation without a layout mapping,
    Enter, Space, Backspace, navigation keys, and so on reach the app as ordinary key events).

Every commit above refreshes the status display 50 ms later (the "cursor update delay").

### 7.5 Layout-switch chords

| Chord | Setting (default) | Behaviour |
|---|---|---|
| Alt+Shift (either order, repeat 0, editable field) | `alt_shift_layout_switch` (false; true for installations that existed before the setting was introduced, recorded by `alt_shift_default_initialized`) | Alt and Shift state fully cleared; next input subtype; toast if `toast_on_layout_switch` (true); consumed |
| Alt+Enter (repeat 0) | `alt_enter_layout_switch` (false) | Alt cleared; next subtype; toast; consumed, and every Enter repeat until the Enter key-up is consumed too (the Enter up itself is consumed) |
| Ctrl+Space (Ctrl meta, pressed, latched or one-shot) | `ctrl_space_layout_switch` (true) | Alt cleared if active; Ctrl cleared, except that a user latch survives when `ctrl_tap_latches` and `ctrl_latch_stays_on_space` are both on and the latch did not come from nav mode; if the latch came from nav mode and is not kept, nav mode's notification is cancelled and nav state refreshed; next subtype; toast; consumed |

A fresh Enter down with repeat 0 always clears the "consume Enter repeats" flag first, so a
chord whose key-up was lost across a field change cannot swallow the next Enter.

### 7.6 Enter and Backspace housekeeping

Enter and Backspace clear the deferred-punctuation space state. Enter consumes a Shift one-shot
before the editor-action decision so that "Send" never leaves an armed Shift behind.

### 7.7 Forward-delete alternatives

Applied to Backspace (KEYCODE_DEL) with no text selected:

| Setting (default) | Trigger | Effect |
|---|---|---|
| `shift_backspace_delete` (false) | Shift meta on the event | delete the character after the caret |
| `alt_backspace_delete` (false) | Alt active (meta, latch or one-shot) | delete the character after the caret |
| `backspace_at_start_delete` (false) | no Shift meta, no Alt, and no text before the caret | delete the character after the caret |

The first two together never delete twice. With text selected, Backspace is left to the normal
path so the selection is deleted.

## 8. Key repeat and long press

### 8.1 System auto-repeat

The system repeats a held key about every 50 ms after 400 ms (D6). PhysiBoard treats repeats
(repeat count above 0) as follows: the bounce and accidental-press filters ignore them; modifier
keys and Sym ignore them; multi-tap keys consume them (holding a multi-tap key must not churn
through its variants); the Fn burst counts them; a key with a pending long press consumes them
(the timer decides the outcome, not the repeats); Enter repeats after a consumed Alt+Enter are
consumed; everything else re-enters the normal path on every repeat, so a held letter types
repeatedly and a held Backspace deletes repeatedly through the app.

The system also reports a "long press" callback for a held key. PhysiBoard answers it with
"handled" whenever the key has an Alt-layer mapping, which suppresses the system's accented
character popup; otherwise the default handling runs. That callback also passes through the
accidental-press filter and, if the keyboard view had been hidden while a text field kept its
connection, marks the input view active again.

### 8.2 PhysiBoard long press

`long_press_modifier` chooses what a long press produces. Eligibility (computed on key-down,
never for a key in an active multi-tap cycle):

| Mode | Eligible when |
|---|---|
| `alt` (default) | the key has an entry in the device Alt layer |
| `shift` | the key's system unicode character is a letter |
| `variations` | the character the key would type (given Shift/caps/one-shot) has a variation list |
| `sym`, `sym_symbols`, `sym_emoji` | the key has an entry on the long-press Sym page (page 1 emoji or page 2 symbols per `layers-sym-alt.md`), the shifted entry counting when Shift is active |

### 8.3 Timer and outcome

An eligible key commits its normal character at once and starts a timer of
`long_press_threshold` ms (read from the preference each time; clamped to 50 to 1000). If the
key is released first, the timer is cancelled and the committed character is reported to the
suggestion engine as typed. If the timer fires while the key is still down, the committed
character is replaced:

| Mode | Replacement |
|---|---|
| `alt` | delete 1 character, then commit the Alt-layer value with the auto-space and French-spacing rules |
| `shift` | delete 1 character, commit the layout's uppercase for the key (for an unmapped key, the uppercase of what was committed) |
| `variations` | replace the committed character with the first variation of the character (looked up in the case the key was pressed in), only if the text at the caret still matches what was committed |
| `sym*` | delete 1 character, commit the Sym page value (shifted entry when the press was shifted) with the auto-space and French-spacing rules |

A long press that produced a replacement reports the inserted character to auto-space tracking
and does not report the original as typed. In `shift` mode the committed letter is reported to
suggestions immediately on key-down as well (the result is still a letter).

Default threshold: the settings screen shows and stores 300 ms, but a never-written preference
is read by the long-press timer with a fallback of 500 ms. Until the user moves the slider once,
the effective long press is 500 ms while the screen says 300 ms. The tutorial's "Devs choice"
preset writes 200 ms and `variations`.

## 9. Multi-tap

Layouts may map a key to a list of taps (for example Norwegian, Turkish, German QWERTZ, Greek;
`layers-sym-alt.md`). A key with more than one tap is a "real multi-tap" key.

- First tap: commit tap 0 (uppercase if the resolved case is upper). Start a 400 ms window.
- Same key within the window: delete the previously committed text (its length, at least 1)
  and commit the next tap, wrapping around; the case chosen on the first tap is kept for the
  whole cycle; the window restarts. The replacement is done as one batch edit so apps such as
  Messages do not flicker.
- A different key, the window expiring, the keyboard window hiding, or leaving the field
  finalises the cycle.
- Repeats of the key while held are consumed.
- Long press: when the key is also long-press eligible, the timer of section 8.3 is scheduled on
  the committed text of every tap, and long press is otherwise suppressed for the key while its
  cycle is active.
- Exception: with an uppercase resolution (Shift meta, one-shot, layer latch or caps), a key
  whose later taps include the capital sharp S (ẞ) does not multi-tap at all; it commits the
  plain uppercase letter so "SS" in words stays "SS" on German QWERTZ.
- A Shift one-shot is consumed by the first tap.

## 10. Bounce filter (same-key debounce)

Off by default (`bounce_keys_enabled` false). When on, a repeat-0 key-down is rejected if the
same key (same device id, scancode and keycode) was accepted less than `bounce_keys_delay_ms`
ago (default 80, clamped 20 to 500), measured on event time. Categories, each with its own
switch:

| Category | Keys | Switch (default) |
|---|---|---|
| character | every key not listed below, plus comma, period, minus, equals, brackets, backslash, semicolon, apostrophe, slash, at, plus, star, pound | `bounce_keys_character_keys_enabled` (true) |
| modifier | Shift, Ctrl, Alt (left and right), Sym | `bounce_keys_modifier_keys_enabled` (false) |
| space | Space | `bounce_keys_space_enabled` (true) |
| enter | Enter | `bounce_keys_enter_enabled` (true) |
| backspace | Backspace | `bounce_keys_backspace_enabled` (true) |
| unsupported | Back, D-pad up/down/left/right, volume up/down, power | never filtered |

### 10.1 Behaviour

A rejected down is consumed and reported to diagnostics as
`bounce_keys:ignored:category=<c>:delta=<n>ms:threshold=<t>ms:id=<device>:<scan>:<key>`.

### 10.2 Repeats

System repeats are never filtered.

### 10.3 Key-up balancing

The key-up that matches a rejected down is consumed too, so the app never sees an up without a
down. Exception: if the rejected down arrived while the previously accepted down of the same
key was still held (no up seen yet), the up is not consumed, because that up belongs to the
accepted press. Filter memory clears on every start of input.

No settings screen exposes the bounce filter in 2.x; the keys exist, are backed up and restored,
and can only be changed through a settings import.

## 11. Accidental-press filter (overlapping keys)

Off by default (`overlapping_keys_enabled` false). The rule applies only to events from a
physical, non-virtual keyboard device (the input device reports the keyboard source and is not
virtual). When on: a repeat-0 key-down of a non-modifier key while any other non-modifier key
on the same device is still held is suppressed, consumed, and reported as
`accidental_keys:ignored:reason=overlapping_key:id=<device>:<scan>:<key>`. Its key-up is swallowed
as well. Modifier keys (every system modifier keycode, plus Sym and Function) are never
suppressed and never count as "held" for the rule. Repeats of a held key are not overlaps. Keys
on different devices do not interact. Overlap is allowed again as soon as the first key is
released. Held-key tracking is only kept while the rule is on; state resets on start and finish
of input and on any input device change.

Like the bounce filter, this has no 2.x settings screen (the switch was on the deleted
Modifiers screen and survives only as a preference).

## 12. Ctrl (Fn Layer) key mappings

### 12.1 Files

Defaults ship in the asset `common/ctrl/ctrl_key_mappings.json`. On first run the asset is
copied to `files/ctrl_key_mappings.json` in the app's private storage; that file is what the
keyboard loads, and the Fn Layer screen edits it. If the file cannot be read, the asset is used.
A version stamp `nav_mode_default_mappings_version` (current 3) migrates older files: any of
`KEYCODE_N`, `KEYCODE_M`, `KEYCODE_U`, `KEYCODE_I` that is missing or `none` gets the word-motion
defaults below, and `KEYCODE_B` missing or `none` gets the keyboard-mode toggle command;
`nav_mode_mappings_updated` records the time of the last write.

### 12.2 Format

```
{ "mappings": { "<KEYCODE_NAME>": { "type": "...", ... } } }
```

Recognised key names: `KEYCODE_A` to `KEYCODE_Z`, `KEYCODE_0` to `KEYCODE_9`, `KEYCODE_GRAVE`,
`KEYCODE_MINUS`, `KEYCODE_EQUALS`, `KEYCODE_LEFT_BRACKET`, `KEYCODE_RIGHT_BRACKET`,
`KEYCODE_SEMICOLON`, `KEYCODE_APOSTROPHE`, `KEYCODE_COMMA`, `KEYCODE_PERIOD`, `KEYCODE_SLASH`,
`KEYCODE_CTRL_LEFT`, `KEYCODE_EM`, `KEYCODE_MIC`. Unknown names are skipped.

| `type` | Extra field | Accepted values |
|---|---|---|
| `keycode` | `keycode` | `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`, `TAB`, `MOVE_HOME`, `MOVE_END`, `PAGE_UP`, `PAGE_DOWN`, `ESCAPE`, `FORWARD_DEL` (others are dropped) |
| `action` | `action` | any string; see section 7.3 for the ones that do something |
| `native_ctrl` | none | forwarded as a real Ctrl combo |
| `command` | `command` | a command id such as `pastiera.toggle_software_keyboard_mode` |
| `none` | none | the key has no Fn Layer mapping |

### 12.3 Shipped defaults

| Key | Mapping | Key | Mapping |
|---|---|---|---|
| Q | keycode ESCAPE | A | action select_all |
| W | action expand_selection_left | S | keycode DPAD_LEFT |
| E | keycode DPAD_UP | D | keycode DPAD_DOWN |
| R | action expand_selection_right | F | keycode DPAD_RIGHT |
| T | keycode TAB | G | none |
| Y | keycode PAGE_UP | H | keycode PAGE_DOWN |
| U | action expand_selection_word_left | J | keycode DPAD_LEFT |
| I | action expand_selection_word_right | K | keycode DPAD_DOWN |
| O | keycode DPAD_CENTER | L | keycode DPAD_RIGHT |
| P | action toggle_minimal_ui | Z | action undo |
| X | action cut | C | action copy |
| V | action paste | B | command pastiera.toggle_software_keyboard_mode |
| N | action move_word_left | M | action move_word_right |

`toggle_minimal_ui` is not one of the actions section 7.3 performs, so Ctrl+P falls through to
the app (a stale default).

## 13. Visible feedback

Every modifier change refreshes a status snapshot: caps lock, Shift physically pressed, Shift
one-shot, Shift layer latched, Ctrl latched, Ctrl physically pressed, Ctrl one-shot, Ctrl latch
from nav mode, Alt latched, Alt physically pressed, Alt one-shot, Alt layer latched, and the
current Sym page. Three surfaces read it.

### 13.1 System status bar icon

Each of Shift, Ctrl, Alt is reduced to off / active / locked: locked = caps lock (Shift) or
latched (Ctrl, Alt); active = physically pressed or one-shot. The 26 non-empty combinations
select one of 26 icons drawn in the phone's status bar through the input method's status icon
slot; all-off shows no icon, unless a Sym page is open, in which case a Sym icon is shown. The
icon is hidden entirely while the keyboard is forced into on-screen mode. Icon changes are
deduplicated (no re-show for the same icon).

### 13.2 Caret badge

A small overlay beside the text cursor (`trackpad-caret-nav.md` for placement and permission).
Items, in order:

| State | Glyph | Colour |
|---|---|---|
| caps lock | arrow | locked colour, full |
| Shift one-shot | arrow | armed colour, full |
| Shift physically pressed only | arrow | armed colour, faint |
| Alt latched | Alt glyph | locked, full |
| Alt one-shot | Alt glyph | armed, full |
| Alt physically pressed only | Alt glyph | armed, faint |
| Ctrl latched, not from nav mode | "CTRL" | locked, full |
| Ctrl one-shot | "CTRL" | armed, full |
| Ctrl physically pressed only | "CTRL" | armed, faint |
| any Sym page open | "SYM" | armed, full |

Full alpha 245/255, faint alpha 140/255. Armed colour `caret_badge_armed_color` default
0xFF2563EB (blue); locked colour `caret_badge_locked_color` default 0xFFDC2626 (red). A Ctrl
latch created by nav mode is deliberately not shown (it would pin a badge to the caret for as
long as nav mode is on). Held-only states are faint because they go away on their own.

### 13.3 Keyboard strip indicators

The LED-style indicators on the keyboard strip (`status-bar.md`) show one-shot and locked only,
never a plain hold, to avoid flashing on every press: Shift active = one-shot and not caps;
Ctrl active = one-shot and not latched; Alt likewise; Sym locked when page 2 is open.

### 13.4 Typing sound

The modifier keys belong to the "modifier" sound group (`app-shell.md`).

## 14. Keys passed to the app untouched

- Back, always (after closing an overlay or Sym page when one is open in an editable field).
- Shift and Ctrl downs and ups (after state update), Alt ups.
- Any key with no letter character and no handling above: digits, punctuation without a layout
  mapping, Space, Enter and Backspace (when the text pipeline did not consume them),
  navigation keys, Tab, Escape, media and volume keys.
- Unmapped Alt combinations, unmapped Ctrl one-shot combinations, and every physically held
  Ctrl combination by default (as a Ctrl+key event with the layout-aware keycode).
- Everything, when no text field is focused, except the nav-mode and shortcut keys of section 15.

## 15. No text field focused

With no editable field the keyboard is mostly transparent. On key-down, in order:

1. Back passes to the app.
2. Sym with `power_shortcuts_enabled` toggles **power shortcut mode**: if the mode is already
   on, it turns off; otherwise nav mode (if active) is exited and remembered, the mode turns on,
   a toast "Press shortcut key to launch" appears after 500 ms unless a key arrives first, and
   the mode expires after 5000 ms. Consumed.
3. **Nav mode keys** (`trackpad-caret-nav.md`), when `nav_mode_enabled` (default true) or nav
   mode is active: a Ctrl key, or any key while nav mode is active.
   - Ctrl down: consumed always. Latched: a single tap un-latches (nav mode off, keyboard
     hidden). Not latched: a consecutive tap within 500 ms of the last Ctrl release latches
     (nav mode on, keyboard shown); a first tap just records "pressed" and waits.
   - Ctrl up: consumed; release time recorded.
   - Other keys while nav mode is active: Enter sends D-pad centre; a key mapped in the Fn
     Layer file runs its mapping (keycode, action, native Ctrl combo, or an allowed command);
     an unmapped key falls through to the shortcuts below.
   - Any key-up while nav mode is active is consumed.
   On the Titan this means a double tap of a real Ctrl key. Fn produces no down or up, so nav
   mode cannot be toggled from Fn on the Titan; it can only be entered from a text field or
   through commands.
4. Without a Ctrl latch, a key with Sym meta that is assigned to the quick launcher: run it as
   a power shortcut if that mode is on, else as a launcher shortcut.
5. Without a Ctrl latch, with power shortcut mode on, a letter, Enter, Backspace or Space runs
   the assigned shortcut and ends the mode.
6. Without a Ctrl latch, with `launcher_shortcuts_enabled` (default false) and the foreground
   app being a launcher, a letter, Enter, Backspace or Space runs the assigned launcher shortcut.
7. Otherwise pass to app.

Key-up: Back passes; nav mode keys as above; otherwise pass.

## 16. Diagnostics

Every key-down that reaches the main path, every key-up in an editable field, every suppressed
event, and every Ctrl mapping that fires is reported to an in-app key logger (Diagnostics
screen) with keycode and name, scancode, device id, source, flags, repeat count, meta state,
raw and effective unicode character, the Shift/Ctrl/Alt meta bits, the latch and one-shot
flags, the Sym page, and a text describing the output (for example `expand_selection_left`,
`delete_last_word`, `sym_edit_copy`, `swipe_to_delete_ignored_native_ime`,
`shift_DPAD_LEFT`). A key-down whose handling takes longer than 16 ms is logged with its keycode,
repeat count and package.

## 17. Vendor side keys and vendor-owned bindings

Some keys never reach an input method; the vendor firmware consumes them and, for the
programmable ones, launches whatever `Settings.System` rows point at (D11).

| Key | Hardware | Vendor rows | PhysiBoard behaviour |
|---|---|---|---|
| Orange side key ("func1") | ff_key, scancode 249 | `func1_short_press_activity`, `func1_double_press_activity`, `func1_long_press_package` / `func1_long_press_activity`, `func1_shortcut_key_enable` | Optionally redirect the long press to PhysiBoard's assistant trigger: write the app's package and trigger activity into the long-press pair and set enable to 1. Original values are captured first (only if both are plain component names: letters, digits, `_`, `.`, `$`, at most 256 characters) so "Reset to stock" can restore them; a malformed original is not recorded and, on restore, dropped rather than written. Writes use the Settings API or the broker; a value that is not a plain component name is refused rather than escaped, because the broker path is a shell. |
| Fn | scancode 251 (FUNC3) | `fn_programmable_key_enable`, `fn_programmable_key_function` (1 = Ctrl, inferred), `fn_long_press_activity` | section 3.6 |
| Right Shift | keyboard matrix, scancode 54 | `shift_r_programmable_key_enable` | reported on the Key mapping screen as "Vendor remapping enabled" or "Types Shift"; not changed by PhysiBoard |
| Home | scancode 102 | `home_programmable_key_enable` | reported only |
| Recent apps | navigation key, scancode 580 | `recent_programmable_key_enable` | reported only |
| Back | scancode 158 | none | passed to the app |
| Volume up / down | gpio-keys 115 / 114 | none | never filtered, passed |
| Power | ff_key 116 | none | never filtered, passed |

The Key mapping screen ("Key mapping") lists every physical key with its hardware identity and
current binding assembled from both the vendor rows (readable without any permission) and
PhysiBoard's own settings, and links Fn to the Fn Layer screen, Sym and the orange key to Voice,
and Space to the screen trackpad screen. Keys nobody can change say so.

## 18. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `long_press_threshold` | long (ms) | 300 (timer falls back to 500 when unset) | long-press timer, section 8.3; clamped 50 to 1000, slider in 50 ms steps | Key Behaviour & Timing | Long Press |
| `long_press_modifier` | string: `alt`, `shift`, `variations`, `sym`, `sym_symbols`, `sym_emoji` | `alt` | what a long press produces | tutorial "Devs choice" preset and long-press selector (`app-shell.md`) | (long-press selector) |
| `fn_long_press_speech` | boolean | false | hold-Fn burst detection and dictation, section 3.3 | Voice | Long-press Fn for speech input |
| `fn_speech_scan_code` | int | 251 | scancode recognised as Fn | none (preference only) | none |
| `sym_long_press_assistant` | boolean | false | 600 ms Sym hold opens the assistant | Voice | Hold Sym for the assistant |
| `alt_ctrl_speech_shortcut` | boolean | true | Alt held plus Ctrl (or the reverse) starts dictation | none (preference only) | none |
| `clear_alt_on_space` | boolean | true | Space/Enter clear Alt | Smart Features | Release Alt with Space |
| `alt_latch_stays_on_space` | boolean | false | an Alt latch survives Space/Enter | none | none |
| `ctrl_latch_stays_on_space` | boolean | false | a Ctrl latch survives Ctrl+Space (with `ctrl_tap_latches`) | none | none |
| `shift_tap_latches` | boolean | false | single Shift tap toggles caps lock | none | none |
| `alt_tap_latches` | boolean | false | single Alt tap latches Alt | none | none |
| `ctrl_tap_latches` | boolean | false | single Ctrl tap latches Ctrl | none | none |
| `bounce_keys_enabled` | boolean | false | section 10 | none | none |
| `bounce_keys_delay_ms` | long | 80 (20 to 500) | section 10 | none | none |
| `bounce_keys_character_keys_enabled` | boolean | true | section 10 | none | none |
| `bounce_keys_modifier_keys_enabled` | boolean | false | section 10 | none | none |
| `bounce_keys_space_enabled` | boolean | true | section 10 | none | none |
| `bounce_keys_enter_enabled` | boolean | true | section 10 | none | none |
| `bounce_keys_backspace_enabled` | boolean | true | section 10 | none | none |
| `overlapping_keys_enabled` | boolean | false | section 11 | none | none |
| `nav_mode_enabled` | boolean | true | Ctrl double tap outside text fields, section 15 | Fn Layer | (nav mode switch, `trackpad-caret-nav.md`) |
| `nav_mode_ctrl_hold_enabled` | boolean | false | held Ctrl uses Fn Layer mappings instead of app shortcuts | Fn Layer | Ctrl-hold navigation |
| `layout_aware_ctrl_shortcuts` | boolean | false | translate Ctrl+letter through the active layout before forwarding | Fn Layer | Layout-aware app Ctrl shortcuts |
| `nav_mode_default_mappings_version` | int | 1 (current 3) | migration stamp for the mappings file | none | none |
| `nav_mode_mappings_updated` | long | unset | last write time of the mappings file | none | none |
| `sym_edit_shortcuts` | boolean | true | Sym+C/V/X/A | Customize SYM Layers (Edit Emoji Layer) | Sym+C/V/X/A: copy, paste, cut, select all |
| `power_shortcuts_enabled` | boolean | true | Sym-plus-key launcher chords and power shortcut mode | More Customization | SYM key shortcuts |
| `launcher_shortcuts_enabled` | boolean | false | bare letter shortcuts inside launcher apps | More Customization | (launcher shortcuts, `expansion-clipboard-pickers-launcher.md`) |
| `ctrl_space_layout_switch` | boolean | true | Ctrl+Space cycles input language | Input Languages | Ctrl+Space Layout Switch |
| `alt_shift_layout_switch` | boolean | false (true for pre-existing installs) | Alt+Shift cycles input language | Input Languages | Alt+Shift Layout Switch |
| `alt_shift_default_initialized` | boolean | false | one-time migration flag for the row above | none | none |
| `alt_enter_layout_switch` | boolean | false | Alt+Enter cycles input language | Input Languages | Alt+Enter Layout Switch |
| `toast_on_layout_switch` | boolean | true | toast after a layout-switch chord | Input Languages | Layout Switch Toast |
| `shift_backspace_delete` | boolean | false | section 7.7 | Smart Features | Shift + Backspace |
| `alt_backspace_delete` | boolean | false | section 7.7 | Smart Features | Alt + Backspace |
| `backspace_at_start_delete` | boolean | false | section 7.7 | Smart Features | Backspace at line start |
| `physical_keyboard_profile_override` | string: `auto`, `titan2`, `titan2elite_qwerty` | `auto` | which device Alt-layer asset loads | Built-in Keyboards | Keyboard profile |
| `titan2_layout_enabled` | boolean | true on a Titan (unset means "is this a Titan") | aligns the on-screen keyboard with the Titan 2 physical keys | Built-in Keyboards | Titan 2 Layout Alignment |
| `swipe_to_delete` | boolean | false | keycodes 322/404 delete the last word | Keyboard swipe screen (`trackpad-caret-nav.md`) | (swipe to delete) |
| `swipe_to_delete_provider` | string: `titan2_keycode`, `native_ime` | `native_ime` | whether the keycodes or the IME gesture provide swipe-to-delete | Keyboard swipe screen | (provider) |
| `fn_ctrl_prev_captured` | boolean | false | whether the original Fn rows were captured | none | none |
| `fn_ctrl_prev_enable` | int | Int.MIN_VALUE (unset) | captured `fn_programmable_key_enable` | none | none |
| `fn_ctrl_prev_function` | int | Int.MIN_VALUE (unset) | captured `fn_programmable_key_function` | none | none |
| `caret_badge_armed_color` | int (ARGB) | 0xFF2563EB | caret badge colour for one-shot and held | caret badge screen (`trackpad-caret-nav.md`) | (armed colour) |
| `caret_badge_locked_color` | int (ARGB) | 0xFFDC2626 | caret badge colour for latched and caps | caret badge screen | (locked colour) |

Rows marked "none" for screen have no user interface in 2.x. The tap-latch and latch-stays
rows and the two filters were adjustable on a Modifiers screen that was deleted (commit
"refactor(modifiers): delete the Modifiers screen, keep its behaviour"); the values are still
honoured, backed up, and restored.

## 19. Titan device facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Titan 2 family needs no key-event remapping; keycodes arrive usable as-is. | comment in the device identification source; test "theTitanFamilyNeedsNoKeyEventRemapping" |
| D2 | The built-in keyboard is kernel device `TitanKey`, `/dev/input/event5`, vendor/product 0x2533; scancodes: 16 to 25 = Q W E R T Y U I O P; 30 to 38 = A S D F G H J K L; 14 = DEL; 56 = ALT_LEFT; 44 to 50 = Z X C V B N M; 253 = Sym; 28 = ENTER; 42 / 54 = SHIFT_LEFT / SHIFT_RIGHT; 158 = BACK; 102 = HOME; 57 = SPACE; 580 = APP_SWITCH; 251 = Fn (FUNC3). | docs/titan2elite/DEVICE.md, scancode map from TitanKey.kl |
| D3 | There is no Ctrl key in hardware; Fn becomes Ctrl only through the vendor's programmable-key setting. | DEVICE.md "No Ctrl in hardware" |
| D4 | The vendor layer never delivers the app-side initial down (repeat 0) or any key-up for Fn. A quick Fn tap delivers nothing to apps. Quick Fn+X chords deliver only X with META_CTRL_ON set. | DEVICE.md "Fn event delivery model (measured 2026-08-19)"; changelog 0.86 "Fixed: Alt/Ctrl chords breaking after Fn presses" |
| D5 | Holding Fn delivers auto-repeat KEYCODE_CTRL_LEFT events with scancode 251, repeat 1, 2, ..., the first flagged as long press, every ~50 ms starting ~400 ms into the hold. | DEVICE.md; source comment "5 repeats at ~50ms adds ~200ms of hold beyond ~400ms, ~600ms total" |
| D6 | System key repeat timeout 400 ms, repeat delay 50 ms. | DEVICE.md (KeyRepeatTimeout=400ms, KeyRepeatDelay=50ms) |
| D7 | The vendor key configuration is a plain `Settings.System` table writable with WRITE_SETTINGS: `{key}_programmable_key_enable` = 0/1, `{key}_programmable_key_function` = int (observed fn=1 means Ctrl, sym=2), `{key}_shortcut_key_enable`, `{key}_{short,long,double}_press_activity` / `_package`, for keys fn, func1, func2, home, recent, shift_r, sym. Function value 1 = Ctrl is inferred from one device and a reboot may be needed. | DEVICE.md "Vendor key-config"; Fn Layer screen source comment; string `fn_ctrl_map_description` |
| D8 | Sym is keycode 63 with scancode 253 (vendor keycode AGUI_SYM in the key layout). | DEVICE.md; the keyboard's own constant for Sym; Key mapping screen "scancode 253 (AGUI_SYM)" |
| D9 | Unlike Fn, Sym delivers a clean down and release, so a plain timer detects a hold. | source comment on the Sym assistant timer; changelog 1.0.7 "hold Sym" |
| D10 | The Titan 2 delivers keycodes 322 and 404 for the keyboard's swipe-to-delete gesture. | swipe-to-delete keycode set and the `titan2_keycode` provider; test "swipeToDelete_handlesBothKnownSwipeKeyCodes" |
| D11 | The orange side key (func1, ff_key scancode 249) never reaches an input method; the vendor launches the configured package/activity directly; stock points its long press at the Gemini app's entry activity. | source comment on the side-key manager; changelog 1.0.7; Key mapping screen "ff_key 249" |
| D12 | Vendor packages: `com.agui.keyboard` (FUNC3 / AGUI_SYM translation layer), `com.agui.shortcutsettings` (the "Shortcut keys" settings UI), `com.agui.spacebarkey`. `show_ime_with_hard_keyboard` = 0 on the device. | DEVICE.md |
| D13 | Companion input devices: `touchPad` event4 (capacitive touch layer on the keys), `fts_ts` event6 (touchscreen and gesture keys), `ff_key` event7 (scancode 249). Volume keys are gpio-keys 115 / 114; Power is ff_key 116. | DEVICE.md; Key mapping screen |
| D14 | Build fingerprints: a Titan 2 Elite exposes `titan2elite_qwerty` in at least one field, or leaks "elite" in the display string or "g72" in the board string within a Unihertz/Titan fingerprint; model string `Titan 2`, build `Titan 2 Elite_V02.00.02`, Android 16. | device identification rules and tests; DEVICE.md |
| D15 | The screen is 1080x1200 at density 300, about 574 x 640 dp: wide and short, which is why settings text must wrap. | DEVICE.md; changelog 0.86 "settings text clipped" |

## 20. Edge cases, quirks and known bugs

| Situation | Behaviour | Why |
|---|---|---|
| Fn held alone with `fn_long_press_speech` off (default) | Ctrl becomes pressed and one-shot; never released until a modifier reset; the next key runs the Fn Layer mapping; status icon shows Ctrl active | the vendor never sends the Fn release (D4); the containment only runs when the dictation setting is on (section 3.4) |
| Fn held with the setting on, then a letter pressed while still holding | the letter is a Ctrl chord (pass-through or Fn Layer per settings); the burst is blocked; no dictation; later Fn repeats are swallowed until 200 ms after the last one | chord keys carry Ctrl meta; the burst blocks on any other key |
| Fn held with the setting on for a long time | dictation starts once at the fifth repeat; nothing else happens while the hold continues | the burst blocks itself after triggering |
| Fn tapped quickly | nothing at all reaches the keyboard | D4 |
| Nav mode toggle from Fn | impossible: nav mode needs a Ctrl down and up | D4 |
| Long-press threshold never adjusted | the timer waits 500 ms while the screen shows 300 ms | two different fallbacks for the same preference key (section 8.3) |
| Shift tapped, then a letter, then Shift tapped again | the second tap is a plain tap (one-shot again), not caps lock | the letter broke the consecutive pair |
| Shift double-tapped, then tapped a third time | third tap clears caps and the layer latch and is consumed (the app does not see that Shift down) | latched-layer tap-off (section 5.2) |
| Shift held over 300 ms with nothing typed, then released | state returns to what it was before the press | intentional-hold restore; a hold is not a tap |
| Shift held under 300 ms, one letter typed during the hold | letter uppercase, one-shot consumed by it, nothing left armed | the down armed one-shot, the letter consumed it |
| Shift held over 300 ms, two letters typed during the hold | both uppercase; release is a normal release; nothing armed | other-key flag prevents the hold restore; the first letter consumed the one-shot, the second used Shift meta |
| Ctrl tapped, then Space | with `ctrl_space_layout_switch` on the language cycles and Ctrl clears; the Ctrl one-shot is not used as a Ctrl+Space shortcut | the layout chord runs before Ctrl resolution |
| Alt one-shot, then Space | a plain space; Alt clears | `clear_alt_on_space` default |
| Alt latched, then Space, with `alt_latch_stays_on_space` on | plain space; latch stays | section 6.4 |
| Alt+Sym | Alt state cleared before the system's language switch; Sym down consumed | section 4.1 |
| Alt held plus Ctrl (or Ctrl held plus Alt) | dictation toggles; no modifier state change | `alt_ctrl_speech_shortcut` default true; on the Titan "Ctrl" is Fn, whose chords have only the meta bit, so in practice this is Alt held then Fn held long enough to repeat |
| Sym tapped while holding a Sym chord in mind, but the chorded key has no symbol on the page | the key types normally; the Sym release still opens no page | the chord was marked used before the lookup |
| Sym held 600 ms with the assistant enabled and Sym also the trackpad trigger | no assistant; the trackpad wins | the assistant timer is never armed when Sym is the trackpad trigger |
| Sym pressed while the emoji picker's search box has focus | Sym still toggles pages; letters go to the search box | Sym and pure modifiers are excluded from search capture |
| Same key pressed twice within 80 ms with bounce keys on | second press and its release dropped | section 10 |
| Bounce filter rejects a down while the first press is still held | the eventual release is delivered, not swallowed | section 10.3 |
| Two letters overlapping with the overlap rule on | second letter and its release dropped; the first letter's auto-repeat still works | section 11 |
| Ctrl+P (default mapping `toggle_minimal_ui`) | passes to the app | the action is not implemented in the Ctrl resolution table |
| Held Ctrl (Fn chord) plus Backspace | forwarded to the app as Ctrl+Backspace | physical combo pass-through wins over the "delete last word" rule; delete-last-word applies only to one-shot or latched Ctrl |
| Ctrl latched from nav mode, then a text field gains focus | nav mode exits on the first key-down in the field | prelude step |
| Leaving a text field while Ctrl is latched | the latch survives as a nav-mode latch; nav mode resumes if it was on before the field | "preserve nav mode" reset |
| Keycodes 322 / 404 with swipe-to-delete off | consumed and logged as ignored; the app never sees them | section 7.1 |
| Uppercase German QWERTZ key whose taps include ẞ | no multi-tap cycling; plain uppercase letter | section 9 exception |
| Multi-tap key held down | only the first tap commits; repeats are consumed | section 9 |
| Bare letter with no text field, in a launcher, `launcher_shortcuts_enabled` off | passes to the launcher | section 15 |
| Whether the Titan sets the Sym meta bit on chorded keys | unknown; chords work through the "toggle pending" flag regardless | needs device evidence |
| Whether `fn_programmable_key_function` = 1 means Ctrl on all firmware | inferred from one device; a reboot may be needed | D7 |
| Changelog 0.86 says hold-Fn dictation is on by default on first install | the preference default is false and no first-run baseline sets it | code default; needs a decision for 3.0 |

## 21. Test cases

Each case is a sequence of key events (keycode, action, repeat count, meta, time in ms) and an
expected outcome that a JVM test can assert on the modifier state, the consumed flag, or the
committed text.

| # | Input sequence | Expected |
|---|---|---|
| T1 | Shift down t=0, Shift up t=50 | Shift ONE_SHOT; pressed false |
| T2 | T1, then A down t=100 | committed "A"; Shift OFF |
| T3 | Shift down t=0, up t=50, Shift down t=200, up t=250 | Shift CAPS; Shift layer latched |
| T4 | T3, then Shift down t=400 | Shift OFF; layer latch cleared; key-down consumed |
| T5 | Shift down t=0, up t=50, A down/up t=100, Shift down t=200 | Shift OFF (second tap not consecutive) |
| T6 | Shift down t=0 (state OFF), Shift up t=400, no other key | Shift OFF (hold restored); pressed false |
| T7 | Shift down t=0, A down t=100 with Shift meta, A up, Shift up t=400 | committed "A"; Shift OFF; not restored (other key during hold) |
| T8 | Shift down t=0, up t=50, Shift down t=600 (consecutive) | Shift OFF (second tap outside 500 ms toggles one-shot off) |
| T9 | Ctrl down t=0, up t=50 | Ctrl one-shot; pressed false; last release 50 |
| T10 | T9, then Ctrl down t=200 | Ctrl latched, one-shot off |
| T11 | T10, then Ctrl down t=400, up | Ctrl off |
| T12 | Ctrl down t=0, up t=50, A down t=100 (Fn Layer default) | select_all performed; Ctrl one-shot cleared |
| T13 | A down t=0 with Ctrl meta, `nav_mode_ctrl_hold_enabled` off | forwarded to app as Ctrl+A; consumed; no mapping run |
| T14 | as T13 with `nav_mode_ctrl_hold_enabled` on | select_all performed |
| T15 | Z down with Ctrl meta, active layout QWERTZ, `layout_aware_ctrl_shortcuts` on | forwarded as Ctrl+Y keycode (the key printing Z) |
| T16 | Ctrl down t=0, A down t=100 (Ctrl meta), A up, Ctrl up t=150 | Ctrl one-shot false after the up (shortcut used during hold) |
| T17 | Alt down t=0, up t=50 | Alt one-shot; Alt down consumed |
| T18 | T17, A down | Alt-layer value for A committed; Alt one-shot false |
| T19 | Alt down, up, Alt down t=200, up | Alt latched; Alt layer latched |
| T20 | T19, Space down, `clear_alt_on_space` on, `alt_latch_stays_on_space` off | Alt off; Space passes as plain space |
| T21 | T19, Space down, `alt_latch_stays_on_space` on | Alt latch true; one-shot false; Space plain |
| T22 | Alt down (Ctrl meta set), `alt_ctrl_speech_shortcut` on | dictation start requested; consumed; Alt state unchanged |
| T23 | Sym down r=0, Sym up | Sym page cycles 0 to 1; both events consumed |
| T24 | Sym down r=0, C down r=0, C up, Sym up, `sym_edit_shortcuts` on | copy performed; no Sym page opened |
| T25 | Sym down r=0, A down r=0 with no launcher shortcut on A, preferred chord page emoji | emoji for A committed; Sym up opens no page |
| T26 | Sym down r=0 with Alt meta | Alt state cleared including pressed; consumed |
| T27 | Sym down r=0 with `sym_long_press_assistant` on, no other key, t=600 | assistant launched; Sym up consumed; no page toggle |
| T28 | Sym down r=0 with the assistant on, A down at t=100 | timer cancelled; A handled as a Sym chord |
| T29 | `fn_long_press_speech` on: Ctrl-keycode downs with scancode 251, repeat 1..5 at t=400,450,500,550,600 | consumed each; dictation starts on the fifth; Ctrl and Alt state clear |
| T30 | as T29 but E down (Ctrl meta) at t=470 | burst blocked; no dictation; E handled as a Ctrl chord |
| T31 | as T29 but repeats 1..3 then nothing for 200 ms, then repeats 1..3 | no dictation (each burst below 5) |
| T32 | `fn_long_press_speech` off: Ctrl down scancode 251 repeat 1, no key-up ever | Ctrl pressed true, one-shot true, remains until reset |
| T33 | Bounce on, delay 80: A down t=0, A up t=20, A down t=50, A up t=70 | second down consumed; second up consumed; app sees one press |
| T34 | Bounce on: A down t=0, A up t=20, A down t=100 | second down accepted |
| T35 | Bounce on: A down t=0 r=0, A down t=60 r=1 | repeat not suppressed |
| T36 | Bounce on, modifier category off: Shift down t=0, up, Shift down t=30 | second Shift accepted |
| T37 | Bounce on: A down t=0 (held), A down t=30 r=0, A up t=200 | second down consumed; the up at 200 is delivered |
| T38 | Overlap on, physical device: A down, S down (A still held) | S down consumed; S up consumed; A up delivered |
| T39 | Overlap on: A down, A up, S down | S accepted |
| T40 | Overlap on: A down, Shift down | Shift not suppressed |
| T41 | Overlap on: A down device 1, S down device 2 | S accepted |
| T42 | Multi-tap key K with taps [a, b, c]: K down t=0 | "a" committed |
| T43 | T42, K down t=200 | "a" deleted, "b" committed |
| T44 | T42, K down t=600 | "a" kept, new "a" committed (new cycle) |
| T45 | T42, K down r=1 at t=100 | consumed; text unchanged |
| T46 | Long press mode `alt`, threshold 300: A down t=0 (A has Alt value "#"), A up t=100 | "a" committed; timer cancelled; "a" reported typed |
| T47 | as T46 but A up t=400 | at t=300 "a" replaced by "#" |
| T48 | Long press mode `shift`: A down, timer fires | "a" replaced by "A" |
| T49 | Backspace down with Shift meta, `shift_backspace_delete` on, no selection | one character after the caret deleted; consumed |
| T50 | Backspace with a selection, any forward-delete setting | not intercepted |
| T51 | No text field: Ctrl down t=0, up t=50, Ctrl down t=200 | nav mode latch on; keyboard shown; downs consumed |
| T52 | No text field, nav mode on: E down | DPAD_UP sent to the editor |
| T53 | No text field, `power_shortcuts_enabled` on: Sym down | power shortcut mode on; consumed; toast after 500 ms; mode off at 5000 ms |
| T54 | Keycodes 322 then 404 with `swipe_to_delete` off | both consumed; nothing deleted |
| T55 | Keycode 322 with `swipe_to_delete` on and provider `titan2_keycode`, text "hello world|" | "hello |" (last word deleted) |
| T56 | Enter down with Shift ONE_SHOT | Shift OFF before editor-action handling |
| T57 | Ctrl latched, Ctrl+Space, `ctrl_tap_latches` and `ctrl_latch_stays_on_space` on | latch kept; language cycled |
| T58 | Ctrl latched from nav mode, Ctrl+Space | latch cleared; nav notification cancelled; language cycled |
| T59 | Numeric field: V down with Ctrl meta, default mappings | paste performed (not the Alt digit, not Ctrl+V forwarded) |
| T60 | Finish input while Ctrl latched | latch survives marked from nav mode; Shift and Alt cleared; layer latches cleared |

## 22. Keep / Drop for 3.0

| Item | Decision | Reasoning |
|---|---|---|
| Fn burst detection by scancode 251, 5 repeats, 200 ms reset, every Fn event consumed | keep | the only correct way to see Fn on this hardware; make it unconditional so the stuck-Ctrl bug of section 3.4 cannot exist |
| `fn_speech_scan_code` as a setting | drop (hard-code 251) | Titan-only; the scancode is fixed by the key layout |
| `fn_long_press_speech` switch | keep | users may not want dictation on Fn; the consumption of Fn events must not depend on it |
| Vendor Fn-to-Ctrl remap card with capture and reset | keep | without it Fn never reaches apps; capture/restore is needed because it survives uninstall |
| Sym tap toggles on release; Sym chords (edit, launcher, symbol) | keep | the chord-without-flashing design is core Titan UX |
| Hold Sym for assistant (600 ms) | keep | works with the clean Sym up/down; conflict rule with the trackpad trigger stays |
| Shift/Ctrl/Alt one-shot, double-tap latch, 500 ms and 300 ms windows, consecutive-tap rule, hold restore | keep | the whole typing model rests on it |
| Layer latches (Shift layer, Alt layer) as a second flag | undecided | they only add "third tap clears" and the uppercase forcing for Shift; 3.0 could fold them into the logical latch |
| `shift_tap_latches`, `alt_tap_latches`, `ctrl_tap_latches`, `*_latch_stays_on_space` | drop | no UI since the Modifiers screen was deleted; defaults are the only tested path |
| `clear_alt_on_space` | keep | on by default and relied on for symbol typing |
| Alt+Ctrl dictation chord | undecided | on the Titan it is Alt held plus an Fn hold long enough to repeat; hold-Fn already dictates |
| Ctrl mapping file, its five types, migration, per-user copy | keep | Fn Layer is the Titan's cursor and edit surface; drop the unimplemented `toggle_minimal_ui` default |
| `nav_mode_ctrl_hold_enabled`, `layout_aware_ctrl_shortcuts` | keep | both change what Fn chords do |
| Nav mode toggled by Ctrl double tap outside text fields | undecided | unreachable from Fn on the Titan (D4); keep only if a real Ctrl key or a command can toggle it |
| Numeric-field Ctrl forcing of copy/cut/paste/select all | keep | tested, fixes Fn+V in number fields |
| Ctrl+Space language switch | keep | on by default and the only always-available switch chord |
| Alt+Shift and Alt+Enter language switch | undecided | off by default; Alt+Shift also collides with the system chord |
| Long press with four modes and a slider | keep, fix the 300/500 default mismatch | used daily; one default only |
| Multi-tap | keep | needed by multi-tap layouts; the ẞ exception must survive |
| Bounce filter | undecided | accessibility value, no UI today; keep only with a screen |
| Accidental-press (overlap) filter | undecided | same as bounce |
| Swipe-to-delete keycodes 322/404 | keep the consumption, decide the provider | the keycodes must never leak to apps; whether the keyboard or the IME gesture deletes is a trackpad question |
| System status-bar modifier icon (26 combos) and caret badge | keep | the only feedback with no on-screen keyboard |
| Keyboard strip LED indicators | see `status-bar.md` | |
| Minimal Phone keycodes 666 / 667 (`KEYCODE_EM`, `KEYCODE_MIC`) | drop | not a Titan |
| Device profile detection for `titan2` and `unknown`, `physical_keyboard_profile_override`, `titan2_layout_enabled` | drop the override and the on-screen alignment; keep Elite detection only as a sanity check | 3.0 is Titan 2 Elite only and has no on-screen keyboard |
| Software-keyboard modifier paths (synthetic key dispatch, 300 ms Sym hold on the soft keyboard, virtual Alt mappings) | drop | no on-screen keyboard |
| Power shortcut mode (Sym outside text fields, 5 s) and launcher-only shortcuts | see `expansion-clipboard-pickers-launcher.md` | |
| Key mapping inventory screen | keep | it is the only place that explains what Fn, Sym and the orange key currently do |
| Orange side key redirection with component-name validation | keep | the only way to give the side key a use; the validation is a security requirement |
| Right Shift / Home / Recent vendor flags | keep read-only display | nothing to do with them beyond showing |
| Diagnostics key logger | keep | device evidence for 3.0 comes from it |

## 23. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/ModifierStateController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ModifierKeyHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/KeyboardEventTracker.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/BounceKeyFilter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AccidentalKeyPressFilter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AccidentalKeyPressPolicy.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/MultiTapController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (key dispatch, constants, status icon, Sym assist, Fn burst, modifier release, resets, software-keyboard modifier paths)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AltSymManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/SymEditShortcuts.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/NavModeHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/LauncherShortcutController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ScreenTrackpadController.kt (trigger-key and threshold parts)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/CaretBadgeController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/CaretBadgeView.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt (snapshot and indicator parts)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/NavModeController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/SymLayoutController.kt (key-up and chord resolution)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/VendorSideKeyManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SystemChangeManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/toolbox/KeyInventory.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/mappings/KeyMappingLoader.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt (keys, defaults, clamps, mapping file handling)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsCatalog.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsBaseline.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardTimingSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/HardwareKeyboardSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyMappingScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/NavModeSettingsScreen.kt (Fn-to-Ctrl and setting rows)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TutorialActivity.kt (Devs choice preset)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/CustomInputStylesScreen.kt (setter references only)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/DiagnosticsScreen.kt (key logger registration only)
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/ctrl/ctrl_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/devices/ (listing)
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml (labels quoted above)
- /home/disdiqqq/projects/pastiera/docs/titan2elite/DEVICE.md
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/ModifierStateControllerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/AccidentalKeyPressFilterTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/BounceKeyFilterTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterCtrlHoldNavModeTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterModifierE2ETest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterShortcutKeysTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterForwardDeleteAlternativesTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/MultiTapControllerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/SymEditShortcutsTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/DeviceSpecificTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/data/mappings/KeyMappingLoaderTest.kt
- git log (commit messages a3f30a3, dedfb5a, 470e076, f3a430e, 24b4898, 1d7dd78, 5f63d1b, de25ddf, 4eca184, ed49638, 54b5fdc, 7a416e3, 27f90ed, d8d03ed, 36b2877, 7878d13, ef222dc, 9673649, 4342a26, 8fad010)
