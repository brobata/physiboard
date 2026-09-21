# Trackpad, caret badge and nav mode: moving the cursor without touching the text

This document describes every way PhysiBoard moves the text cursor, selects text or reports
modifier state without the user touching the text itself on the Titan 2 Elite:

- the **screen trackpad**: hold a key and the whole display becomes a cursor pad;
- the **keyboard-surface swipe**: the capacitive layer over the physical keys, as upstream
  Pastiera used it to accept suggestions and delete words, and how much of that survives on
  the Elite;
- the **caret badge**: the small floating glyphs beside the text cursor that report which
  modifier is armed, locked or held;
- **nav mode**, called "Fn Layer" in the user interface: the mode in which the letter keys
  become arrows, page keys, selection and editing commands;
- the **touch-screen-awake** pulse that keeps the display from timing out while the strip is
  being touched.

Key event ordering, modifier state machines and the Fn burst are specified in the keys
document; the strip is in the status bar document; the suggestion slots and the add-word
candidate are in the autocorrect document; the ADB broker is in the broker document. They are
referenced here only where the behavior in this document depends on them.

Everything below is the 2.x behavior on the Titan 2 Elite unless a row says otherwise.

## 1. Vocabulary

- **Trigger key**: the key the user holds or taps to open the screen trackpad. Space by
  default; Left Shift, Right Shift, either Shift, or Sym selectable.
- **Hold mode**: the trackpad is open only while the trigger key is held.
- **Sticky mode**: the trackpad stays open after a double tap or single tap of the trigger key
  until the user explicitly leaves it.
- **Step**: the number of pixels of finger travel that produces one cursor move.
- **Overlay**: a window PhysiBoard draws above the app being typed into. Both the screen
  trackpad and the caret badge are overlays; both need the "Display over other apps"
  permission (`android.permission.SYSTEM_ALERT_WINDOW`).
- **Third**: the keyboard-surface swipe divides the touch surface into a left, centre and
  right third and reports which third a swipe started in.
- **Armed**: a modifier that applies to the next key only (one-shot). **Locked**: a modifier
  that stays on until turned off (caps lock, a latched Alt or Ctrl). **Held**: a modifier key
  physically down.
- **Nav mode** / **Fn Layer**: a latched Ctrl created by double-tapping Ctrl with no text
  field focused. While it is on, letter keys run the Fn Layer map instead of typing.
- **Ctrl-hold navigation**: an option that makes a physically held Ctrl inside a text field
  use the same Fn Layer map instead of passing Ctrl+letter to the app.
- **Fn Layer map**: the per-letter table of what the letter does in nav mode. Shipped as
  `common/ctrl/ctrl_key_mappings.json` in the assets, copied on first run to
  `ctrl_key_mappings.json` in the app's private files directory, and edited there.

## 2. The screen trackpad

### 2.1 What it is

While the trigger key is active, PhysiBoard adds a transparent full-screen window of type
`TYPE_APPLICATION_OVERLAY` above the current app. Finger drags on that window are converted
into DPAD key events sent to the editor through the input connection, so the cursor moves in
every app that understands arrow keys, terminals included. Holding Shift while dragging
sends the DPAD events with Shift set, which extends the selection.

