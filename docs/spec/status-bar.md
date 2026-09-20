# Status bar: the on-screen strip, its buttons, visibility, insets, and the per-app dip

This document describes the strip PhysiBoard draws at the bottom of the screen while a text
field has focus on the Titan 2 Elite. Android calls this surface the input method's
"candidates view"; PhysiBoard calls it the status bar. It carries the three suggestion slots,
the configurable buttons at its two edges, an optional LED row underneath, and the surface on
which the Sym pages (emoji, symbols, clipboard, emoji picker) open. It is the only piece of
PhysiBoard the user can touch.

What the suggestion engine puts into the slots is specified in the autocorrect document; the
Sym pages themselves are specified in the layers document; the caret badge is in the trackpad
and caret document. Here they appear only where the strip's own behavior depends on them.

Everything below is the 2.x behavior on the Titan 2 Elite unless a row says otherwise.

## 1. Vocabulary

- **Strip** or **bar**: the whole on-screen chrome PhysiBoard owns: suggestion row, buttons,
  LED row, Sym surface. When the Sym surface is closed and LEDs are off, the strip is exactly
  one row, the suggestion row.
- **Candidates surface**: Android's candidates view. The strip is rendered there whenever the
  system decides the on-screen keyboard is not to be shown, which on a Titan with the system
  setting "show virtual keyboard with hardware keyboard" off is always. The other surface,
  the "input view", is the on-screen keyboard; the strip renders in it too, above the soft
  keys, when the software keyboard is forced. Both surfaces carry an identical strip; only
  one is on screen at a time.
- **Refresh**: PhysiBoard rebuilds a snapshot of its state (modifiers, Sym page, suggestions,
  clipboard count, the app being typed into) after every key, every selection change, every
  window show and every relevant preference change, and hands it to the strip. The strip is
  only redrawn when the snapshot, the Sym mappings, the connection to the app or the
  effective keyboard mode actually changed since the last draw.
- **Slot**: one of the three equal-width suggestion boxes. Left, center, right.
- **Edge button**: the outermost button on each side, drawn wider with its outer bottom
  corner rounded to hug the display corner.
- **Hardware mode**: the software keyboard mode resolves to anything but "force virtual". On
  the Titan this is the normal state; the strip is then the whole of PhysiBoard's chrome.
- **Nudge** or **dip**: the strip hiding and re-showing itself for an app on the "Text box
  under the bar" list (section 12).

## 2. Screen geometry on the Titan 2 Elite

The display is 1080 by 1200 physical pixels at density 300, which Android exposes as a window
of about 574 by 640 dp (D1). One dp is 1.875 px; every dp value below is converted to pixels
by multiplication and truncation. The numbers that matter:

| Quantity | dp | px on the Titan |
|---|---|---|
| Bar height, default | 56 | 105 |
| Bar height, options | 36, 48, 64 | 67, 90, 120 |
| Button size at 56 dp bar (bar minus 4 dp, never under 24 dp) | 52.x | 98 |
| Edge button width (button size times 1.45) | 75.x | 142 |
| Gap between buttons and between slots | 3 | 5 |
| Extra inset the edge button adds on its side (button times 0.45) | 23.x | 44 |
| Slot horizontal padding | 12 | 22 |
| Slot vertical padding at the hardware default theme scale 1.4 (3 dp times scale) | 4.2 | 7 |
| Slot text, maximum, at scale 1.4 (14 sp times scale, clamped 12..20, truncated) | 19 sp | |
| Slot text, minimum, at scale 1.4 (7 sp times scale, clamped 7..12, truncated) | 9 sp | |
| Display corner radius fallback used for the bar's bottom corners | 24 | 45 |
| Edge button outer corner radius (button size times 0.9) | 46.x | 88 |
| Button corner radius (button size times theme key rounding 0.10) | 5.2 | 9.8 |
| Slot corner radius (bar height times theme chrome rounding 0.10) | 5.6 | 10.5 |
| Border on slots, buttons and modifier chips | 1 | 1 |
| LED row: LED height, top padding, gap, corner | 5.5, 1, 1.5, 3 | 10, 1, 2, 5 |
| Modifier chip (preview only, see 8.3) | 26 square, 2 gap, 20 left inset | 48, 3, 37 |
| Clipboard badge text | 10 sp bold | |
| Backlight-paused dot | 9 | 16 |

With the default configuration (one left button, two right buttons, both edges rounded), the
suggestion row is inset 147 px on the left (98 + 5 + 44) and 250 px on the right
(2 times 98 + 5 + 5 + 44), leaving 683 px for the three slots: about 224 px each with two
5 px gaps. The Titan is the narrowest Titan except the Slim, 75.0 mm across (D2), and the
buttons are sized for that width.

Bar height on the hardware strip comes from the "Bar height" setting, not from the theme. The
theme's "Suggestions height" scale sizes the strip only above the on-screen keyboard, but it
still drives the slot text size and vertical padding in hardware mode, which is why the
default hardware theme carries scale 1.4 (section 9.4).

## 3. Where the strip lives and when it is on screen

### 3.1 The two surfaces

PhysiBoard keeps two identical strips, one for the candidates surface and one for the input
view, and treats them as one. Every listener, every update and every count reaches both. When
the input view is active the candidates strip is collapsed to zero height, because Android
hides its candidates frame with "invisible", which still reserves the child's measured height
and would otherwise leave a ghost band above the soft keys. When the system returns to
candidates-only mode the strip's height is restored explicitly.

### 3.2 The system decision

Every time an app asks for the keyboard, and every time PhysiBoard asks for itself, Android
asks the input method whether the input view should be shown. PhysiBoard answers "yes" only
when the software keyboard mode resolves to "force virtual"; the system's own opinion (based
on the hardware keyboard and the "show virtual keyboard" setting) is recorded for the
auto-detector but does not decide. When the answer is "no", PhysiBoard posts, on the next UI
turn, "candidates surface active, candidates view shown", and one turn after that forces the
enclosing container back to visible, because Android can leave that container invisible when
an already-open window switches to candidates-only mode. A later evaluation cancels any
earlier posted one that has not run yet, so a stale decision never overrides a newer one.

The window itself is a normal input method window that never uses fullscreen (extract) mode.

### 3.3 What brings the window up

- **A text field gains focus.** On every field start (not a restart) with an editable field
  and "Show keyboard automatically" on (`auto_show_keyboard`, default true), PhysiBoard asks
  Android to show itself if its view is not already shown and the field is really editable.
- **A key is pressed.** On every hardware key-down while a field is editable and the view is
  not shown, and on every long press, PhysiBoard asks Android to show itself, unless nav mode
  is latched. The request is skipped entirely when there is no connection to a field.
- **A strip button opens a Sym page** (clipboard, emoji picker, symbols): the request is made
  first so the page has a window to draw in. The dedicated emoji key does the same.
- **The app requests input.** Android's own path. PhysiBoard does not gate on it.

There is no "did anyone ask for a keyboard" gate: once a field is focused and the evaluation
runs, the candidates surface is shown. This is why the strip floats over a launcher's home
screen even though nobody requested input (section 3.6).

### 3.4 What takes it down

- The app or the system hides the input method (the field loses focus, Back, the app
  finishes input). Android hides the window; PhysiBoard's window-hidden handling runs
  (section 13) unless a nudge is in flight.
- Nav mode is latched: the strip's root is made invisible in the layout (it contributes no
  height) but the window stays up.
- The "Show status bar" setting says no for this app (section 3.5): the root collapses to
  zero height. The window still exists, the input method is still active and hardware keys
  still work; the strip has no footprint and its touchable band is empty.
- A key that closes the window from PhysiBoard's side (a keyboard-mode transition that
  requests hide) hides it.

### 3.5 "Show status bar": always, never, only in these apps

The decision is made on every refresh from `status_bar_visibility` and the app being typed
into:

| Mode | Strip shown when |
|---|---|
| `ALWAYS` | Always. |
| `NEVER` | Never (the strip collapses to zero height). |
| `APPS` | The package of the field's app is in `status_bar_apps`. No package (no field) counts as not listed. |

Exception: an open Sym page is content the user asked for with the Sym key, so a Sym page
still shows while the mode says hidden, and the strip collapses again when the page closes.
Before this exception the Sym key did nothing at all with the bar hidden (changelog 1.0.6).