The feature is off by default in the app's own defaults but the first-run baseline that a
fresh install stamps turns it on (`screen_trackpad_enabled` = true, `screen_trackpad_step_px`
= 32), so on a Titan it is effectively on from the first launch (changelog 1.0.4 "screen
trackpad on"). It is also turned on automatically the first time the overlay permission is
obtained through the broker (section 2.9).

### 2.2 Which key is the trigger

| `screen_trackpad_trigger_key` value | Trigger matches |
|---|---|
| `space` (default) | KEYCODE_SPACE |
| `shift_left` | KEYCODE_SHIFT_LEFT |
| `shift_right` | KEYCODE_SHIFT_RIGHT |
| `shift_either` | KEYCODE_SHIFT_LEFT or KEYCODE_SHIFT_RIGHT |
| `sym` | KEYCODE_SYM or raw keycode 63 (the Titan's Sym, D3) |

A Space down that already carries Ctrl or Alt in its meta state is never a trigger: Ctrl+Space
and Alt+Space go straight down the normal pipeline (language switching and the like).

Choosing Sym as the trigger disables "hold Sym for the assistant": the 600 ms Sym hold timer
is never armed while the trackpad is enabled and set to Sym (changelog 1.0.7, "the hold is
unavailable while Sym is the screen trackpad trigger").

The trackpad intercepts the trigger key after the accidental-press filter and the bounce
filter have had their turn and before everything else in the key pipeline (keys document,
key-down order). Its decision is made on the remapped keycode; the raw event is kept for
replay.

### 2.3 Activation modes and their timings

| `screen_trackpad_activation` | Behavior |
|---|---|
| `hold` (default) | Trigger down starts a 250 ms timer. The down is swallowed. If the key comes up before 250 ms, the swallowed down and the up are replayed through the normal pipeline, so a quick tap still types the key. If any other key goes down before 250 ms, the pending trigger is treated as a chord: the swallowed trigger down is replayed at once, the other key proceeds normally, and the trackpad steps aside until the trigger comes up. If the timer fires, the overlay opens and stays open until the trigger key comes up. |
| `double_tap` | The first tap of the trigger is not consumed: it types normally (a Space types a space). Its release time is remembered. A second down of the same trigger key whose event time is between 1 and 400 ms after that release opens the overlay in sticky mode and is consumed. A second down outside that window is just another first tap. |
| `single_tap` | Every fresh down of the trigger (repeat count 0) opens the overlay in sticky mode and is consumed. The key can no longer type at all; the settings note says to use it only with a key you do not type with. |

Timings in the hold flow:

| Event | Result |
|---|---|
| Trigger down, repeat 0, trackpad idle | pending; consumed; 250 ms timer started |
| Trigger auto-repeat while pending | consumed (the repeat is swallowed with the hold) |
| Trigger auto-repeat while not pending and not active | not consumed |
| Trigger up at t < 250 ms | timer cancelled; down and up replayed; consumed |
| Other key down at t < 250 ms | timer cancelled; trigger down replayed alone; other key not consumed; trackpad in "aborted" state until the trigger's real up, which is then not consumed and flows normally |
| Timer fires at 250 ms and the overlay opens | active (hold) |
| Timer fires and the overlay cannot open (permission) | toast "Screen trackpad needs Display over other apps. Enable it in PhysiBoard settings."; state aborted; trigger down replayed |
| Trigger up while active (hold) | overlay removed; consumed |
| Trigger down again while active (sticky), repeat 0 | overlay removed; consumed |
| Trigger up while active (sticky) | consumed |
| KEYCODE_BACK down while active (sticky) | overlay removed; consumed |
| Any other key while active | not consumed: typing continues under the overlay |

Replay: a replayed key goes back into the service's normal key-down and key-up entry points
with a "replaying" flag set, so on the way through it skips the accidental-press filter, the
bounce filter and the trackpad itself. If the normal pipeline does not handle the replayed
down, the raw down (and up, when present) are sent to the editor through the input
connection instead.

The 250 ms hold is deliberately shorter than the 600 ms Sym assistant hold and than the
~400 ms at which the vendor starts delivering the Fn repeat burst (keys document).

### 2.4 The overlay window

| Property | Value |
|---|---|
| Size | match parent in both directions |
| Type | `TYPE_APPLICATION_OVERLAY` |
| Flags | not focusable, layout in screen, layout no limits, keep screen on |
| Pixel format | translucent; background fully transparent |
| Gravity | top-start |
| Touch handling | the window claims every touch; the app underneath receives none while the overlay is up |

Because the window is not focusable, the editor keeps focus and keeps receiving the DPAD
events. Because it has keep-screen-on, the display cannot time out while the trackpad is
open.

Overlay lifetime: created on activation, removed on deactivation, on the keyboard window
being hidden (except during the status bar's per-app dip, which is not a real hide, status
bar document section 12), and when the keyboard service is destroyed.

### 2.5 The hint pill

With `screen_trackpad_show_hint` on (default true) a small pill is drawn inside the overlay:

| Property | Value |
|---|---|
| Position | top centre, 56 dp below the top of the screen |
| Text | white, 13 sp |
| Padding | 14 dp horizontal, 7 dp vertical |
| Background | rounded 20 dp, colour argb(200, 20, 20, 20) |
| Text, hold mode | "✥ Cursor" |
| Text, sticky mode | "✥ Cursor · tap to exit" |
| Text, Shift active | "⇧ Select" (either mode) |
| Tap on the pill | sticky mode only: closes the trackpad. In hold mode the pill is inert. |

The text is chosen when the overlay opens and re-chosen on every finger down, so pressing
Shift mid-session shows "⇧ Select" from the next touch. "Shift active" means a physically
held Shift, an armed Shift one-shot, or the visual Shift layer latched (keys document); caps
lock alone does not count.

### 2.6 Finger movement to DPAD events

There is no dead zone, no acceleration, no key repeat and no velocity term. Movement is
accumulated per axis from the last touch position:

| Quantity | Value |
|---|---|
| Horizontal step | `screen_trackpad_step_px` pixels (default 32, range 8 to 64) |
| Vertical step | 2.0 times the horizontal step (default 64 px) |
| Maximum DPAD events per motion event | 12, shared: horizontal steps are emitted first, then vertical, until the budget is spent |
| Accumulator reset | on finger down, finger up and cancel |

On every move event the delta since the previous position is added to the horizontal and
vertical accumulators. While the horizontal accumulator's magnitude is at least one step, one
step is subtracted and KEYCODE_DPAD_RIGHT (positive) or KEYCODE_DPAD_LEFT (negative) is
sent; then the same for the vertical accumulator with KEYCODE_DPAD_DOWN (positive, finger
moving down the screen) or KEYCODE_DPAD_UP. Leftover movement under a step carries over to
the next move event. Movement left over when the finger lifts is discarded.

Each DPAD event is a key down followed by a key up with the same timestamp, sent through the
current input connection. If there is no input connection the step is silently dropped. When
Shift is active (same definition as the pill) both events carry META_SHIFT_ON and
META_SHIFT_LEFT_ON; the editor then extends the selection instead of moving the caret.
Whether Shift is on is re-read on every move event, so Shift can be pressed or released
mid-drag.

The step value is read once when the overlay opens; changing the slider takes effect on the
next activation. The default of 32 px was tuned on the Titan 2 Elite: 24 px "was too twitchy
for the 4-inch screen" (commit fd3c5ad). On the 1080 px wide display a full-width drag is
about 33 cursor moves at the default step.

### 2.7 What happens on release

- Hold mode: releasing the trigger removes the overlay immediately. Any finger still on the
  screen is ignored from then on; the app receives no synthetic up.
- Sticky mode: lifting the finger does nothing beyond resetting the accumulators. The
  overlay stays until the trigger key, Back, or the pill closes it.
- In both modes, closing the overlay cancels any pending hold timer and clears the replay
  state.

### 2.8 The settings screen ("Screen trackpad")

Reached from Settings, from the "Trackpad" home tile (which replaced the Auto-correct tile
in 1.0.2), from the settings search entry, and from the trackpad deep link.

Rows, top to bottom:

1. **Enable screen trackpad** switch. Turning it on when the overlay permission is missing
   and the broker is not usable opens the system "Display over other apps" page for the
   package (`android.settings.action.MANAGE_OVERLAY_PERMISSION`, package URI).
2. **Display over other apps** status row: check icon and "Permission granted", or warning
   icon in the error colour and "Permission not granted, tap to open system settings"
   (tappable only while not granted). The permission is re-read every 1500 ms while the
   screen is open, so returning from system settings updates the row without leaving.
3. **Trigger key** dropdown (five options of section 2.2); disabled while the feature is off.
4. **Activate by** dropdown (three options of section 2.3); disabled while off. Under it a
   note: for hold, "A quick tap still types the key normally. Hold Shift while swiping to
   select text."; for the sticky modes, "Tap the trigger key, the Back key, or the on-screen
   pill to exit. Single tap takes over the key completely, only use it with a key you don't
   type with."
5. **Sensitivity** slider, labelled "Pixels of swipe per cursor step, lower is faster",
   showing the value as "N px". Range 8 to 64 with 13 intermediate stops (15 positions, 4 px
   apart). The value is stored when the drag ends.
6. **Show on-screen hint** switch ("Small pill at the top of the screen while the trackpad is
   active"); disabled while off.

Silent grant on this screen: whenever the feature is on, the permission is missing, the
broker is verified usable (a real round trip, not merely "a key is stored", changelog 2.0.7
"Features are checked, not assumed") and no attempt has been made yet in this visit, the
screen runs the broker grant of section 2.9 once; if the permission still is not granted
afterwards it opens the system page instead of leaving the trackpad silently
non-functional.

### 2.9 Obtaining the overlay permission through the broker

The overlay permission is a normal user-grantable toggle, so the user can always grant it in
system settings. With the embedded ADB broker paired, PhysiBoard grants it itself by running
`appops set brobata.physiboard SYSTEM_ALERT_WINDOW allow` through the broker, then re-reads
the permission. Rules:

- No-op when the permission is already granted, or when the broker is not paired.
- Runs off the main thread, never throws, best effort.
- If the grant lands and `screen_trackpad_enabled` is false, it is set to true: the backlight
  pairing is meant to be "genuinely the only step the user has to take" (changelog 1.0.2 "One
  pairing applies every privileged step").
- It is part of the "apply every privileged step" pass, which runs when a pairing succeeds,
  when the keyboard service starts (through the backlight manager start), and when the smart
  backlight screen is opened, as well as from the trackpad settings screen as above.
- Broker calls are serialised; a blocked broker records a failure reason against every
  privileged step for the diagnostics export instead of logging (release builds strip logs).

The caret badge (section 4) needs the same permission and benefits from the same grant.

### 2.10 The debug activity and the debug overlay

- **Trackpad Debug activity**: registered in the manifest (translucent theme, single instance,
  excluded from recents, not exported, label "Trackpad Debug"). A full-screen black surface at
  85 percent opacity listing, in green monospace, every motion and pointer event it receives:
  action, source (touchpad, touchscreen, mouse, stylus, trackball or the raw source number),
  time, pointer count, and per pointer id, x, y, pressure, size, touch major and minor; scroll
  axis values for scroll events; button state when non-zero. It keeps the last 500 lines and
  auto-scrolls to the newest; a delete button clears and a back arrow closes. While empty it
  shows "Waiting for trackpad events... Swipe on the trackpad to see events here." Nothing in
  the app launches it any more: the launcher in the strip that used to open it is never
  called. It is reachable only through an explicit `am start` of the component.
- **Trackpad debug overlay service**: a class that would draw a `TYPE_APPLICATION_OVERLAY`
  window (black at 87 percent opacity, green 10 sp monospace) listing the last 50 motion
  events, stopping itself when the overlay permission is missing. It is not declared in the
  manifest and cannot be started. Dead code.

## 3. The keyboard-surface swipe (upstream "trackpad gestures")

### 3.1 Device facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Titan 2 Elite's physical keys carry a capacitive touch layer exposed as a separate input device named `touchPad` at `/dev/input/event4`, distinct from the key matrix (`TitanKey`, `/dev/input/event5`), the touchscreen (`fts_ts`, `/dev/input/event6`) and `ff_key` (`/dev/input/event7`). | DEVICE.md, "Companion devices" |
| D2 | The touch layer reports multitouch absolute coordinates (ABS_MT) over a 1080 by 600 range, is flagged as a direct input surface (INPUT_PROP_DIRECT) and samples at about 90 Hz. | Stated in the brief for this document from the maintainer's capture; not yet recorded in DEVICE.md. Needs a `getevent -p /dev/input/event4` capture added to DEVICE.md before 3.0 builds on it. |
| D3 | Sym arrives as keycode 63 (scancode 253); Fn arrives as Ctrl repeats with scancode 251 and never sends a key-up. | keys document; DEVICE.md scancode map and Fn delivery model |
| D4 | Upstream Pastiera's Titan 2 (non-Elite) trackpad device is `/dev/input/event7`, moved to `/dev/input/event6` from firmware V01.00.14. The Elite identifies its keyboard as `titan2elite_qwerty`, not `titan2`, so the firmware rule never applies to it and the legacy path is chosen. | commits 60bd83b "Select Titan 2 trackpad device by firmware", 867330f "Limit Titan 2 trackpad remap to V01.00.14"; device identification rules |
| D5 | The vendor firmware can deliver keyboard-surface swipes as key events: keycodes 322 and 404 are treated as "swipe to delete" keys. Whether the Elite firmware actually emits them, and for which gesture, has not been captured. | commit 625b185 "Support alternate swipe delete keycode"; swipe-to-delete provider `titan2_keycode` |
| D6 | The Android input stack, on a Titan 2 running Android 16, can deliver the touch layer to the keyboard's own window as motion events from a device named `touchPad` with the touchpad source. | commits 82e0e48 "Add native Titan 2 trackpad gestures", ae06398 "Enable native trackpad gestures on Titan 2 Elite" |

### 3.2 Status on the Elite

Upstream Pastiera used the touch layer for one thing: swipe up on the keys to accept a
suggestion, later also swipe left to delete the previous word. PhysiBoard removed the
"Keyboard swipe" settings screen in 1.0.2 ("the Shizuku-backed keyboard-surface trackpad is
superseded; its detector code remains, disabled, for upstream parity"). What remains:

- The preferences still exist and are honoured by the running keyboard (table in section
  3.7), but nothing in the app turns `trackpad_gestures_enabled` on except the tutorial's
  "Dev's choice" preset, which sets it to true along with a QWERTZ layout and 200 ms long
  press.
- The default provider is `native_ime`, which needs no Shizuku.
- The Shizuku provider would read `/dev/input/event7` on the Elite (D4), which is `ff_key`,
  not the touch layer, so it can never see a swipe there.
- PhysiBoard does not use the Elite's keyboard-surface scroll gestures for cursor movement
  at all. Cursor movement is the screen trackpad (section 2) and nav mode (section 5).

### 3.3 The native provider: gesture detection

Active when `trackpad_gestures_enabled` is true and `trackpad_provider` is `native_ime`, and
only when all of the following hold: the device is a Titan 2 family device; Android is 16 or
newer; the motion event comes from a touchpad-source device or from an input device named
`touchPad` (case-insensitive). Events failing any test are left to the system.

The keyboard's own window (its decor view) is made focusable in touch mode, given focus, and
a generic-motion listener is attached to it whenever the editor starts, the keyboard window
is shown, or the provider preference changes. So the swipe can only be seen while the
keyboard has a window, that is while the strip is on screen.

Per gesture:

| Phase | Behavior |
|---|---|
| Down | remember start x, y, time; mark the gesture unhandled; consumed |
| Move | update the "last" position and time, walking the event's historical samples in order so the final sample is the last one; consumed |
| Up | evaluate once (below) from start to last position; then forget the gesture; consumed |
| Cancel | forget the gesture; consumed |

Evaluation at up, with dx = last x minus start x, dy = last y minus start y, duration =
max(1, last time minus start time) ms:

| Direction | Conditions (all required) |
|---|---|
| Up (accept suggestion) | upward distance (minus dy) >= `trackpad_suggestion_swipe_threshold`; abs(dx) < upward distance / 4; upward velocity >= 2.0 px/ms |
| Left (delete word) | leftward distance (minus dx) >= `trackpad_delete_swipe_threshold`; abs(dy) < leftward distance / 4; leftward velocity >= 2.0 px/ms; `swipe_to_delete` true; `swipe_to_delete_provider` = `native_ime` |

Up is tested first. A gesture that qualifies for neither is recorded in the diagnostics
capture as a "candidate" and does nothing. A gesture that qualifies within 250 ms
(wall-clock) of the previously accepted gesture is recorded as "debounced" and dropped.

Third for an accepted up-swipe: start x clamped to 0..1440, then left third below 480,
centre below 960, right otherwise. The 1440 width is upstream's Titan 2 value; against the
Elite's 1080 range (D2) the right third can only be reached by starting at x >= 960 of 1080,
which would need device confirmation.

### 3.4 The Shizuku provider: gesture detection

Active when `trackpad_gestures_enabled` is true and `trackpad_provider` is `shizuku`, Shizuku
is running and has granted PhysiBoard permission. It starts when the keyboard service
starts, when the editor's view starts, and after any of its preferences change (stopping the
old reader first; the reader process is destroyed on stop, including when stop races start,
changelog 2.0.7 "A leaked background process").

It runs `getevent -l <device>` through Shizuku and parses lines: BTN_TOUCH DOWN marks a
touch start and its time; the first ABS_MT_POSITION_X then ABS_MT_POSITION_Y after the
down become the start position; every later position updates the current position; BTN_TOUCH
UP evaluates.

| Quantity | Value |
|---|---|
| Swipe-up distance | start y minus current y > `trackpad_suggestion_swipe_threshold` (as an integer) |
| Straightness | abs(dx) < dy / 4 |
| Velocity | dy / duration >= 2.0 px/ms |
| Third | start x against a 1440 wide surface: < 480 left, < 960 centre, else right |

There is no left swipe in this provider and no debounce. A qualifying swipe accepts the
suggestion for its third exactly like the native provider.

### 3.5 What an accepted up-swipe does

The third is turned into a slot: left third selects the left slot (the engine's third
candidate), centre third the centre slot (the engine's first candidate), right third the
right slot (the second candidate). The status bar document gives the slot layout.

Before anything is inserted:

1. If the visual Shift or Alt layer is latched (keys document), both are cleared and the
   modifier state saved before the hold is restored.
2. The gesture is allowed only when the Sym page is 0 (no Sym page open), suggestions are
   enabled, smart features are not disabled for this field, and either at least one
   suggestion is visible or the add-word rule below allows adding. Otherwise it is ignored.
3. **Add-word rule** (a pure decision): with `trackpad_gesture_add_word_enabled` true and an
   add-word candidate pending (a word the user typed that the dictionary does not know,
   autocorrect document), a swipe in the left third always adds the word; a swipe in the
   centre or right third adds it only when `trackpad_gesture_add_word_full_width_enabled` is
   true and no suggestions are visible. With the setting off, or no candidate, the rule never
   fires.
4. Adding the word: the right slot (index 2) flashes, the word is added to the user
   dictionary, the pending candidate is cleared, and a space is committed after the cursor
   unless the next character is already whitespace or a word boundary (in which case the
   auto-space tracker is just cleared); the strip refreshes; a haptic tick plays.
5. Otherwise the chosen slot flashes, the word around the cursor (up to 64 characters each
   side, bounded by the punctuation word-boundary rules) is replaced with the suggestion,
   cased like the word it replaces and capitalised when auto-capitalisation applies at the
   cursor and `auto_capitalize_first_letter` is on; a space is appended unless the
   replacement ends with an apostrophe and the auto-space tracker is marked; an armed Shift
   one-shot is consumed; the suggestion context resets; a haptic tick plays; the commit is
   recorded in the diagnostics capture as a "suggestion tap".

An accepted left swipe deletes the last word before the cursor (up to 100 characters are
inspected; trailing whitespace is skipped, then the run of non-whitespace is removed).

### 3.6 The firmware swipe keycodes

Independently of the providers above, a key down with keycode 322 or 404 in an editable
field is a firmware swipe (D5):

| `swipe_to_delete` | `swipe_to_delete_provider` | Result |
|---|---|---|
| true | `titan2_keycode` | the last word before the cursor is deleted; consumed when something was deleted, otherwise falls through |
| any other combination | | consumed and ignored, recorded in the key log as "swipe_to_delete_ignored_<provider>" |

`swipe_to_delete` defaults to false and the provider to `native_ime`, so on a fresh install
those keycodes are swallowed silently.

### 3.7 Settings for the keyboard-surface swipe

None of these has a screen in 2.x; they are listed because the keyboard still reads them and
backup carries them.

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `trackpad_gestures_enabled` | boolean | false | whether either provider runs | none (tutorial "Dev's choice" sets true) | "Enable Trackpad Gestures" |
| `trackpad_provider` | string `shizuku` or `native_ime` | `native_ime` | which detector runs; unknown values read as the default | none | "Trackpad input provider" |
| `trackpad_swipe_threshold` | float 120..750 | 500 | legacy single threshold; used as the fallback for both thresholds below | none | "Trackpad Swipe Sensitivity" |
| `trackpad_suggestion_swipe_threshold` | float 120..750 | the legacy value, else 500 | minimum upward distance in px | none | "Suggestion Swipe" |
| `trackpad_delete_swipe_threshold` | float 120..750 | the legacy value, else 500 | minimum leftward distance in px | none | "Delete Swipe" |
| `trackpad_gesture_add_word_enabled` | boolean | true | whether a swipe may press the add-word button | none | "Add words with gestures" |
| `trackpad_gesture_add_word_full_width_enabled` | boolean | true | whether any third adds when only the add-word button is shown | none | "Full-width add-word swipe" |
| `swipe_to_delete` | boolean | false | whether a left swipe or a firmware swipe key deletes the previous word | none | |
| `swipe_to_delete_provider` | string `titan2_keycode` or `native_ime` | `native_ime` | which source of left swipe is honoured | none | |

## 4. The caret badge

### 4.1 What it is

The caret badge is a small overlay window holding bare glyphs, no panel, placed just to the
right of the text cursor, reporting which modifiers are on. It exists so the user does not
have to look down at the keyboard's LED strip or up at the system status bar: the state is
where they are already looking (commit 7a416e3). It is drawn in an overlay because an IME
cannot paint inside the app it is typing into, and it is positioned from the cursor anchor
information the editor publishes.

Editors are not obliged to report a cursor position. When one does not, the badge simply
never appears in that app; the system status bar icon and the optional LED row still show
the state.

### 4.2 What it shows

Items are listed left to right in the fixed order Shift, Alt, Ctrl, Sym. At most one item per
modifier, chosen by the first matching row:

| Modifier state | Glyph | Style |
|---|---|---|
| Caps lock on | up arrow with a bar under it | locked colour, full |
| Shift one-shot armed | up arrow | armed colour, full |
| Shift physically held | up arrow | armed colour, faint |
| Alt latched | ⌥ (U+2325), or the word "ALT" if the system font has no glyph for it | locked colour, full |
| Alt one-shot armed | ⌥ / "ALT" | armed colour, full |
| Alt physically held | ⌥ / "ALT" | armed colour, faint |
| Ctrl latched, and the latch is not nav mode | "CTRL" | locked colour, full |
| Ctrl one-shot armed | "CTRL" | armed colour, full |
| Ctrl physically held | "CTRL" | armed colour, faint |
| Any Sym page open (page not 0) | "SYM" | armed colour, full |

Nav mode is the one exclusion: it holds Ctrl latched for as long as it is on, and reporting
that would pin a "CTRL" badge beside the cursor permanently. Only the Shift arrow carries the
lock bar; "an arrow over a bar is what a caps-lock key has always been", while underlining a
word would read as an artefact (commit 56e68e5).

Colour: blue for one click, red for two: a modifier that expires by itself against one that
is stuck on until turned off (commit d2a9353). A held modifier is drawn faint because it goes
away the moment the key comes up.

The "Sym" item follows the Sym page, not a modifier flag, so it shows while any Sym page
(emoji, symbols, clipboard, picker) is open.

### 4.3 How it is drawn

| Quantity | Value |
|---|---|
| Glyph height (words and arrow) | 11 sp |
| ⌥ point size | 1.3 times the glyph height (symbol glyphs sit small in their em box) |
| Gap between items | 4 dp |
| Halo | white at alpha 225, stroked outward, stroke width 2 times 1.1 dp, round joins, drawn before the fill |
| Full alpha | 245 |
| Faint alpha | 140 |
| Letter spacing | 0.02 em |
| Arrow width | 0.68 times the glyph height |
| Arrow head | from the top to 46 percent of the height; stem between 30 and 70 percent of the width |
| Lock bar | 0.11 times the glyph height tall, 0.14 times the glyph height below the baseline, as wide as the arrow |
| View width | sum of item widths plus gaps plus halo padding on both sides |
| View height | tallest ascent among the paints plus descent plus lock-bar room plus halo padding |
| Armed colour default | 0xFF2563EB (blue) |
| Locked colour default | 0xFFDC2626 (red) |

The Shift arrow is drawn as a path rather than as a character because the Unicode shift and
caps-lock characters are hairline outlines at this size. All items share one baseline so the
arrow's foot lines up with the words' feet. The halo separates the glyphs from any app
background without putting a slab behind them.

The two colours are re-read from preferences every time the badge is shown, so a colour
picked while a field is open takes effect on the very next key press.

### 4.4 Where it is placed

From the editor's insertion marker (caret) in screen pixels, with line height = caret bottom
minus caret top:

1. x = caret x + 4 dp. The badge starts just right of the caret, in the empty space the cursor
   is about to move into; centring it put half a glyph over the last letter typed.
2. y = caret top + 0.18 times the line height minus the distance from the view's top to the
   glyphs' feet. The glyphs' feet sit 18 percent into the top of the line box, like a
   superscript.
3. If x + badge width exceeds the screen width, it flips to the left of the caret: x = caret
   x minus 4 dp minus badge width.
4. If y is negative, it drops below the line: y = caret bottom minus 0.18 times the line height.
5. Both are then clamped into the screen.

One placement, always. An earlier version chose between "beside" and "above the line" and
jumped between them as the field filled and emptied (commits 0d5220e, d2a9353); a position
that never collides never has to move.

Window: wrap content, `TYPE_APPLICATION_OVERLAY`, not focusable, not touchable (it must never
steal a tap meant for the text under it), layout in screen, layout no limits, translucent,
gravity top-start at the computed x, y. When already attached it is moved, not recreated.

### 4.5 The window-context requirement

The badge window must be created from a context typed for an overlay. The keyboard service's
own context is typed as an input method, and adding an overlay through it makes the system
log a window-type mismatch on every layout pass. On Android 12 and newer PhysiBoard creates
a window context of type `TYPE_APPLICATION_OVERLAY` for the badge and falls back to the
service context only if that fails, which then costs only the log line (commit a44a9b6,
changelog 2.0.7 "The caret badge no longer logs a window warning on every cursor move").

### 4.6 When it shows, moves and hides

The badge recomputes its items on every strip refresh (status bar document, "refresh"): after
every key, selection change, window show and relevant preference change. If the item list is
unchanged nothing happens. If it became empty the badge hides. If it is non-empty and a
caret position is known, the badge shows there at once, without waiting for a fresh anchor
report.

Every cursor anchor report from the editor updates the remembered caret. If items are on
screen, the badge moves to the new caret, or hides when the report has no usable caret. A
caret is unusable when the report is absent, when any of its horizontal, top or bottom
values is not a number, or when the editor flags the insertion marker as having an
invisible region and no visible region (scrolled out of view): "a badge floating over the app
where the cursor is not actually visible is worse than no badge".

The remembered caret is forgotten and the badge hidden when the editor finishes, when the
editor's view finishes, and when monitoring restarts for a new editor: the cached caret
belongs to the old editor.

Permission: the overlay permission is re-checked on every show, never cached, so granting it
takes effect without restarting the keyboard (commit d837c83). A badge window that the
window manager actually rejects is latched off until the service restarts, so a broken
overlay is not retried on every keystroke.

### 4.7 Asking the editor for the caret position

Cursor anchor reports only arrive while PhysiBoard asks for them, and the request is scoped
to one input connection: every new editor drops it. Rules:

- When an editor's view starts, the badge's caret is forgotten and a request for cursor
  updates (immediate plus monitor) is issued if `caret_modifier_badge` is on or the
  emoji-picker search needs it; with neither, a request with no flags is issued to turn
  monitoring off. Both features share this one switch, so neither can turn it off under the
  other.
- Editors are not always ready at once and some refuse the first request, so retries are
  staged at 80, 250, 600 and 1200 ms after the start. A retry is skipped once a request has
  been accepted or after 8 attempts in total for that editor.
- On every strip refresh: if the setting's value differs from the last one seen, the counters
  reset and the request is re-issued; otherwise, while the setting is on and no request has
  been accepted yet, a retry is attempted (subject to the same cap).
- Commit a5b7b2a moved the initial request to the editor start because, with a hardware
  keyboard, the input view is often never created; in the current source the request is
  issued when the editor's view starts and again on refresh, and the retries cover the gap.

### 4.8 Settings

All on the "Status Bar Theme" screen under Keyboard, in the "Modifiers" section, after the
bar height rows and before the modifier extras.

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `caret_modifier_badge` | boolean | true | whether the badge exists and whether cursor updates are requested for it | Status Bar Theme | "Show modifiers at the cursor" ("Shift, Alt, Ctrl and Sym appear next to the text cursor whenever they are on. Only works in apps that report where the cursor is.") |
| `caret_badge_armed_color` | int ARGB | 0xFF2563EB | colour of one-shot and held items | Status Bar Theme, shown only while the badge is on | "One press colour" ("Shift, Alt or Ctrl armed for the next key only") |
| `caret_badge_locked_color` | int ARGB | 0xFFDC2626 | colour of caps lock, Alt lock and Ctrl lock | Status Bar Theme, shown only while the badge is on | "Locked colour" ("Caps lock, Alt lock and Ctrl lock, which stay on until you turn them off") |

While the switch is on and the overlay permission is missing, the row shows in the error
colour "Drawing next to the cursor means drawing over the app you are typing in, so Android
needs permission first." and a button "Allow drawing over apps" that opens the system overlay
page for the package. The colour rows open a picker whose subtitle reads "A modifier being
held down is shown in a faded version of these." with ten quick swatches: 0xFF2563EB,
0xFFDC2626, 0xFF16A34A, 0xFFEA580C, 0xFF9333EA, 0xFF0891B2, 0xFFDB2777, 0xFFCA8A04,
0xFF475569, 0xFF111827 (saturated mid-tones, because a very pale or very dark pick vanishes
on one theme or the other); the wheel underneath allows anything. The three keys are
"content" keys for the settings migration: they survive migration exactly as stored.

## 5. Nav mode ("Fn Layer")

### 5.1 What it is

Nav mode is a latched Ctrl that PhysiBoard itself created, marked "from nav mode". While it
is on, letter keys run the Fn Layer map: arrows on E/S/D/F and J/K/L, page keys on Y/H,
Escape on Q, Tab on T, selection and word movement on W/R/U/I/N/M, clipboard actions on
A/Z/X/C/V, and so on (full map in 5.4). It is meant for driving an app that has no text
field focused, a list or a web page, from the keyboard.

The user-facing name is "Fn Layer" everywhere (Smart Features was renamed to Fn Layer in
0.86); the preference keys and the system notification channel still say "nav mode".

### 5.2 Entering and leaving

Entry requires `nav_mode_enabled` (default true) and no editable field: the key arrives while
there is no input connection or the field type is null.

| Situation | Key | Result |
|---|---|---|
| no field, nav off, Ctrl not already down | Ctrl down (first tap) | consumed; Ctrl marked physically pressed; nothing else |
| | Ctrl up | release time recorded |
| no field, nav off | Ctrl down again, same Ctrl key as the previous key event, less than 500 ms after that release | consumed; nav mode latched; 70 ms haptic; system status icon shown |
| no field, nav off | Ctrl down, but a non-modifier key or the other Ctrl key came between | treated as a first tap again |
| no field, nav on | Ctrl down | consumed; nav mode off; latch cleared; status icon hidden; legacy notification cancelled |
| no field, nav on | letter with a map entry | the mapping runs (5.5); consumed if it ran |
| no field, nav on | letter with no entry, or entry of type none | not consumed; passes to the system |
| no field, nav on | Enter | KEYCODE_DPAD_CENTER down and up sent through the input connection, if any; otherwise not consumed |
| no field, nav on | Back | not consumed: nav mode is persistent and does not close on Back |
| no field, nav on | Sym, with power shortcuts enabled | power-shortcut mode toggles (launcher document); the toggle is told nav mode is on |
| no field, nav on | any key up | consumed |
| text field gains focus (not a restart), nav on, the field is really editable and has a connection | | nav mode is remembered as "was on", exited, and modifiers reset; the field types normally |
| text field focused, nav somehow still on | any key down | nav mode exits before routing (the prelude) |
| the field finishes | | modifiers reset preserving nav; if nav "was on" it is re-entered; otherwise, if nav is off, the status icon is hidden |
| the field's view finishes, or the keyboard window hides | | modifiers reset preserving nav |
| a Ctrl lock made inside a field (Ctrl double tap, keys document) and then the field goes away | | the plain lock is converted into nav mode by the preserve rule (5.3) |
| Enter sent as "send" in an app, or an editor action performed, while Ctrl in any form was on | | Ctrl state cleared, and if it was nav mode the icon and notification are cleared |
| Alt+Shift layout switch while Ctrl was on | | Ctrl cleared (unless it is a plain lock kept by `ctrl_latch_stays_on_space`); nav cleared |
| launcher quick-launch or power shortcut ends | | nav mode is re-entered if the shortcut was told it was on (launcher document) |

Nav mode's own re-entry ("enter") only succeeds when `nav_mode_enabled` is true and nav is not
already on; it applies the latch, marks it from nav mode, plays the 70 ms haptic and shows
the icon.

The double-tap needs a true key-up between the two downs and a repeat-0 down each time. On
the Titan the Fn key never delivers a key-up or a repeat-0 down (D3): a physical Fn double tap
cannot enter nav mode by itself. The "Set Fn key to Ctrl" card on the Fn Layer screen
rewrites the vendor's per-key function so the firmware synthesises a real Ctrl (device
document); whether that yields the down/up pair a double tap needs has not been captured and
must be verified on the phone.

### 5.3 The preserve rule and the Ctrl lock

Whenever modifiers are reset "preserving nav mode" (field finish, view finish, window hidden,
finish input): if Ctrl is latched, the latch is kept and marked as from nav mode; if it was
only marked from nav mode without a live latch, the mark is kept. Otherwise, if either flag
was set, nav mode's notification and icon are cancelled and Ctrl state is cleared. All Shift
state, the consecutive-tap memory and all Alt state are cleared regardless.

A Ctrl lock inside a field therefore becomes nav mode as soon as the user leaves the field.
Inside a field, tapping Ctrl while Ctrl is latched from nav mode turns it off and reports
"should not happen"; tapping Ctrl while a plain lock is on just turns the lock off.

### 5.4 The default Fn Layer map

Shipped in `common/ctrl/ctrl_key_mappings.json` as a `mappings` object keyed by
`KEYCODE_<letter>`, each entry `{"type": ..}` with `keycode`, `action` or `command` as the
value field. Only the 26 letter keys are mapped.

| Key | Type | Value | Meaning |
|---|---|---|---|
| Q | keycode | ESCAPE | Esc |
| W | action | expand_selection_left | extend selection one character left |
| E | keycode | DPAD_UP | up |
| R | action | expand_selection_right | extend selection one character right |
| T | keycode | TAB | Tab |
| Y | keycode | PAGE_UP | PgUp |
| U | action | expand_selection_word_left | extend selection one word left |
| I | action | expand_selection_word_right | extend selection one word right |
| O | keycode | DPAD_CENTER | select / press |
| P | action | toggle_minimal_ui | "Mini" (dead: see 5.5) |
| A | action | select_all | select all |
| S | keycode | DPAD_LEFT | left |
| D | keycode | DPAD_DOWN | down |
| F | keycode | DPAD_RIGHT | right |
| G | none | | nothing |
| H | keycode | PAGE_DOWN | PgDn |
| J | keycode | DPAD_LEFT | left |
| K | keycode | DPAD_DOWN | down |
| L | keycode | DPAD_RIGHT | right |
| Z | action | undo | undo |
| X | action | cut | cut |
| C | action | copy | copy |
| V | action | paste | paste |
| B | command | pastiera.toggle_software_keyboard_mode | toggle the on-screen keyboard mode |
| N | action | move_word_left | cursor one word left |
| M | action | move_word_right | cursor one word right |

Migration: `nav_mode_default_mappings_version` (default 1 when unset; current 3). On start,
if the stored version is below 3, the user's file is patched: N, M, U, I get the word actions
above and B gets the toggle command, but only where the entry is missing or of type none;
the version is stored and `nav_mode_mappings_updated` is stamped. The version key is
carried by the settings migration so a mapping is not re-patched after a restore.

The mapping file is loaded from the private files directory when it exists, else from the
assets. Unknown key names are skipped; a `keycode` entry whose value is not one of the twelve
allowed names is skipped. The running keyboard reloads the map whenever
`nav_mode_mappings_updated` changes.

### 5.5 What each mapping type does

Applies both in nav mode with no field and, inside a field, whenever the map is consulted
(5.6). "ic" means the current input connection; without one the keycode and action types do
nothing and report not handled, except commands.

| Type | Value | Behavior |
|---|---|---|
| keycode | DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, TAB, MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN, ESCAPE, FORWARD_DEL | key down then key up of that keycode sent to ic. Inside a field the events carry Shift when Shift is held or the event has Shift and the key is a selection-aware nav key (the four arrows, home, end, page up, page down); outside a field the original event's meta state is copied. After an arrow, home, end or page key inside a field the strip refreshes 50 ms later. |
| action | copy, paste, cut, undo, select_all | the matching Android context-menu action performed on ic |
| action | expand_selection_left / right | the selection's moving edge steps one character left or right, keeping the anchor; with no selection a one-character selection is created; at the start or end of the text nothing happens. When the editor cannot report its selection, the fallback assumes the cursor is at the end of the text before it. |
| action | move_word_left / right | the cursor jumps to the previous or next word start; with Shift held inside a field it becomes expand_selection_word_left / right instead |
| action | expand_selection_word_left / right | the moving edge jumps a word; requires the editor to report its selection |
| action | page_start / page_end | Ctrl+Home or Ctrl+End key down and up to ic (with Shift too when Shift is held); strip refresh 50 ms later. Only inside a field; with no field this type is not handled. |
| action | media_play_pause / media_previous / media_next | the media key dispatched through the audio manager as down and up |
| action | toggle_minimal_ui | the strip's key preview labels it "Mini" but no handler exists: inside a field it falls through to the app as an unknown Ctrl combo; with no field it is not handled. Dead default on P. |
| action | anything else | not handled (falls to the app inside a field) |
| native_ctrl | | inside a field: the original event is passed through with Ctrl set (Ctrl+letter to the app). With no field: key down and up of the letter with Ctrl meta sent to ic. |
| command | any command id whose surfaces include nav mode: `nav.keycode.<name>`, `nav.action.<name>`, `pastiera.quick_launcher`, `pastiera.main`, `pastiera.toggle_software_keyboard_mode`, `pastiera.voice_assistant`, `device.home`, `device.media.*`, `device.volume.*`, `device.brightness.*`, `device.shade.*`, app launches and app actions | the command runs; handled when it reports success. A command that does not list nav mode among its surfaces is not handled. Commands run with or without an input connection (a test covers `device.home` with none). |
| none | | not handled |

Word boundaries for the word actions follow the punctuation rules in the text-input
document; the text inspected is up to 1000 characters each side of the cursor.

### 5.6 Ctrl inside a text field: pass-through versus the map

Inside a field a key that arrives with Ctrl (the event's Ctrl flag, a live Ctrl latch, an
armed Ctrl one-shot, or any Ctrl form in a numeric field) is routed by these rules, in
order:

1. Decide which key looks up the map. With the latch from nav mode, or with a physical Ctrl
   and `nav_mode_ctrl_hold_enabled` on, the physical key is used ("the physical nav grid").
   Otherwise, with `layout_aware_ctrl_shortcuts` on, the key is translated through the
   active text layout first (on QWERTZ the physical Y key looks up Z), else the physical key.
2. Physical Ctrl, not from nav mode, Ctrl-hold navigation off, and not a numeric field
   forcing basic actions: the combo passes to the app as Ctrl plus the (possibly translated)
   key. Rich-text editors and IDEs get their native shortcuts. This wins over an armed
   one-shot, because a physical press sets one-shot internally.
3. An armed one-shot that is not nav mode is consumed now and the strip refreshes.
4. A command mapping runs (5.5) or, if the command is unknown or not allowed on this
   surface, the key goes to the system.
5. Without an input connection nothing else is handled.
6. A mapping of the other types runs per 5.5. Native Ctrl always passes through as a real
   Ctrl shortcut. Unknown action or keycode values fall to the system.
7. No mapping: Ctrl+DEL deletes the selection if there is one, else the last word;
   Ctrl+Enter and Ctrl+Back go to the system; any other key with a physical Ctrl not from
   nav mode passes through as a Ctrl combo; otherwise it goes to the system.

Numeric fields force copy, cut, paste and select_all mappings to run as context-menu actions
even with a physical Ctrl.

The keyboard's input view is never requested while the latch is from nav mode, and the
tutorial says the same thing in one line: "Press Ctrl twice to latch it, or enable Ctrl-hold
navigation below."

### 5.7 What the user sees

- The strip is hidden entirely (its layout gone) while nav mode is on; the suggestion row,
  buttons and LED row all disappear and return when it ends.
- The system status bar shows the nav mode icon (a small vector in the notification shade
  area) through the input method's status icon API while nav is on; it is hidden when nav
  ends, when a field finishes with nav off, and when the keyboard service is destroyed. The
  same icon slot otherwise shows the modifier icon (status bar document); nav mode wins.
- A 70 ms haptic when nav mode turns on; none when it turns off.
- The legacy "Fn Layer Activated / Nav mode activated" notification (channel "PhysiBoard Fn
  Layer") is never posted any more; it is only ever cancelled, at service start and at every
  exit, so an old one cannot linger.
- The caret badge deliberately omits nav mode (4.2).

### 5.8 The Fn Layer settings screen

Reached from Settings (search entries "Fn Layer" and "Configure Fn layer key mappings"), and
from the modifier screen's "Control in Fn Layer" row when Ctrl is configured as a Fn Layer
modifier. Rows, top to bottom:

1. **Fn Layer guide** row; tapping opens a dialog with the long guide text (Ctrl-hold, double
   tap, Action versus Native Ctrl, QWERTZ on physical QWERTY, layout-aware shortcuts,
   overrides).
2. **Set Fn key to Ctrl** card with its warning and reset action (behavior in the device
   document; it writes the vendor key-config, then the broker, else opens the
   write-settings grant).
3. **Enable Fn Layer** switch ("Double-tap Ctrl to activate the Fn Layer").
4. While enabled: **Ctrl-hold navigation** switch ("When enabled, text fields use Fn Layer
   mappings while Ctrl is held. When off, Ctrl shortcuts are passed to the app. Keys set to
   Native Ctrl are always passed through as real Ctrl shortcuts.").
5. While enabled, in an "Advanced" section: **Layout-aware app Ctrl shortcuts** switch,
   disabled with the description "Disabled while Ctrl-hold navigation is active, because held
   Ctrl is handled by Fn Layer instead of the app." when Ctrl-hold is on, plus an info dialog.
6. While enabled: the **key grid**: three rows Q..P (10), A..L (9), Z..M (7), 2 dp apart, each
   key as wide as fits (at most 64 dp) and 1.25 times as tall. A key with a mapping other than
   none is tinted (primary container at 30 percent, primary border, elevation 2 dp); an
   unmapped key is flat. Each key shows its letter (13 sp bold); a layout hint in the tertiary
   colour at 8 sp, "→ Z" for example, when the active text layout would type a different
   letter on that physical key; then the mapping as an icon where one exists (arrows, first
   and last page, select-move icons, play/pause, skip) or its short label at 11 sp, or the
   word "default" at 9 sp when the key has a default it is not overriding. Tapping a key
   opens the dialog.
7. **Revert to Default** button: deletes the private mapping file, copies the asset again,
   runs the migration, stamps `nav_mode_mappings_updated`.

The key dialog ("Configure Q"): a **Type** choice of Keycode, Action, Native Ctrl, Command,
None; a "Use Default: <label>" shortcut when the key has a default; then a two-column grid of
values: the twelve keycode names, the sixteen action names (copy, paste, cut, undo,
select_all, expand_selection_left, expand_selection_right, move_word_left, move_word_right,
expand_selection_word_left, expand_selection_word_right, page_start, page_end,
media_play_pause, media_previous, media_next, with their short labels such as "←Sel",
"Word→", "DocStart"), or every command allowed on the nav mode surface labelled
"<source>: <name>"; Cancel and Save. Save writes the whole 26-key map to the private file,
stamps `nav_mode_mappings_updated`, and shows "settings save failed" on error. An entry
saved as None is written as type none, so a default is overridden rather than restored.

The screen note: "Note: Modifying Fn Layer keys also affects Ctrl+key combinations in text
fields."

### 5.9 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `nav_mode_enabled` | boolean | true | whether Ctrl double tap can latch nav mode and whether the map applies with no field | Fn Layer | "Enable Fn Layer" |
| `nav_mode_ctrl_hold_enabled` | boolean | false | whether a physically held Ctrl inside a field uses the map instead of passing Ctrl+key to the app | Fn Layer | "Ctrl-hold navigation" |
| `layout_aware_ctrl_shortcuts` | boolean | false | whether a passed-through Ctrl+letter is translated through the active text layout first | Fn Layer, Advanced | "Layout-aware app Ctrl shortcuts" |
| `nav_mode_default_mappings_version` | int | 1 (unset), current 3 | which default-map patches have been applied to the user's file | none (internal) | |
| `nav_mode_mappings_updated` | long | unset | timestamp; any change makes the running keyboard reload the map | none (internal) | |
| `ctrl_key_mappings.json` in the private files dir | file | copy of the asset | the user's Fn Layer map | Fn Layer key grid | "Key Mappings" |

## 6. Touch-screen-awake

Touches delivered to an input method window are not always counted as user activity, so the
screen could dim and lock while the user was tapping the strip. Since commit 6cae033 ("keep
screen awake after touch"):

| Property | Value |
|---|---|
| Trigger | every touch down (action down only; moves and ups do nothing) on the keyboard's chrome layout, that is the strip and everything drawn in the keyboard window |
| Mechanism | a screen-bright wake lock with the "on after release" option, tag "PhysiBoard:ImeTouch", not reference-counted, acquired with a 200 ms timeout |
| Renewal | a new down while the pulse is held releases it and acquires again, so the pulse always ends 200 ms after the last down |
| Effect | when the pulse ends, Android restarts its normal screen-timeout countdown as if the user had touched the screen |
| Release | when the chrome layout is detached from its window (keyboard window torn down), any held pulse is released at once |
| Permission | `android.permission.WAKE_LOCK` |

There is no preference. The screen trackpad overlay additionally carries the keep-screen-on
window flag for as long as it is up (2.4), so the display cannot time out mid-drag.

## 7. Timings and limits (summary)

| Quantity | Value |
|---|---|
| Screen trackpad hold threshold | 250 ms |
| Screen trackpad double-tap window | 1 to 400 ms from trigger up to next trigger down |
| Horizontal step | 8 to 64 px, default 32 |
| Vertical step | 2 times horizontal |
| DPAD events per motion event | at most 12 |
| Hint pill top margin | 56 dp |
| Permission poll on the trackpad screen | every 1500 ms |
| Keyboard-surface swipe thresholds | 120 to 750 px, default 500 (both) |
| Keyboard-surface swipe straightness | cross-axis travel below one quarter of the main-axis travel |
| Keyboard-surface swipe velocity | at least 2.0 px/ms |
| Native swipe debounce | 250 ms between accepted gestures |
| Trackpad third width | 1440 px surface split at 480 and 960 |
| Word delete lookback | 100 characters |
| Suggestion replacement lookaround | 64 characters each side |
| Caret badge glyph height | 11 sp; ⌥ at 1.3 times |
| Caret badge gap, halo | 4 dp; 1.1 dp stroke, white alpha 225 |
| Caret badge alphas | 245 full, 140 faint |
| Caret badge offset | 4 dp right of the caret; glyph feet 18 percent into the line box |
| Cursor update retries | at 80, 250, 600, 1200 ms; at most 8 requests per editor |
| Nav mode double-tap window | 500 ms from Ctrl up to next Ctrl down |
| Nav mode haptic | 70 ms |
| Strip refresh after a nav keycode in a field | 50 ms |
| Nav mode selection helpers text window | 1000 characters each side |
| Screen-awake pulse | 200 ms |
| Debug activity log | last 500 lines |

## 8. Titan 2 Elite facts

D1 to D6 are in section 3.1. Additional:

| # | Fact | Evidence |
|---|---|---|
| D7 | The display is 1080 by 1200 physical pixels at density 300, about 574 by 640 dp; one dp is 1.875 px. The 32 px default step is therefore about 17 dp of finger travel per cursor move, and "4-inch screen" in the tuning commit refers to this panel. | DEVICE.md; commit fd3c5ad |
| D8 | The first-run baseline captured from the maintainer's phone stamps `screen_trackpad_enabled` true and `screen_trackpad_step_px` 32 on a fresh install. | baseline comment "Re-captured from the phone on 2026-08-27 (app 1.2.3)"; changelog 1.0.4 |
| D9 | The Elite's keyboard is identified as `titan2elite_qwerty` (fingerprint rules), which is why it takes the "not titan2" branch of the upstream trackpad device rule and the native provider's "Titan 2 family" test both. | keys document D14; commit ae06398 |
| D10 | Fn never sends key-up and a quick Fn tap delivers nothing, so a double tap of the physical Fn cannot enter nav mode unless the vendor "Fn as Ctrl" mapping produces real Ctrl events. | DEVICE.md Fn delivery model; keys document |

## 9. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| Hold mode, the user holds Space to type repeated spaces | after 250 ms the trackpad opens instead; no spaces are typed; the swallowed down is not replayed when the hold succeeds | the hold is the feature; the repeat events are swallowed with it |
| Hold mode, the user types a fast Shift+letter chord with Shift as trigger | the trigger down is replayed the moment the letter arrives; the letter is capitalised normally | chord detection replays before the other key is routed |
| Hold mode, hold succeeds but the permission is missing | toast, key replayed as a plain press | the overlay cannot be added without the permission |
| Double tap with Space as trigger | the first tap always types a space; the second opens the pad | the first down is deliberately not consumed |
| Single tap with Space as trigger | Space can no longer type | every fresh down is consumed |
| Trackpad open, user presses letters | letters type under the overlay | only the trigger and Back (sticky) are consumed |
| Trackpad open, no input connection | drags do nothing | the DPAD events have nowhere to go |
| Shift latched visually before the drag | the pill says "Select" and DPAD events carry Shift | the layer latch counts as Shift active |
| Caps lock on, no Shift | plain cursor moves | caps lock is not "Shift active" for the pad |
| Strip dips for a "Text box under the bar" app while the pad is open | the pad stays open | the dip is not a real window hide |
| Sym chosen as trigger | hold-Sym-for-assistant never arms | two things would fight over one hold |
| Trackpad reader (Shizuku provider) on the Elite | never sees a swipe | it opens `/dev/input/event7`, which is `ff_key` on the Elite (D4) |
| Native provider third calculation | uses a 1440 px surface on a 1080 px layer | inherited from upstream; the right third needs x >= 960 (D2 unverified) |
| Keycodes 322 and 404 on a fresh install | swallowed silently | `swipe_to_delete` off by default; they are consumed either way |
| Trackpad debug activity | unreachable from the app | its launcher is never invoked |
| Trackpad debug overlay service | cannot start | not in the manifest |
| Caret badge in an app that never reports the caret | never appears | nothing to anchor to; cursor updates are still requested up to 8 times |
| Caret badge, the editor rejects the first cursor-update request | retried at 80, 250, 600, 1200 ms and on refresh | some editors refuse the first request (commit f0bf8d4) |
| Caret badge, second field of a session | works | the request is re-issued per editor (commit ea182c4); before that it worked only in the first field |
| Caret badge, caret scrolled out of the editor's viewport | hides | invisible-region flag without visible-region flag |
| Caret badge, caret near the right edge | flips to the left of the caret | otherwise clamping would push it over the text |
| Caret badge, caret on the first line at the top of the screen | drops below the line | y would be negative |
| Caret badge in nav mode | no CTRL item | would be pinned permanently |
| Caret badge, Alt symbol missing from the font | shows "ALT" | tofu is worse than the word |
| Caret badge, window manager rejects the window | off until the keyboard restarts | not retried per keystroke |
| Caret badge, permission granted while a field is open | shows on the next refresh | permission re-checked every show |
| Caret badge created from the service context (pre-Android 12 or window-context failure) | works, but the system logs a type mismatch on every layout pass | overlay window from an input-method context |
| Nav mode entered, then a text field focused | nav exits, field types; nav returns when the field finishes | "remembered as was on" |
| Ctrl double tap inside a field, then leave the field | nav mode is now on | the preserve rule promotes a Ctrl lock into nav mode |
| Nav mode on, Back pressed | passes to the system, nav stays | persistent by design (upstream comment) |
| Nav mode on, P pressed | passes to the system | `toggle_minimal_ui` has no handler; the strip labels it "Mini" |
| Nav mode on, G pressed | passes to the system | mapped to none |
| Nav mode on, letter mapped to page_start with no field | not handled | page_start is only implemented on the in-field path |
| Nav mode on, strip | entirely hidden | layout set gone while nav is active |
| Nav mode on, Enter | DPAD_CENTER | fixed, not in the map |
| Nav on, the user double taps Fn on the Titan | nothing (D10) | Fn sends no down/up pair |
| Ctrl-hold navigation off, physical Ctrl+letter in a field | passes to the app even if the letter is mapped | native shortcuts win; only the latch or the option uses the map |
| Ctrl-hold on, Ctrl+B in a field with the default map | the on-screen keyboard mode toggles | B is a command by default |
| Layout-aware shortcuts on with Ctrl-hold on | the switch is disabled and the physical key is used | held Ctrl never reaches the app then |
| Mapping file corrupt or unreadable | assets map used | fallback on read error |
| `nav_mode_default_mappings_version` restored from an old backup | the patch is not re-applied to a map the user had already edited | the key is migrated with the settings |
| Touch on the strip while the keyguard is showing | 200 ms wake pulse | the pulse restarts the timeout when released |

## 10. Test cases

Each row is a decision a JVM test can encode without a device. "pad" is the screen
trackpad, "map" the Fn Layer map.

| # | Input | Expected |
|---|---|---|
| T1 | pad enabled, trigger space, hold; Space down (repeat 0) at t=0; Space up at t=100 | down consumed; up consumed; the normal pipeline receives the replayed down then up exactly once; overlay never shown |
| T2 | as T1 but Space up at t=300 with the overlay permission present | overlay shown at t=250; up at 300 removes it and is consumed; nothing replayed |
| T3 | pad hold; Space down at 0; A down at 50 | Space down replayed at 50; A not consumed; Space up at 120 not consumed |
| T4 | pad hold; Space repeat event (repeat count 2) while pending | consumed |
| T5 | pad hold; Space down with Ctrl meta set | not consumed |
| T6 | pad hold; overlay permission missing; Space held 300 ms | at 250: toast, down replayed; Space up not consumed |
| T7 | pad double_tap, trigger shift_either; Left Shift down/up at 0/40; Left Shift down at 300 | first down not consumed; second down consumed and overlay shown sticky |
| T8 | as T7 with the second down at 500 | second down not consumed (400 ms window missed); its up records a new release time |
| T9 | as T7 but second key Right Shift | not consumed; a first tap of Right Shift |
| T10 | pad single_tap, trigger sym; keycode 63 down | consumed, overlay shown sticky |
| T11 | sticky overlay shown; KEYCODE_BACK down | consumed, overlay removed |
| T12 | sticky overlay shown; letter A down | not consumed |
| T13 | hold overlay shown; trigger down again with repeat 3 | consumed, overlay stays |
| T14 | step 32, Shift off; finger down at (100,100); move to (170,100) | two DPAD_RIGHT down/up pairs sent; accumulator left at 6 |
| T15 | continue T14; move to (180,100) | no event (6 + 10 = 16, below one step); accumulator 16 |
| T16 | step 32; finger down (0,0); move to (0,70) | one DPAD_DOWN (vertical step 64); accumulator 6 |
| T17 | step 32; finger down (0,0); move to (0,-64) | one DPAD_UP |
| T18 | step 8; single move of 200 px right and 200 px down | 12 events total: 12 DPAD_RIGHT, 0 DPAD_DOWN (budget spent horizontally); horizontal accumulator 104 |
| T19 | Shift held; move 32 px left | one DPAD_LEFT down and up, both with META_SHIFT_ON and META_SHIFT_LEFT_ON |
| T20 | finger up with 30 px accumulated, then a new finger down and 5 px move | no event; accumulators reset on up and down |
| T21 | step preference written as 4, then 100 | read back as 8, then 64 |
| T22 | trigger preference written as "bogus" | read back as `space`; activation "bogus" reads as `hold` |
| T23 | add-word rule: third 0, gesture on, full width off, candidate "PhysiBoard", no suggestions | add |
| T24 | add-word rule: third 2, gesture on, full width on, candidate present, no suggestions | add |
| T25 | add-word rule: third 1, gesture on, full width on, candidate present, suggestions ["past","paste"] | do not add (select the centre slot instead) |
| T26 | add-word rule: third 0, gesture off | do not add |
| T27 | third mapping | 0 -> engine index 2; 1 -> 0; 2 -> 1 |
| T28 | native swipe: start (200,500), end (210,150), 100 ms | upward distance 350: rejected at the default threshold 500; accepted with threshold 300, third 0 |
| T29 | native swipe: start (700,500), end (900,100), 100 ms, threshold 300 | rejected: abs(dx)=200 is not below 400/4 |
| T30 | native swipe: start (700,500), end (700,100), 400 ms, threshold 300 | rejected: velocity 1.0 px/ms |
| T31 | two accepted native swipes 200 ms apart | the second is debounced |
| T32 | native left swipe 600 px in 100 ms, `swipe_to_delete` false | not accepted |
| T33 | keycode 322 in a field, `swipe_to_delete` true, provider `titan2_keycode`, text "hello world" before the cursor | "world" deleted; consumed |
| T34 | keycode 404 in a field, provider `native_ime` | consumed, nothing deleted |
| T35 | device name "titan", any firmware | legacy device `/dev/input/event7` |
| T36 | "titan2", "Titan 2_EEA_V01.00.12-20260206" | `/dev/input/event7` |
| T37 | "titan2", "Titan 2_TEE_V01.00.14-20260422" | `/dev/input/event6` |
| T38 | "titan2", "unknown" | `/dev/input/event7` |
| T39 | "titan2elite_qwerty", "V02.00.02" | `/dev/input/event7` |
| T40 | badge items: caps lock on, Alt one-shot, Ctrl held, Sym page 2 | [arrow locked full, ⌥ armed full, CTRL armed faint, SYM armed full] in that order |
| T41 | badge items: Ctrl latched from nav mode only | empty |
| T42 | badge items: Shift held and Shift one-shot both set | one arrow, armed, full (one-shot row wins) |
| T43 | badge items: nothing on, Sym page 0 | empty; badge hides |
| T44 | badge placement: screen 1080 wide, badge 60 wide, caret x=1050, top 100, bottom 140, glyph bottom offset 20 | x = 1050 - 7 - 60 = 983 (4 dp = 7 px); y = 100 + 7 - 20 = 87 |
| T45 | badge placement: caret top 5, bottom 45 | y = 5 + 7 - 20 < 0, so y = 45 - 7 = 38 |
| T46 | caret report with horizontal NaN | caret unusable; badge hides |
| T47 | caret report with invisible-region flag and no visible-region flag | unusable |
| T48 | caret report with both flags | usable |
| T49 | badge enabled; editor accepts the request on the third attempt | requests at 0, 80, 250; none at 600 or 1200 |
| T50 | badge enabled; editor never accepts | 5 requests from the schedule, then refresh-driven retries stop after the 8th request total |
| T51 | tint: locked colour 0xFFDC2626, faint | argb(140, 0xDC, 0x26, 0x26) |
| T52 | nav: no field; Ctrl down/up at 0/50; Ctrl down at 400 | latched from nav mode; consumed |
| T53 | nav: Ctrl down/up at 0/50; A down at 100; Ctrl down at 200 | not latched (the tap is not consecutive) |
| T54 | nav: Ctrl down/up at 0/50; Ctrl down at 600 | not latched (500 ms window) |
| T55 | nav on; Ctrl down | nav off, consumed |
| T56 | nav on, default map; E down | DPAD_UP down and up sent |
| T57 | nav on, default map; Z down | undo context-menu action |
| T58 | nav on; K down mapped `command` `device.home`, no input connection | home intent started; handled |
| T59 | nav on, default map; G down | not handled |
| T60 | nav on; Enter down with a connection | DPAD_CENTER down and up |
| T61 | nav on; I down mapped native_ctrl | I down and up with Ctrl meta |
| T62 | in a field, Ctrl held, Ctrl-hold off, D mapped DPAD_LEFT, QWERTY | Ctrl+D passed through once, keycode D |
| T63 | as T62 with layout-aware on and QWERTZ, key Y | passed through as Ctrl+Z |
| T64 | as T62 with Ctrl-hold on | DPAD_LEFT down and up |
| T65 | as T64 with Shift also held | DPAD_LEFT down and up with Shift |
| T66 | Ctrl-hold on, layout-aware on, QWERTZ, Y mapped PAGE_UP, Z mapped PAGE_DOWN; Ctrl+Y | PAGE_UP (physical grid, no translation) |
| T67 | latch from nav mode, same map, Y without Ctrl meta | PAGE_UP |
| T68 | Ctrl one-shot, layout-aware on, QWERTZ, map Z -> undo; key Y | undo (the one-shot path translates) |
| T69 | Ctrl held, Ctrl-hold on, B unmapped in a custom map | Ctrl+B passed through |
| T70 | Ctrl held, Ctrl-hold on, B -> `pastiera.toggle_software_keyboard_mode`, mode FORCE_HARDWARE | runtime override becomes FORCE_VIRTUAL; stored mode unchanged |
| T71 | reset modifiers preserving nav with a plain Ctrl latch | latch kept and now marked from nav mode |
| T72 | reset modifiers not preserving with the latch from nav mode | latch cleared; nav cancelled callback fired |
| T73 | mapping file version 1 with N absent, U of type none, B mapped keycode DPAD_UP | after migration N = move_word_left, U = expand_selection_word_left, B unchanged; version 3 stored |
| T74 | screen-awake: touch down, wait 999 ms, wait 2 ms | held; not held |
| T75 | screen-awake: down at 0, down at 800, wait to 1600, then 1801 | held at 1600; released by 1801 (pulse 1000 in the test) |
| T76 | screen-awake: move and up only | never acquired |
| T77 | screen-awake: down then release | not held, and still not held 2 s later |

## 11. Keep / Drop for 3.0

3.0 is Titan-only and has no on-screen keyboard.

| Item | Decision | Reasoning |
|---|---|---|
| Screen trackpad, hold mode on Space | keep | the maintainer's default; the only way to move the cursor by touch in every app |
| Trigger key choice (Space, Shifts, Sym) | keep | cheap; Sym as trigger already interacts with the assistant hold, keep that rule |
| Double tap and single tap sticky modes | undecided | sticky exists; single tap eats a key; nobody on a Titan is known to use either |
| 250 ms hold, 32 px step, 2x vertical, 12 events per move, no acceleration | keep | tuned on the Elite; the trackpad's whole feel |
| Replay of a swallowed trigger through the normal pipeline | keep, redesign | 3.0's key pipeline should decide the trigger before typing it rather than swallow and replay |
| Hint pill | keep | the only visual sign the pad is open |
| Broker grant of the overlay permission | keep | one pairing applies every privileged step |
| Keep-screen-on on the overlay | keep | free |
| Trackpad debug activity and overlay service | drop | unreachable and unregistered |
| Keyboard-surface swipe, Shizuku provider | drop | reads the wrong device on the Elite; Shizuku is gone from the app |
| Keyboard-surface swipe, native provider | undecided | the touch layer (D1, D2) is real hardware nobody has used from the IME on the Elite; needs a capture on the phone before deciding (brobata/physiboard#11 offers one from an Elite); if kept, thirds must be measured again and the rows need a screen (settings-catalog.md section 13)st 1080 not 1440 |
| Add-word gesture rule | drop unless the native provider is kept | it has no other trigger |
| Firmware swipe keycodes 322 / 404 | undecided | needs a capture of what the Elite firmware sends |
| Caret badge | keep | the reason the LED strip is off by default |
| Caret badge item rules, colours, halo, placement | keep | all measured on the phone over several sessions |
| Window-context creation for the overlay | keep | otherwise a log line per caret move |
| Cursor update retries 80/250/600/1200, cap 8 | keep | editors refuse the first request on real hardware |
| Shared cursor-update switch with the emoji search | drop | 3.0 has no emoji picker search in the strip unless the pickers document keeps it |
| Nav mode entered by Ctrl double tap with no field | keep, verify | the one thing that needs the Fn-as-Ctrl vendor mapping to deliver real Ctrl events; unverified on the Titan (D10) |
| Fn Layer map, file format, migration | keep, simplify | keep the JSON contract and the 26-key map; drop the version patching by starting 3.0 at the current defaults |
| Default map | keep, except P | `toggle_minimal_ui` on P is dead; give P a real default or none |
| Keycode, action, native_ctrl, none types | keep | |
| Command type | keep | the launcher and device commands are Titan features |
| Ctrl-hold navigation | keep | the maintainer's guide text explains it; off by default |
| Layout-aware app Ctrl shortcuts | undecided | only matters for non-QWERTY text layouts on the QWERTY Titan |
| Strip hidden while nav is on | keep | nothing to suggest with no field |
| System status icon for nav mode | keep | the only indicator with the strip hidden |
| Legacy nav mode notification and channel | drop | never posted, only cancelled |
| Nav mode preserve rule promoting a Ctrl lock | undecided | surprising; a Ctrl lock in a field becoming nav mode outside it may be a bug rather than a feature |
| Software-keyboard Ctrl and Alt preview labels for the map | drop | soft keyboard only |
| "Set Fn key to Ctrl" card | keep | device document owns it; it is the precondition for nav mode on the Titan |
| Touch-screen-awake pulse | keep | strip touches otherwise let the screen lock |

## 12. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/docs/spec/status-bar.md (format and cross-references)
- /home/disdiqqq/projects/pastiera/docs/spec/keys-and-modifiers.md (cross-references, D13 to D15)
- /home/disdiqqq/projects/pastiera/docs/titan2elite/DEVICE.md
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
- /home/disdiqqq/projects/pastiera/.claude-context.md
- /home/disdiqqq/projects/pastiera/app/src/main/AndroidManifest.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/ctrl/ctrl_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ScreenTrackpadController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ScreenTrackpadSetup.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ScreenTrackpadSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/trackpad/TrackpadEventDeviceResolver.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/trackpad/TrackpadGestureDetector.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TrackpadDebugActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TrackpadDebugOverlayService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/TrackpadAddWordGesturePolicy.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AddWordCommitHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/CaretBadgeController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/CaretBadgeView.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/StatusBarButtonsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/NavModeController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/NavModeHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/NavModeSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ModifierKeyHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/ModifierStateController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/TextSelectionHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/KeyboardVisibilityController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/NotificationHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PrivilegedSetup.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ImeTouchScreenAwakeController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsMigration.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TutorialActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/mappings/KeyMappingLoader.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/commands/NavCommandSource.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/commands/PhysiBoardCommandSource.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/commands/DeviceControlCommandSource.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/TrackpadAddWordGesturePolicyTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/trackpad/TrackpadEventDeviceResolverTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterCtrlHoldNavModeTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/ImeTouchScreenAwakeControllerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/ModifierStateControllerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodServiceDeviceBehaviorTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/SettingsMigrationTest.kt
- git log (commits 6cae033, ff0cb84, fd3c5ad, 7a416e3, 8fad010, d837c83, ea182c4, f0bf8d4, 56e68e5, d2a9353, 0d5220e, 0583865, a5b7b2a, a44a9b6, 60bd83b, 867330f, 82e0e48, ae06398, 625b185, 389f249, b78d715)