The app list is seeded the first time it is read with twenty messaging, mail and social
packages: Gmail, Google Messages, AOSP Messaging, WhatsApp, WhatsApp Business, Messenger,
Messenger Lite, Facebook, Instagram, Telegram, Signal, Discord, Slack, Teams, Outlook,
Snapchat, X, Reddit, LinkedIn, Google Chat (their package names are in the settings
catalog). The settings page shows only the installed ones, sorted by label, each with a
"Remove" button, and an "Add an app" button that opens the app picker.

Reading the mode when `status_bar_visibility` is absent falls back to the older boolean
`show_status_bar`: true or absent means `ALWAYS`, an explicit false means `NEVER`. Writing the
mode writes both keys (the boolean is true for anything but `NEVER`). The strip carries the
suggestions, so silence about it is not a request to hide it; an earlier build that defaulted
to hidden took the suggestion bar away from everyone who set the keyboard up from Android's
settings and never opened the app.

A change to the mode, the app list, the height, or the slots re-renders the strip live: the
input method listens to its preferences, invalidates the render cache and refreshes on the
next UI turn, with no restart. A change to the rounded-corner setting re-requests window
insets instead. A change to the theme refreshes on the next turn.

### 3.6 Launchers

Niagara Launcher parks focus in a hidden search field on its home screen so hardware typing
filters apps. That field is a filter field (input type 0xb1), which PhysiBoard already treats
as restricted, and the launcher's window declares that input should always stay hidden. The
strip goes up anyway: PhysiBoard's evaluation calls "show candidates" for any focused editor,
and Android brings the window up when candidates are requested even if the app never asked
for input. Confirmed on the Titan with the real Niagara on 2026-08-31: the home window is
in state always-hidden, nothing was requested explicitly, the input view is not shown, and
yet the window is visible with the candidates visible (D6). Gboard on the same field shows no
window. It is not sticky: the strip drops when focus leaves. It does not steal taps, because
the touchable band is bounded to the strip (section 11). The app drawer's search field is a
legitimate request and is not part of the bug. The shipped workaround is "Only in these apps"
with the launcher off the list; that empties the window, it does not stop it being forced up.
The maintainer chose to document rather than fix; any fix must discriminate on "did the app
request input", and must not gate purely on that, because a field focused by hardware Tab
never triggers a request and the strip would be lost until the first keystroke.

## 4. Layout of the strip

From top to bottom, inside one vertical container whose background is the theme background:

1. **The suggestion row**, a frame exactly bar-height tall. Inside it, layered: the three
   slots (a horizontal row inset by the button widths on each side), a modifier chip row at
   the left edge (never visible in the live strip, see 8.3), the left button group at the
   left edge, the right button group at the right edge, the hamburger overlay (full frame,
   hidden until opened), and the backlight-paused pill and dot (right-anchored, hidden
   unless the smart backlight is paused).
2. **A legacy modifier container**, always hidden. It once held the modifier indicators.
3. **The Sym surface**: a frame that holds, stacked, the Sym content (emoji grid, symbols
   grid, clipboard list, emoji picker, or the software keyboard) and the LED row, plus a
   close button drawn over the stack. When no Sym page is open the content is gone and the
   surface is exactly the LED row, or nothing when LEDs are off.

The container's own padding is zero at the top and, from the window insets: bottom = the
larger of the navigation or gesture inset and the display cutout inset (ignoring visibility,
so a hidden gesture bar still counts); left and right = the larger of the navigation/gesture
and cutout insets on that side, but only when the rounded-corner setting is on, otherwise
zero. The strip is clipped to its bounds and to its padding.

With the rounded-corner setting on (`titan2_elite_rounded_corner_insets`, default true on
the Elite only, D3), the bar's background rounds its two bottom corners to the display's
reported bottom-corner radius, falling back to 24 dp when the system reports none; the top
corners stay square because the bar butts against the app. With the setting off the
background is a plain rectangle and the edge buttons are ordinary buttons.

The touch-to-wake behavior on the strip (any touch on the chrome keeps the screen awake) is
part of the backlight document.

## 5. The suggestion row

### 5.1 Slot contents

The engine's ordered suggestion list is mapped onto the slots as:

| Slot | Content |
|---|---|
| Left | The add-word candidate if there is one and it does not already appear (case-insensitively) among the suggestions; otherwise the third suggestion, or empty. |
| Center | The first suggestion, or empty. |
| Right | The second suggestion, only when there are at least two; otherwise empty. |

Empty slots are still drawn, identical in color to full ones, so the row never jumps; they
are not clickable and are hidden from accessibility. Text is centered, one line, ellipsized
at the end, auto-sized between the minimum and maximum sp above in 1 sp steps, normal weight,
in the theme text color (white when there is no theme).

When the add-word candidate is the only content (no suggestions at all), a single slot with
three times the weight spans the whole row instead of three slots.

### 5.2 When the row is hidden

The slots are removed (the frame stays, so the buttons remain) when any of these holds:

- "Suggestions" is off (`suggestions_enabled`, default true).
- The field disables suggestions (password, URL, email, filter, or an app on the raw-mode
  list) and the keyboard is in hardware mode.
- A Sym page is open (in hardware mode; above the on-screen keyboard the overlay pages keep
  the row).
- The clipboard overlay is open.
- No dictionary is installed for the current language (checked by the language code of the
  current subtype), unless text-expansion suggestions are active.

Text-expansion suggestions (up to three, from the expansion subsystem) take precedence over
everything: they show even in a restricted field and without a dictionary, in the same three
slots (left = third, center = first, right = second), with no add-word candidate, and a tap
hands the chosen text to the expansion subsystem rather than the suggestion engine.

### 5.3 Tapping a slot

- **A suggestion**: tap haptic (system "keyboard tap" when `tap_haptic_use_system` is true,
  default; otherwise a fixed vibration of `tap_haptic_duration_ms`, default 25 ms), the slot
  flashes (pressed color for 160 ms), action mode is reset, and the suggestion is committed
  through the same path as accepting it from the keyboard (specified in the autocorrect
  document).
- **The add-word candidate** (drawn with a yellow 18 dp "+" glyph after the text, 6 dp
  apart): tap haptic, flash, the word is added to the personal dictionary and an auto-space
  follows it. Long press: long-press haptic, opens the "add substitution" dialog for that
  word in the settings app.
- **Long press on a suggestion**: long-press haptic, the slot enters **action mode**: its
  text is replaced by one or two icon buttons of equal width, 4 dp padding, 7 dp corners: an
  "eye" (hide this suggestion) always, and a "trash" (delete from the personal dictionary)
  only when the word is in the personal dictionary. Tapping either performs the action,
  triggers a 25 ms haptic, refreshes the strip and leaves action mode. Long-pressing either
  leaves action mode. Action mode also ends when the slot contents change, when the field
  finishes, when the window hides, and before any tap on any slot.

### 5.4 The flash from the trackpad

Committing a suggestion by a trackpad swipe (specified in the trackpad document) flashes the
slot it came from: the swipe's left third maps to the third suggestion, center third to the
first, right third to the second. Adding the word by gesture flashes the left slot. The flash
is the pressed drawable held for 160 ms.

### 5.5 Redraw rules

The row is rebuilt only when the slot texts changed, the theme changed, the height changed,
the insets changed, or action mode is entered or left. If the slots are unchanged, an update
only forces the row and every slot back to fully visible.

### 5.6 Accessibility

The suggestion container is never a live region. With "live announcements" on
(`accessibility_live_announcements_enabled`, default false) and at least one non-blank slot,
the container hides its descendants from accessibility for
`accessibility_suggestions_announcement_delay_ms` (default 500 ms, never negative), then
re-exposes them and announces the non-blank slot texts joined by ", " if they differ from the
last announcement. Any new update cancels a pending announcement. The hidden legacy modifier
container is exposed to accessibility only when "read second row" is on
(`accessibility_read_second_row_enabled`, default false). The language button is never a
live region; its state description reads "Language X, layout Y".

## 6. The buttons

### 6.1 Catalog

Every assignable action, its stored id, its glyph, and what it does. Every tap gives the
system keyboard-tap haptic (undo and redo give the 25 ms haptic instead).

| Id | Label / description | Glyph | Tap | Long press |
|---|---|---|---|---|
| `none` | None / Empty | none | The slot is skipped; nothing is drawn. | |
| `clipboard` | Clipboard / Clipboard history | paste clipboard | Opens the clipboard Sym page (page 3) after asking for the window; a latched Shift or Alt layer is released first. | none |
| `microphone` | Microphone / Voice input | microphone | Starts dictation (the dictation document); a latched layer is released first. | none |
| `emoji` | Emoji / Emoji picker | smiling face | Opens the emoji picker Sym page (page 4). | none |
| `language` | Language / Switch language | the language code as text | Cycles to the next input style (the dictionaries document), with a toast if enabled. | Opens the PhysiBoard settings app. |
| `hamburger` | Menu / Quick actions | three lines | Opens the quick-actions overlay (6.4). | none |
| `software_keyboard_mode` | Keyboard mode / Temporarily open or close the on-screen keyboard (Ctrl+B) | expansion panels | Toggles the temporary software keyboard mode override. | none |
| `settings` | Settings / Open settings | gear | Opens the PhysiBoard settings app. | none |
| `symbols` | Symbols / Symbols keyboard | symbols | Opens the symbols Sym page (page 2). | none |
| `undo` | Undo / Send Ctrl+Z | curved arrow left | Sends Ctrl+Z to the app. | none |
| `redo` | Redo / Send Ctrl+Y | curved arrow right | Sends Ctrl+Y to the app. | none |

The clipboard button carries a badge: the item count in 10 sp bold white text at the top-end
corner (2 dp margin, nudged 2 dp down), hidden at zero. Whenever the count changes to a
different positive number, a red overlay flashes over the button: alpha 0 to 0.4 and back
over 350 ms. The count is pushed to the strip whenever the clipboard history changes and on
every refresh; its accessibility state reads "Empty" or "N items".

The language button shows the first segment of the current subtype's locale in upper case
("EN" for en_US, "DE" for de) or "??" when there is no subtype, with a dashed underline
(dash and gap each the larger of 2 dp and one fifth of the text width, so three dashes span
the text) to hint at the long press. Text is 14 sp, centered, no font padding. Taps are
debounced: a tap within 500 ms of the last accepted tap is ignored; an accepted tap disables
the button at 50 percent alpha for 300 ms, then re-enables it and refreshes its text. The
text is also refreshed on every strip refresh.

The microphone button, while dictation is active, has a red background instead of the theme
button color: initially (255, 80, 80); then, on every audio level report, a red between
(128, 0, 0) and (255, 50, 50) chosen by the square of the level normalized from -10 dB to
0 dB (clamped). Its pressed color while active is the fixed blue (100, 150, 255), not the
theme accent. When dictation stops the button falls back to the unthemed default drawable
(semi-transparent dark gray, argb 100/17/17/17) until the next strip refresh re-applies the
theme. Its accessibility state reads "On" or "Off". See section 17 for the interaction
between this red background and refreshes.

### 6.2 Drawing

Every button is a square of the button size, with the theme's button color as fill, the
theme accent when pressed, a 1 dp border in the theme divider color, and corners of button
size times the theme key rounding ratio (0.175 when there is no theme). Glyphs are tinted
with the theme text-and-icons color. Plain icon buttons draw their 24 dp glyph at native size,
centered; a button with a badge (the clipboard) scales its glyph to fill the square with
padding of 22 percent of the size. The first left button and the last right button are edge
buttons when the rounded-corner setting is on: 1.45 times wider, the outer bottom corner
rounded at 0.9 times the button size, the glyph scaled to fit with 18 percent base padding and
leaned inward by half the extra width (text gravity toward the inner side for the language
button). Buttons on a side are separated by 3 dp; a side with no buttons is hidden.

Buttons are created once and reused across refreshes; a refresh re-attaches them and
re-applies the theme.

### 6.3 Slots and their storage

The layout is: left buttons, then the suggestion slots, then right buttons. Each side is an
ordered list of ids stored as a JSON array of strings:

| Key | Type | Default when absent |
|---|---|---|
| `status_bar_slots_left` | JSON array string | `["hamburger"]` |
| `status_bar_slots_right` | JSON array string | `["emoji","microphone"]` |

Three older keys mirror the first entries and are written alongside: `status_bar_slot_left`
(the first left id, or `none`), `status_bar_slot_right_1` and `status_bar_slot_right_2` (the
first two right ids, or `none`). They are read only as a fallback when the JSON key is
absent or unparsable. Unknown ids in a stored list are replaced by `none` on read and on
write; `none` entries are kept in the list but draw nothing. The 2.0 migration renamed the
lists from their pre-2.0 names (`pastierina_status_bar_slots_left`, `..._right`) and deletes
everything under the old prefix; until 2.0.5 the strip still read the old names, found
nothing, and fell back to its own defaults (hamburger left, language right) regardless of
what the page showed (changelog 2.0.5).

There are three different "defaults" and they disagree:

| Source | Left | Right |
|---|---|---|
| Absent keys (preference default) | hamburger | emoji, microphone |
| First-run baseline written once on a fresh install (the maintainer's captured config) | clipboard | microphone, none |
| The "Reset" button on the settings page | hamburger | emoji, microphone |

So a fresh install shows clipboard on the left and the microphone on the right; pressing
Reset changes that to a menu button on the left and emoji plus microphone on the right.

**The second right slot.** The settings page shows exactly one dropdown per side ("Left
buttons (L1)", "Right buttons (R1)") and no add or remove controls; the underlying lists keep
their remaining entries untouched. The strip, however, renders every entry in the list. With
the preference default the page shows "Emoji" for the right side while the bar shows emoji
and the microphone. Picking a button in a visible dropdown clears that same button from every
other slot, including the hidden ones, so choosing "Microphone" for R1 while the hidden R2 is
the microphone leaves R2 as `none`. Any button can be chosen for any visible slot; the
dropdown lists all eleven ids with glyph, label and description ("None" shows a dash).

### 6.4 The quick-actions overlay (hamburger menu)

Tapping the menu button toggles an overlay that covers the whole suggestion row in the theme
background color. It holds, in one row of equal widths, a close button (an X) followed by nine
fixed buttons in this order: Symbols, Emoji, Microphone, Clipboard, Undo, Redo, Language,
Keyboard mode, Settings. Width per button is (row width minus nine 3 dp gaps) divided by ten;
the row takes up to 8 dp vertical padding but never leaves the buttons under 28 dp tall (39 dp
fallback before layout). Buttons use the theme chrome rounding ratio for their corners.
Tapping any item closes the overlay and performs the action, except Keyboard mode, which
performs without closing. The close button and the hardware Back key close it. The overlay is
also closed whenever the connection to the app changes, and, in hardware mode, on every strip
refresh; on the Titan the menu therefore survives only until the next key press or other
refresh. The overlay carries the clipboard count, microphone state and language text like the
main buttons.

## 7. The LED row

Underneath the suggestion row (and under any open Sym page) when the active theme's
`show_leds` is true (default false). Six equal-width LEDs across the full width with 1.5 dp
gaps, 5.5 dp tall with 1 dp above, 3 dp corners: Shift, Sym, two invisible placeholders, Ctrl,
Alt. Colors from the theme: inactive, active, locked (fallbacks: argb 100/17/17/17, rgb
100/150/255, rgb 247/99/0). A color change animates over 200 ms.

| LED | Locked color | Active color | Inactive |
|---|---|---|---|
| Shift | caps lock on | Shift one-shot armed (a plain physical hold does not light it) | otherwise |
| Ctrl | Ctrl latched (including nav mode) | Ctrl one-shot | otherwise |
| Alt | Alt latched | Alt one-shot | otherwise |
| Sym | page 2 (symbols) | page 1, 3 or 4 | page 0 |

A plain physical hold is deliberately ignored so the row does not flash on every keypress.
The LED row is "additive": it never replaces the other indicators. Until 2.0 it was read from
the theme only in software mode, so "Show LEDs" did nothing on the hardware keyboard. The
LED row's long press is wired to nothing.

## 8. Modifier state and the strip

### 8.1 Where modifier state is shown

Modifier state has three homes, none of which takes room from the suggestion row:

1. **Android's own status bar at the top of the screen**, always in hardware mode: one
   compact icon that encodes Shift, Ctrl and Alt each as off, active (held or one-shot) or
   locked (27 combinations, one icon each), or a Sym icon when no modifier is on and a Sym
   page is open, or a nav-mode icon while nav mode is latched. It is hidden when nothing is
   on and while the software keyboard is forced. It is also hidden when a field finishes,
   unless nav mode stays on.
2. **The LED row** (section 7), optional.
3. **The caret badge** beside the text cursor (`caret_modifier_badge`, default true, needs the
   draw-over-apps permission; colors `caret_badge_armed_color` default 0xFF2563EB and
   `caret_badge_locked_color` default 0xFFDC2626). Specified in the trackpad and caret
   document; the strip only hosts its settings rows.

### 8.2 What the strip does not do

Since commit "keep modifier state out of the keyboard strip", the suggestion row shows no
modifier chips: "Putting Shift and Alt in the suggestions strip cost the left edge of it
whenever either was held, which is most of the time you are typing. The strip is for
suggestions." The chip row still exists in the layout, is asked to update on every refresh,
and is forced hidden every time. The preference `modifier_indicator_mode` is still listened
to (a change refreshes the strip) but nothing reads its value any more.

### 8.3 The chips in the preview

The theme page's hardware preview does enable the chips, so the user sees what the theme's
LED colors look like on a chip: a 26 dp square per active modifier, 2 dp apart, inset 20 dp
from the left edge so they clear the rounded display corner, glyph tinted active or locked
color, background the theme button color with a 1 dp divider border and 7 dp corners. Only
Shift (outline glyph when active, filled when locked) and Alt (option glyph) are ever
chipped; Ctrl and Sym were left out as noise. The preview snapshot has Shift physically held
and Sym page 2 open, so it shows one Shift chip in the active color and, when LEDs are on,
the Sym LED in the locked color. When the chips are visible the slots are inset further by
20 dp plus the chip row width plus 3 dp.

## 9. Theme

### 9.1 What the strip takes from the theme

| Theme field (JSON name) | Used by the strip for |
|---|---|
| `background` | Bar background, Sym surface, hamburger overlay |
| `suggestion` | Slot fill |
| `status_bar_button` | Button fill, chip background, action-mode button fill |
| `accent` | Pressed state of slots, buttons and action buttons |
| `text_and_icons` | Slot text, glyph tint, language text |
| `divider` | 1 dp borders on slots, buttons, chips |
| `key_corner_radius_ratio` | Button corner radius (times button size) |
| `chrome_corner_radius_ratio` | Slot corner radius (times bar height); hamburger buttons |
| `led_inactive`, `led_active`, `led_locked` | LED row, chips |
| `suggestions_height_scale` | Slot text size and padding; bar height only above the on-screen keyboard |
| `show_leds` | LED row |

The remaining fields (`normal_key`, `special_key`, `cursor_swipe`, `key_popup`,
`key_popup_selected`, `key_height_scale`, `number_row_height_scale`, `key_width_scale`,
`row_gap_scale`, `distribute_horizontal_spacing`, `ortholinear`, `variations_height_scale`,
`key_popup_style`, `key_popup_attached`, `key_popup_tail_enabled`,
`key_preview_after_long_press`, `key_alternates_popup_enabled`) belong to the on-screen
keyboard and the retired variation row. Colors are stored as signed 32-bit ARGB integers,
ratios and scales as doubles, flags as booleans, all in one JSON object per theme.

### 9.2 Resolution

There are two theme targets, hardware and software, each with its own chosen theme
(`keyboard_theme_hardware`, `keyboard_theme_software`), its own list of per-input-style
overrides (`keyboard_theme_layout_overrides_hardware`, `..._software`: a JSON array of
objects with optional `locale`, optional `layout`, and `theme`), and, ignored since 2.0, its
own light and dark slots and assignment mode. The strip in hardware mode uses the hardware
target; above a forced on-screen keyboard it uses the software target. Resolution order for a
target: the most specific matching override (locale and layout both match, then locale only,
then layout only; locales are normalized so underscores and hyphens compare equal) beats the
chosen theme; a stored "follow system" mode is ignored so a restored older backup lands on
its chosen theme. A missing or unparsable stored theme falls back to the default with every
field individually defaulted.

The default is "Slate Dark": background 0xFF000000, divider 0xFF2C3136, normal key
0xFF15191D, special key 0xFF2B3138, text and icons 0xFFEFEFEF, LED inactive 0xFF303030, LED
active 0xFF6496FF, LED locked 0xFFF76300, accent 0xFF6496FF, cursor swipe 0xFF6496FF, key
popup 0xFF2B3138, key popup selected 0xFF6496FF, suggestion 0xFF15191D, status bar button
0xFF2B3138, key rounding 0.10, chrome rounding 0.10, and for the hardware target suggestions
height scale 1.4 (D4). Hardware themes saved before that scale existed carry the stock 1.0,
which would pin the row's text at the old size; once per install
(`hardware_bar_height_migrated`) every stored hardware theme, both system slots and every
hardware override whose scale is exactly 1.0 is lifted to 1.4, any other value being a
choice and kept. The software default additionally sets key rounding 0.19, chrome rounding
0.20, key height 1.5489256, number row 0.8, row gap 0.47933885, ortholinear true, LEDs off,
suggestions height 0.8982954, variations height 0.95914257.

### 9.3 Presets

Twenty-four presets are offered. Columns: background, divider, normal key, special key, text
and icons, LED inactive, LED active, LED locked, accent (hex, all fully opaque). Where a
preset overrides the derived fields, they follow the row.

| Preset | bg | divider | normal | special | text | LED off | LED on | LED lock | accent |
|---|---|---|---|---|---|---|---|---|---|
| Slate Dark | 000000 | 2C3136 | 15191D | 2B3138 | EFEFEF | 303030 | 6496FF | F76300 | 6496FF |
| Slate Light | F8FAFC | C7CDD4 | FFFFFF | E0E6EE | 171A1F | D1D5DB | 276EF1 | D65A00 | 276EF1 |
| Cloud Tap | E1E3E7 | D4D7DD | FFFFFF | FFFFFF | 050505 | C2C6CE | 0A84FF | FF9500 | 0A84FF |
| Moon Tap | 111111 | 303030 | 3A3A3C | 3A3A3C | F8F8F8 | 303030 | 409CFF | FF9F0A | 409CFF |
| Classic Cloud | CCD2DC | 9EA5AF | FFFFFF | AFB6C2 | 000000 | AEB5C0 | 007AFF | FF9500 | 007AFF |
| Classic Midnight | 1C1C1E | 4A4A4D | 3A3A3C | 2C2C2E | FFFFFF | 404044 | 0A84FF | FF9F0A | 0A84FF |
| ePaper | F2F2F2 | B8B8B8 | FAFAFA | DDDDDD | 111111 | B0B0B0 | 555555 | 111111 | 3F8C96 |
| High Contrast | 000000 | FFFFFF | 0D0D0D | 000000 | FFFFFF | 555555 | 00E5FF | FFEA00 | FFEA00 |
| Warm | 241F1A | 6F6255 | 352E27 | 5B4734 | FFF1DD | 665A4E | E0B05D | E06A4B | E0B05D |
| Solarized Dark | 002B36 | 586E75 | 073642 | 16424D | EEE8D5 | 586E75 | 2AA198 | B58900 | 2AA198 |
| Solarized Light | FDF6E3 | 93A1A1 | FFFBEC | EEE8D5 | 073642 | B8B7AA | 268BD2 | CB4B16 | 268BD2 |
| Monokai | 272822 | 75715E | 3E3D32 | 49483E | F8F8F2 | 75715E | A6E22E | FFD866 | 66D9EF |
| Dracula | 282A36 | 6272A4 | 343746 | 44475A | F8F8F2 | 6272A4 | FF79C6 | F1FA8C | BD93F9 |
| Nord | 2E3440 | 4C566A | 3B4252 | 434C5E | ECEFF4 | 4C566A | 88C0D0 | EBCB8B | 88C0D0 |
| Volcanic Dusk | 1B141A | 5D3B4F | 2A2028 | 723650 | FFEDF5 | 66515F | FF5D9E | FFB000 | FF5D9E |
| Terminal (Amber) | 0F172A | 334155 | 1E293B | 334155 | F1F5F9 | 475569 | F59E0B | B45309 | F59E0B |
| Terminal (Green) | 000000 | 334155 | 1E293B | 334155 | 33FF88 | 475569 | 33FF88 | 16A34A | 33FF88 |
| Synthwave | 1A1033 | 6D28D9 | 241748 | 3B1E6B | F5D0FE | 4C1D95 | FF2E97 | 22D3EE | FF2E97 |
| Vapourwave | 2B1B4D | 7C4DBE | 3C2569 | 553383 | EAD9FF | 5B3A8C | 00F0FF | FF71CE | FF71CE |
| Hazard | 141414 | 4A4A00 | 1F1F1F | 2E2E00 | FFE81A | 3D3D0A | FFE81A | FF6B00 | FFE81A |
| Blueprint | 0B3D91 | 3D6FC4 | 11499E | 1A56AE | DCE9FF | 2E5FB0 | FFFFFF | 7FD4FF | 7FD4FF |
| Forest Floor | 14200F | 3E5B33 | 1D2E17 | 2C4423 | E8F3DF | 3A5230 | 9CCC65 | D4A017 | 9CCC65 |
| Rose Gold | 2A1A1E | 7A4A55 | 3A252B | 4E3038 | FFE4E8 | 5C3B44 | E8A0A8 | D98C6A | E8A0A8 |
| Ink and Paper | FFFFFF | 000000 | FFFFFF | F0F0F0 | 000000 | BBBBBB | 000000 | 6E6E6E | 000000 |

Slate Dark and Slate Light use rounding 0.10 for both ratios. Cloud Tap and Moon Tap set
suggestion DDE0E5 / 171717, status bar button FFFFFF / 1C1C1E, key rounding 0.18186983,
chrome rounding 0.35, key height 1.2588017, number row 0.971126, key width 0.94148767, row
gap 1.05, ortholinear, suggestions height 0.9, variations height 0.88, and keep their
software geometry. Classic Cloud and Classic Midnight set suggestion CCD2DC / 202124, status
bar button AFB6C2 / 2C2C2E, key rounding 0.118, chrome rounding 0.09, the same key geometry,
the "classic" popup style, and keep their geometry. All other presets derive suggestion from
normal key, status bar button from special key, cursor swipe and popup selected from accent,
key popup from special key, and use 0.08 rounding. The presets' locked LED colors are all
distinct from each other (a test asserts it).

User-saved themes live in `keyboard_theme_saved_themes` (JSON array of name plus theme,
names compared case-insensitively on delete), drafts in `keyboard_theme_drafts` (name, theme,
and the set of populated field names).

### 9.4 The Status Bar Theme page

One page under Keyboard, titled "Status Bar Theme" (description "Colours, LEDs, and which
buttons sit on the bar"), reachable by search under both "Status Bar Theme" and "Status Bar".
Top to bottom:

1. **"Choose a preset"**: a horizontal 104 dp row of cards, one per preset, then one per
   user-saved theme, then one per draft; the active one is marked "Active". Tapping a preset
   applies it to the target of the preview page currently shown (hardware for page 0,
   software for page 1); a preset applied to the software target gets the software geometry
   unless it keeps its own. Tapping a draft opens it for editing.
2. **"Keyboard UI Preview"**: a two-page pager. Page 0 is the hardware preview: the
   suggestion row with the words "Werk", "Team", "Park", the Shift chip (8.3), and the LED
   row if the theme shows LEDs. Its height is 36 dp times the theme's suggestions scale
   (clamped 0.65..2.2) plus 12 dp, minus 6.5 dp when LEDs are off, in a viewport of
   36 times 1.6 plus 12 (minus 6.5). Page 1 is the on-screen keyboard preview with a "Preview
   max viewport" slider (1.0 to 1.8, stored in `keyboard_theme_preview_viewport_scale`,
   default 1.0) that appears only on that page. The preview shows the draft while one is
   being edited.
3. **"Customize colors"**: with no draft open, one button, "Create a custom theme", which
   asks for a name (rejecting one already used by a saved theme or draft) and creates a draft
   as a copy of the active theme. With a draft open: the fourteen color rows (Background,
   Dividers, Normal keys, Special keys, Text and icons, Accent, Suggestions, Status bar
   buttons, Cursor swipe, Key popup, Selected popup key, LED inactive, LED active, LED
   locked), each opening the color dialog, a count of required values still missing, and
   "Save and use theme", enabled only when every required value is set; it saves the theme
   under the draft's name, deletes the draft, and makes it the active theme for the current
   target. The New / Import / Duplicate / Export / Delete actions and the geometry sliders
   exist in the app but have no entry point on the page.
4. **"Status Bar"** section, which is the button and visibility half (6.3, 3.5, section 2):
   "Buttons" with "Left buttons" and "Right buttons"; "Show status bar" with the three chips
   and, in "Only in these apps" mode, the explanation, the installed app rows and "Add an
   app"; "Bar height" with the four chips (36 marked in its description as below Android's
   minimum touch target, for people who type on the keys and only read the bar); "Modifiers"
   with the "Cursor modifiers" switch, its overlay-permission prompt and button (polled every
   1500 ms so the row updates when the user comes back from system settings), the armed and
   locked color rows when it is on, then "Show LEDs" (a switch bound to the active theme's
   `show_leds`, with a reset-to-preset affordance), then "Reset", which restores the slot
   defaults only.

### 9.5 The color dialog

Shared by everything that picks a color. A title, an optional subtitle ("Pick a colour, or
dial one in on the wheel."), quick-pick swatches in rows of five 40 dp circles (the union of
every preset's thirteen colors, de-duplicated, for theme colors), a square hue and saturation
wheel with a brightness slider, an optional warning line, OK and Cancel. The Titan is a short
screen, so the content is bounded to the window height minus 220 dp (never under 200 dp) and
scrolls, and the wheel is capped at the smaller of 220 dp and 30 percent of the window height;
before that the brightness slider was cut off at the bottom (changelog 2.0.0). The draft
editor's color dialog additionally offers a hex field ("Hex color", six digits with or
without a leading "#", partial or invalid values rejected) with "Apply".

## 10. The smart-backlight nudge in the strip

When the smart backlight is enabled but has never been applied (`smart_backlight_enabled`
true and `smart_backlight_applied` false), the strip shows, right-anchored just inside the
right buttons (their inset plus 4 dp), an amber (0xFFFFB300) pill with 8 dp corners, padding
9/3/6/3 dp, the text "backlight paused, tap to fix" (a lightning glyph in front, a long dash between the two halves) in 11 sp black monospace on one line,
and a "✕" in 12 sp. Tapping the pill opens the Smart Backlight settings; tapping "✕"
collapses it. The pill collapses on its own to a 9 dp amber dot after 4000 ms, or on the first
refresh that arrives at least 350 ms after it appeared (typically the first keypress). The
dot opens the same settings. Both disappear as soon as the backlight is applied. Neither
resizes or moves the row.

## 11. Insets: what the app is told

In candidates-only mode (the strip alone, no fullscreen), PhysiBoard rewrites the insets it
reports after Android computes them: the content inset is set equal to the visible inset, so
the app is told to make room for exactly the strip's visible band and nothing more; and the
touchable area becomes a rectangle from the content top down to the bottom of the window and
across its full width, so only touches inside the strip's band reach PhysiBoard and touches
above it fall through to the app. If the window has no width yet or its height is not greater
than the content top, the touchable area falls back to "content". With the input view (the
soft keyboard) showing, Android's own insets are left untouched. The window is never in
fullscreen mode.

A debug switch, `ime_overlay_debug_logging` (default false), logs the navigation, cutout and
applied bottom padding once per distinct combination.

## 12. The per-app dip ("Text box under the bar")

### 12.1 The fault

Some apps (Microsoft Teams first) place their compose box themselves from the keyboard inset
and only move it when the keyboard animates in or out, or when their window regains focus.
On a phone with a hardware keyboard the strip is usually already on screen when the user
taps such a box: the app resets its layout, asks for the keyboard, PhysiBoard declines
because there is nothing more to show, no inset changes, and the box stays under the strip
until something else changes window focus. That is why it looked intermittent.

### 12.2 The fix

For apps on the list `app_keyboard_nudge_packages` (seeded on first read with Teams,
`com.microsoft.teams`), whenever the app's show request is refused (a refusal is the normal
answer in hardware mode) and the request is not a configuration change:

1. If a dip is already in flight, or the app is not listed, or the strip is not actually
   rendered on screen (attached, visible, non-zero size, non-empty visible rectangle), or
   fewer than 1500 ms have passed since the last dip started: nothing.
2. Otherwise the strip is hidden immediately, and a hold begins.
3. 200 ms later the hold ends and the strip is shown again (with the container re-sync one
   turn later).
4. 300 ms after that the dip is over.

The 200 ms exist because a re-show in the same frame is folded into the hide by the window
manager and the app sees nothing: measured on a Titan 2, a re-show 21 ms after the hide left
Teams' inset untouched (D5). The 1500 ms cool-down exists because requests arrive in pairs
(the app's own and the system's on attach) and some apps ask again on every focus change; one
blink per tap is the target.

While the hold is on (the first 200 ms), every other request to show the candidates view is
refused, including PhysiBoard's own re-show from the evaluation pass that every show request
triggers, so that the hide reaches the app as its own event. While the dip is in flight (the
full 500 ms), the window-hidden handling (section 13) is skipped entirely: the field, the
modifiers and the suggestion context survive the blink. The dip is a visible blink, so the
settings text tells the user to leave other apps off unless they see the same fault there.

The list is edited on "Text box under the bar" under Keyboard (search keywords: teams,
hidden, covered, text box, compose field, under bar, inset, blink), the same per-app switch
list used by Exact typing.

## 13. Window hidden and window shown

When Android hides the window and no dip is in flight: the screen trackpad is deactivated,
the auto-detector is told the window went away, the render cache is invalidated, suggestion
action mode is reset, any multi-tap cycle is finalized, modifier state is reset (nav mode
preserved), and the suggestion context is reset. When the window is shown again the strip is
refreshed immediately.

When a field finishes (independent of the window): action mode is reset, any pending surface
transition is cancelled, modifiers are reset (nav mode preserved), nav mode is re-entered if
it was on before the field, and the system status-bar icon is hidden unless nav mode is on.

## 14. Timings and limits

| Value | Number |
|---|---|
| Dip: hold before re-show | 200 ms |
| Dip: settle after re-show | 300 ms |
| Dip: cool-down between dips | 1500 ms |
| Surface transition retry interval, attempts | 250 ms, 6 |
| Slot flash | 160 ms |
| Clipboard badge flash | 350 ms, alpha 0 to 0.4 to 0 |
| Language button tap debounce, disabled period | 500 ms, 300 ms |
| LED color animation | 200 ms |
| Sym surface slide in, slide out | 125 ms, 100 ms |
| Generic fade in, fade out (unused by the live strip) | 75 ms, 50 ms |
| Backlight pill auto-collapse, minimum show before keypress collapse | 4000 ms, 350 ms |
| Suggestions accessibility announcement delay | 500 ms default |
| Overlay permission poll on the settings page | 1500 ms |
| Tap haptic fixed duration | 25 ms default |
| Bar heights | 36, 48, 56, 64 dp; default 56 |
| Theme bar height scale range | 0.65 to 2.2 (36 dp base, 23 to 79 dp) |
| Slot text size clamp | 12 to 20 sp max, 7 to 12 sp min |
| Maximum suggestions in the row | 3 |
| Expansion suggestions kept | 3 |
| Hamburger overlay buttons | 1 close + 9 |
| Minimum button size | 24 dp |
| Seeded status bar apps | 20 |

## 15. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `status_bar_visibility` | string: `ALWAYS`, `NEVER`, `APPS` | absent (read as `ALWAYS` unless `show_status_bar` is false) | Whether the strip has any footprint | Status Bar Theme > Show status bar | Always / Never / Only in these apps |
| `show_status_bar` | boolean | true | Legacy mirror of the mode; false means `NEVER` when the mode is absent | none (written with the mode) | |
| `status_bar_apps` | string set | seeded with 20 packages on first read | Packages the strip shows in when the mode is `APPS` | Status Bar Theme > Show status bar | Add an app / Remove |
| `status_bar_height_dp` | int | 56 | Height of the hardware strip | Status Bar Theme > Bar height | 36, 48, 56, 64 |
| `status_bar_slots_left` | JSON array string | `["hamburger"]` | Left buttons in order | Status Bar Theme > Buttons | Left buttons |
| `status_bar_slots_right` | JSON array string | `["emoji","microphone"]` | Right buttons in order | Status Bar Theme > Buttons | Right buttons |
| `status_bar_slot_left`, `status_bar_slot_right_1`, `status_bar_slot_right_2` | string | `hamburger`, `emoji`, `microphone` | Legacy mirrors; fallback when the JSON is absent | none | |
| `app_keyboard_nudge_packages` | string set | seeded with `com.microsoft.teams` | Apps that get the dip | Keyboard > Text box under the bar | per-app switches |
| `titan2_elite_rounded_corner_insets` | boolean | true on a Titan 2 Elite, false elsewhere | Rounded bar corners, edge buttons, side insets | none (no UI row) | |
| `keyboard_theme_hardware` | JSON object string | absent (Slate Dark, scale 1.4) | The hardware strip's theme | Status Bar Theme | preset cards |
| `keyboard_theme_software` | JSON object string | absent (Slate Dark with software geometry) | The theme above the on-screen keyboard | Status Bar Theme (page 1) | preset cards |
| `keyboard_theme_layout_overrides_hardware`, `..._software` | JSON array string | absent | Per-locale or per-layout theme overrides | no entry point since 2.0 | Layout overrides |
| `keyboard_theme_saved_themes` | JSON array string | absent | User-saved themes | Status Bar Theme | saved cards |
| `keyboard_theme_drafts` | JSON array string | absent | Unfinished custom themes | Status Bar Theme | draft cards |
| `keyboard_theme_preview_viewport_scale` | float | 1.0 (clamped 1.0..1.8) | Height of the on-screen preview | Status Bar Theme (page 1) | Preview max viewport |
| `hardware_bar_height_migrated` | boolean | false | One-time lift of stored scale 1.0 to 1.4 | none | |
| `keyboard_theme_assignment_mode_hardware`, `..._software`, `keyboard_theme_light_*`, `keyboard_theme_dark_*` | string | ignored since 2.0 | nothing | none | |
| `modifier_indicator_mode` | string | unread | nothing (a change only refreshes the strip) | none | |
| `pastierina_mode_override` | string | dropped in 2.0 | nothing | none | |
| `caret_modifier_badge` | boolean | true | The caret badge (its own document) | Status Bar Theme > Modifiers | Cursor modifiers |
| `caret_badge_armed_color`, `caret_badge_locked_color` | int ARGB | 0xFF2563EB, 0xFFDC2626 | Badge colors | Status Bar Theme > Modifiers | armed / locked color rows |
| `suggestions_enabled` | boolean | true | Whether the slots are drawn | Typing settings (other document) | Suggestions |
| `auto_show_keyboard` | boolean | true | Ask for the window when a field starts | Keyboard settings | Show keyboard automatically |
| `accessibility_live_announcements_enabled` | boolean | false | Suggestion announcements | Accessibility | |
| `accessibility_suggestions_announcement_delay_ms` | long | 500 | Delay before re-exposing and announcing slots | Accessibility | |
| `accessibility_read_second_row_enabled` | boolean | false | Exposes the hidden legacy row to TalkBack | Accessibility | |
| `tap_haptic_use_system`, `tap_haptic_duration_ms` | boolean, long | true, 25 | Slot tap haptic | Haptics | |
| `smart_backlight_enabled`, `smart_backlight_applied` | boolean | false, false | The backlight-paused pill | Smart Backlight (other document) | |
| `ime_overlay_debug_logging` | boolean | false | Inset logging | Diagnostics | |

## 16. Titan 2 Elite facts

| # | Fact | Evidence |
|---|---|---|
| D1 | Display 1080 by 1200 at density 300, about 574 by 640 dp; wide-short, near-square; 1 dp = 1.875 px. | docs/titan2elite device profile; the color dialog comment about the short screen |
| D2 | The Titan 2 Elite is 75.0 mm wide, narrower than every Titan but the Slim; the strip's button sizing is for this width. | docs keyboard phone widths table |
| D3 | The display has rounded bottom corners; when Android reports no radius the bar assumes 24 dp. The rounded-corner treatment defaults on only when the device profile is a Titan 2 Elite QWERTY. | source default tied to device detection; first-run baseline writes it true |
| D4 | At 300 dpi the original 32 to 36 dp strip was under Android's 48 dp touch minimum; the default became 56 dp (a text box's height) and the hardware theme scale 1.4 (about 50 dp) so buttons clear 48 dp. | changelog 1.2.2; theme model comment; changelog 2.0.x "bar height setting is back" |
| D5 | On a Titan 2, re-showing the strip 21 ms after hiding it left Teams' inset untouched; 200 ms works. | source comment on the dip; commit "dip the bar for apps that leave their text box under it"; changelog 2.0.7 |
| D6 | With Niagara Launcher on the Titan (2026-08-31) the strip floats over the home screen: window state always-hidden, no explicit request, input view not shown, candidates visible; the field's input type is 0xb1. | maintainer memory note; confirmed on device |
| D7 | The system shows PhysiBoard's candidates path (not the soft keyboard) because "show virtual keyboard with hardware keyboard" is off; the AVD profile reproduces it with that setting at 0, where the strip does not draw but the window state is reported. | maintainer memory note on AVD candidates-only QA |
| D8 | The settings page's color dialog was taller than the display on the Titan before its bounds were derived from the window height. | changelog 2.0.0 "the colour wheel fits the screen" |
| D9 | The maintainer's own phone runs "Only in these apps" with a hand-picked list; the shipped first-run default is "Always" so a new user sees the strip work before restricting it. | first-run baseline comment (re-captured 2026-08-27, app 1.2.3) |

## 17. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| Strip hidden by mode, user presses Sym | The Sym page opens on the (otherwise collapsed) strip and closes again; the strip collapses after. | The page is content the user asked for (changelog 1.0.6). |
| Mode `APPS`, no field focused | Hidden: a null package never matches. | Package membership test. |
| `status_bar_visibility` absent, `show_status_bar` false | `NEVER`. | Explicit legacy off is honored; absent legacy is on. |
| Fresh install versus Reset | Fresh: clipboard left, microphone right. Reset: hamburger left, emoji and microphone right. | Baseline and preference default disagree (6.3). |
| Second right slot | Rendered on the bar, invisible on the page; cleared when its button is chosen for a visible slot. | Page limited to one dropdown per side, lists preserved (6.3). |
| Unknown button id stored (older or newer build) | Read as `none`; nothing drawn in that position. | Normalization on read and write. |
| Hamburger overlay open, any key pressed | The overlay closes. | In hardware mode every refresh hides it (6.4). |
| Dictation active, strip refreshes (a word arrives) | The red recording background is overwritten by the theme button drawable on that refresh and later level updates recolor a drawable no longer on screen; the red indicator is therefore likely lost after the first refresh during dictation, and certainly for an edge-slot microphone. Needs device confirmation. | Buttons are re-themed on every re-attach; the recording drawable is separate from it. |
| Dictation ends | Microphone background is the unthemed dark gray until the next refresh. | Stop restores the default drawable, not the theme. |
| Language tapped twice within 500 ms | Second tap ignored. | Debounce. |
| No subtype | Language button reads "??". | No locale to abbreviate. |
| No dictionary for the current language | Slots removed, buttons remain. | Dictionary check per refresh, unless expansion suggestions are active. |
| Add-word candidate equals a suggestion | Not shown as add-word; the third suggestion takes the left slot. | Case-insensitive duplicate check. |
| Long press on an empty slot | Nothing. | Empty slots are not clickable. |
| Long press on an expansion suggestion | Nothing beyond the tap path. | Expansion slots have no action mode. |
| Suggestions off, buttons configured | Row shows buttons only, full height. | The frame is shown whenever buttons are shown, which is always in the live strip. |
| Nav mode latched | Strip invisible, window stays; the system status bar shows the nav-mode icon. | Root hidden on refresh. |
| Dip in flight and the app hides the window for real | The real hide is ignored for up to 500 ms: modifiers and suggestion context are not reset. | The in-flight flag cannot tell the two apart. |
| Dip requested while the strip is collapsed (mode says hidden) | No dip. | "Actually rendered" is false at zero height. |
| App on the dip list asks twice within 1500 ms | One dip. | Cool-down. |
| Configuration change triggers a refused show | No dip. | Configuration changes are excluded. |
| Theme changed while the strip is up | Buttons, slots, LEDs, chips recolor on the next refresh; the slots are rebuilt. | Theme change clears the slot cache. |
| Bar height changed while the strip is up | The row re-measures live. | Preference listener; height change clears the slot cache. |
| `titan2_elite_rounded_corner_insets` changed | Window insets are re-requested; corners and edge buttons update on the next slot rebuild. | Preference listener. |
| Height 36 chosen | Buttons are 32 dp (36 minus 4, above the 24 dp floor) and the bar is under Android's touch minimum; the setting says so. | Deliberate option for people who only read the bar. |
| Theme scale under 1.0 in hardware mode | Text can be as small as 7 to 12 sp while the bar stays 56 dp. | Scale drives text, not height, on the hardware strip. |
| Modifier held (Shift down) | Nothing changes in the strip; the system status bar icon and the caret badge report it; the LED does not (only one-shot or lock). | Section 8. |
| Rotation or locale change | Only a locale change re-registers subtypes after 500 ms; the field session is kept. | Configuration handling. |
| Launcher home screen with a hidden filter field | Strip floats over the home screen. | Section 3.6, D6. |
| Two slots show the same text | Cannot happen from the engine; the add-word duplicate check prevents the only case. | |
| Stored theme JSON malformed | Defaults, logged; overrides array malformed: no overrides. | Per-field fallback. |
| Hardware theme saved with scale 1.0 before 1.2.2 | Lifted to 1.4 once; a saved theme with any other value is untouched. | Migration flag. |
| Backlight pill shown, user types within 350 ms | Pill stays until 350 ms have passed and the next refresh, or 4000 ms. | Minimum show. |

## 18. Test cases

Each row is a pure decision that a JVM test can encode without a device.

| # | Input | Expected |
|---|---|---|
| T1 | Mode `ALWAYS`, apps empty, package "any" or null | shown |
| T2 | Mode `NEVER`, apps {"a"}, package "a" | hidden |
| T3 | Mode `APPS`, apps {"com.google.android.apps.messaging"}, package that | shown |
| T4 | Mode `APPS`, same apps, package "com.android.launcher3" or null | hidden |
| T5 | Preferences cleared, read the app list | contains "com.whatsapp" and "com.google.android.gm"; removing WhatsApp then reading again omits it |
| T6 | Only `show_status_bar`=true stored | mode `ALWAYS`; only false stored: `NEVER`; nothing stored: `ALWAYS` |
| T7 | Set mode `APPS`, add "a", check "a"; remove "a", check "a"; set boolean off | shown; hidden; mode `NEVER` |
| T8 | Dip: listed app, strip shown, t=0 refused | events [hide]; at t=200 [hide, show] |
| T9 | Dip: hold flag | true from the refusal until t=200 exclusive, false at t=200 |
| T10 | Dip: in-flight flag | true from refusal, still true at t=200, false at t=500 |
| T11 | Dip: unlisted app, or null package | false, no events |
| T12 | Dip: strip not rendered | false, no events |
| T13 | Dip: second refusal at t=80, third at t=500 | both false; events stay [hide, show] |
| T14 | Dip: second refusal at t=1500 | true; events [hide, show, hide, show] after another 200 ms |
| T15 | Insets, candidates-only, content top 122, visible top 0, width 1080, height 229 | content top 0; touchable = region; rectangle (0, 0, 1080, 229) |
| T16 | Insets, not candidates-only, content top 122 | content top 122; touchable mode unchanged |
| T17 | Touchable bounds with width 0, or height not greater than content top | none (fallback to content) |
| T18 | Suggestions ["a","b","c"], no add-word | slots [c, a, b] |
| T19 | Suggestions ["a"], add-word "z" | slots [z, a, empty] |
| T20 | Suggestions ["a","B"], add-word "b" | slots [empty, a, B] (duplicate ignored case-insensitively) |
| T21 | Suggestions [], add-word "z" | one full-width slot "z" |
| T22 | Flash index 0, 1, 2, 5 | slot center, right, left, nothing |
| T23 | Trackpad third 0, 1, 2 | suggestion index 2, 0, 1 |
| T24 | Text size at scale 1.4 | max 19 sp, min 9 sp; at scale 0.65: max 12, min 7; at 2.2: max 20, min 12 |
| T25 | Bar 56 dp at 300 dpi | 105 px; button 98 px; edge 142 px; gap 5 px; edge extra inset 44 px; left inset with one edge button 147 px; right inset with two buttons 250 px |
| T26 | Slots JSON `["clipboard","bogus","none"]` | read as [clipboard, none, none]; the mirrors written as clipboard, none, none on write |
| T27 | Slots JSON unparsable, mirrors `status_bar_slot_right_1`=microphone, `_2`=emoji | right list [microphone, emoji] |
| T28 | Choose "microphone" for R1 while list is [emoji, microphone] | list [microphone, none] |
| T29 | Reset slots | left [hamburger], right [emoji, microphone]; mirrors hamburger, emoji, microphone |
| T30 | Migration input `pastierina_status_bar_slots_left`=`["settings"]` | `status_bar_slots_left`=`["settings"]`, old key gone; `status_bar_apps` string set survives intact |
| T31 | Hardware theme JSON with `suggestions_height_scale` 1.0, migration flag unset | scale becomes 1.4 and the flag is set; a JSON with 1.2 is untouched; a second run changes nothing |
| T32 | Override with locale "en_US" stored, lookup with "en-US" | override matches |
| T33 | Overrides: one for locale only, one for layout only, one for both; lookup matching all | the both-fields override wins |
| T34 | Stored assignment mode "follow_system" with dark slot set | the chosen theme is returned, not the slot |
| T35 | Malformed override JSON | empty override list, chosen theme returned |
| T36 | Locale "en_US", "de", "" for the language button | "EN", "DE", "??" |
| T37 | Microphone level -10, -5, 0 dB | red (128,0,0), (159,12,12), (255,50,50) (intensity 0, 0.25, 1) |
| T38 | Backlight paused at t=0; refresh at t=100; refresh at t=400 | pill still shown after t=100; dot after t=400; with no refresh, dot at t=4000 |
| T39 | Evaluate returns "not shown" then "shown" before the first post runs | candidates never shown (stale post ignored) |
| T40 | Surface transition to candidates requested, surface not rendered after 6 retries of 250 ms | abandoned; requested-shown state set to what is actually rendered |
| T41 | All 24 presets | locked LED colors pairwise distinct |
| T42 | Hex "#1A2B3C", "1a2b3c", "12345", "GGGGGG" | parsed, parsed, rejected, rejected |

## 19. Keep / Drop for 3.0

3.0 is Titan-only and has no on-screen keyboard.

| Item | Verdict | Reason |
|---|---|---|
| The strip on the candidates surface, one instance | keep | It is the only touch surface; the second (input view) strip and the collapse/restore dance exist only for the soft keyboard. |
| Three suggestion slots, add-word slot, action mode, flash | keep | Core of the typing loop. |
| Left and right button lists | keep | Cheap and used; simplify to one editable list per side that the page shows in full (drop the hidden second slot and the three mirror keys). |
| Button catalog | keep clipboard, microphone, emoji, symbols, language, settings, undo, redo | Drop `software_keyboard_mode` (no soft keyboard). Undecided on `hamburger`: it closes on every refresh in hardware mode, so it is barely usable today; keep only if fixed. |
| Show status bar: Always / Never / Only in these apps, seeded list | keep | The shipped answer to launchers and the maintainer's own configuration. |
| Bar height 36/48/56/64, default 56 | keep | D4. |
| Rounded bottom corners and edge buttons | keep | Titan-specific; default on. Drop the toggle key and make it unconditional, or keep as a hidden preference. |
| The per-app dip with 200/300/1500 ms | keep | D5; the only fix for Teams. |
| Insets policy (content = visible, touchable = strip band) | keep | Required so the strip never steals taps and the app resizes exactly. |
| Window-hidden skip during the dip | keep | Part of the dip. |
| LED row with Show LEDs | undecided | Off by default since the caret badge; costs 6.5 dp; the maintainer kept it as an option. |
| Modifier chips in the strip | drop | Disabled live since "keep modifier state out of the keyboard strip"; only the preview draws them. |
| `modifier_indicator_mode`, legacy modifier container, fade animator | drop | Dead. |
| System status-bar modifier icon (27 combinations plus Sym and nav) | keep | The always-on modifier home in hardware mode; belongs with the keys document but the strip must not duplicate it. |
| Theme: colors, key and chrome rounding, LED colors, show LEDs, suggestions scale for text size | keep | Reduce to the fields the strip uses; drop the soft-keyboard geometry fields and the software target. |
| Theme: 24 presets | keep | Data; cheap. Drop the "keeps software geometry" and popup-style columns. |
| Theme: per-input-style overrides, light/dark slots, assignment mode | drop | No entry point since 2.0; assignment mode already ignored. |
| Theme: drafts and the wizard fields (populated-field tracking) | undecided | Only "create a custom theme" and the color rows are reachable; a simpler "duplicate and edit colors" model would do. |
| Theme: import/export strings, duplicate, delete | undecided | Exist without UI. |
| Suggestions height scale as bar height | drop | Only sized the bar above the soft keyboard; keep the scale only if text size should remain adjustable, otherwise fix text at the 1.4 numbers (19/9 sp, 4.2 dp padding). |
| Hardware bar height migration flag | drop | Migration from pre-1.2.2 hardware themes; 3.0 starts clean. |
| Legacy `show_status_bar` boolean and the three slot mirror keys | drop | Backup compatibility only; the settings catalog decides the import story. |
| The 2.0 renames from `pastierina_*` | drop | Already migrated. |
| Backlight-paused pill and dot | keep | Titan-specific nudge for the smart backlight; the numbers are settled. |
| Accessibility announcement throttling | keep | Small. |
| Preview page with "Werk", "Team", "Park" and the Shift chip | keep the hardware preview, drop the virtual page and the viewport slider | No soft keyboard. |
| Color dialog bounds for the short screen | keep | D8. |
| Launcher float | undecided | Documented, not fixed; a fix must gate on "the app requested input" without losing the strip for Tab-focused fields. |
| Hamburger overlay auto-close on refresh in hardware mode | fix or drop with the button | Current behavior is an accident of the software-mode check. |
| Microphone red background being overwritten on refresh | fix | Needs device confirmation first (17). |
| Titan 2 (non-Elite) ortholinear Sym grid flag `titan2_layout_enabled` | out of scope here | Layers document. |

## 20. Provenance

- app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/CandidatesBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/KeyboardVisibilityController.kt
- app/src/main/java/brobata/physiboard/inputmethod/ImeInsetsPolicy.kt
- app/src/main/java/brobata/physiboard/inputmethod/KeyboardInsetsNudge.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarAnimator.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonConfig.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonHost.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonRegistry.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonStyles.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/ClipboardButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/EmojiButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/HamburgerButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/LanguageButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/MicrophoneButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/SettingsButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/SoftwareKeyboardModeButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/StatusBarButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/SymbolsButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/UndoButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/suggestions/ui/FullSuggestionsBar.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/HamburgerMenuView.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/KeyboardThemeColors.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/LedStatusView.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/ModifierIndicatorView.kt
- app/src/main/java/brobata/physiboard/inputmethod/CommandMaterialIcons.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (candidates view, insets, window callbacks, status snapshot, button handlers, preference listener, system status icon)
- app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt (device detection only)
- app/src/main/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtils.kt (language code only)
- app/src/main/java/brobata/physiboard/SettingsManager.kt (status bar, nudge, theme, slot, caret badge, haptic and accessibility sections)
- app/src/main/java/brobata/physiboard/SettingsMigration.kt
- app/src/main/java/brobata/physiboard/SettingsScreen.kt and SettingsCatalog.kt (hosting and search entries)
- app/src/main/java/brobata/physiboard/StatusBarButtonsScreen.kt
- app/src/main/java/brobata/physiboard/AppKeyboardNudgeScreen.kt
- app/src/main/java/brobata/physiboard/KeyboardThemeSettingsScreen.kt
- app/src/main/java/brobata/physiboard/KeyboardThemeModel.kt
- app/src/main/java/brobata/physiboard/KeyboardThemePreview.kt
- app/src/main/java/brobata/physiboard/ColorPickerDialog.kt
- app/src/main/java/brobata/physiboard/inputmethod/NotificationHelper.kt (haptic helpers)
- app/src/main/res/values/strings.xml (status bar, theme, nudge strings)
- app/src/test/java/brobata/physiboard/StatusBarVisibilityTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/KeyboardInsetsNudgeTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/ImeInsetsPolicyTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/KeyboardVisibilityControllerTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/CandidatesBarControllerTest.kt
- app/src/test/java/brobata/physiboard/KeyboardThemeModelTest.kt
- app/src/test/java/brobata/physiboard/KeyboardThemeColorTest.kt
- app/src/test/java/brobata/physiboard/SettingsManagerKeyboardThemeAssignmentTest.kt
- docs/keyboard-phone-widths.md
- docs/titan2elite/DEVICE.md
- docs/spec/README.md
- PHYSIBOARD_CHANGES.md (entries 2.0.7, 2.0.5, 2.0.x bar height, 2.0.0, 1.2.4, 1.2.2, 1.0.6, 1.0.3, 1.0.0)
- git log messages: 45ac5fc, d476cdf, 7878d13, 7a416e3, 8fad010
- maintainer memory notes: status bar floats over launchers, AVD candidates-only QA
