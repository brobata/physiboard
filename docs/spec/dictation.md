# Dictation: speech sessions, engines, silence handling, cues, permissions, assistant

This document describes voice dictation in PhysiBoard 2.x on the Titan 2 Elite: how a session
starts and stops, which speech engine transcribes it, how the keyboard keeps a session alive
across the engine's own short timeouts, how provisional and final words land in the text field,
the haptic cues, the microphone permission flow, the Voice settings screen, and the related
"ask the assistant" triggers (hold Sym, the orange side key, a bindable command). How the Fn
burst and the Sym hold are detected at the key level is specified in the keys document; this
document picks up at the moment a trigger fires.

Everything below is the 2.x behavior on the Titan 2 Elite unless a row says otherwise.

## 1. Vocabulary

- **Session**: one dictation, from the trigger to the stop cue. A session may contain several
  engine requests (see section 6); the user sees one start cue and one stop cue per session.
- **Request**: one "listen" issued to the speech engine. Google's engines end a request on their
  own after about 1 s of silence following speech, and about 2 s after any sound if no words came
  through (D2, D4).
- **Partial** (provisional result): the engine's running hypothesis while the user is still
  talking. Shown in the field as composing text.
- **Final**: the engine's result for a request or a segment. Committed to the field.
- **Segment**: one utterance inside a segmented session (section 6.3).
- **Pause**: the user's end-of-speech silence setting, `dictation_end_silence_ms`, 0 to 10000 ms
  in 500 ms steps. 0 means "system default", which in practice means the engine's own ~1 s.
- **Quiet error**: the engine reporting "no match" (code 7) or "speech timeout" (code 6). Every
  other engine error code is a **real error**. Android's numeric codes used throughout: 1 network
  timeout, 2 network, 3 audio, 4 server, 5 client, 6 speech timeout, 7 no match, 8 recognizer
  busy, 9 insufficient permissions, 10 too many requests, 11 server disconnected, 12 language
  not supported, 13 language unavailable.
- **Heard speech**: the session has received at least one non-empty partial or one final.
- **Continuation**: a new request started right after a final, inside the same session, in
  restart-loop mode (section 6.4).
- **Engine id**: the value of `dictation_engine`: the empty string (system default), the word
  `ondevice`, or a flattened Android component name `package/class` naming one installed
  recognition service.

## 2. Ways a session starts

All triggers call the same "start or stop" action: if a session is currently marked active
(the strip's microphone is lit), the trigger stops it; otherwise it starts one. "Active" is set
when the engine reports it is ready for speech, not at the trigger (see 6.1), so two triggers
inside that window both start (the second one restarts the session state and issues a fresh
request; there is no toggle-off until the engine has said "ready").

### 2.1 Hold Fn

Only while `fn_long_press_speech` is on (default off; the first-run defaults turn it on). The
keys document specifies the burst: five consecutive Fn-origin key repeats (scancode
`fn_speech_scan_code`, default 251, or the Android FUNCTION keycode 119) with no other key in
between and no gap over 200 ms, which on the Titan is about 600 ms of hold (D1). On the fifth
repeat the keyboard clears every Ctrl and Alt state including "physically pressed", refreshes
the status display, and fires start-or-stop. Every Fn-origin event is consumed while the
setting is on, so the hold never leaves Ctrl stuck. This intercept runs before the keyboard
checks whether the current field is editable, but the keyboard only receives key events at all
while an editor is connected (D9), so a hold in the camera or on a page with no text field does
nothing.

### 2.2 Microphone button on the strip

The strip's microphone button (slot value `microphone`; on the first-run defaults it occupies
the first right-hand slot, on the untouched upstream defaults the second) fires start-or-stop
on tap after a keyboard-tap haptic. The same button inside the hamburger menu behaves the same.
Tapping it also releases a latched Shift or Alt variation layer and restores the modifier state
saved before that hold (the variations part of the strip document).

### 2.3 Alt+Ctrl chord

In an editable field, with `alt_ctrl_speech_shortcut` on (default on; the first-run defaults
turn it off): a Ctrl key-down while Alt is physically held and Ctrl is not already pressed, or
an Alt key-down while Ctrl is physically held and Alt is not already pressed, fires
start-or-stop and consumes the key. Latched or one-shot modifiers do not count; both must be
physically down. On the Titan, Ctrl is the vendor's Fn remap, so with `fn_long_press_speech`
off this chord is what an Alt-plus-held-Fn produces once Fn's repeats begin (about 400 ms in).

### 2.4 Dedicated microphone key

Keycode 667 (the Minimal Phone's microphone key) with repeat count 0, in an editable field,
while Alt is neither held, latched nor one-shot, fires start-or-stop. The Titan has no such key.

### 2.5 The assistant triggers are not dictation

Holding Sym, long-pressing the orange side key, and the "Voice assistant" command open the
device's voice assistant already listening. They never start a dictation session and never
insert text; section 11 covers them.

### 2.6 What starting does

1. If microphone permission (`android.permission.RECORD_AUDIO`) is not granted: remember that a
   start is pending and open the permission activity (section 10). Nothing else happens until
   the grant broadcast arrives.
2. Remember the package of the current editor as the session's owner (section 3, "another app").
3. Reset the "last partial" memory.
4. Make sure a recognizer exists for the engine id in `dictation_engine`. If a recognizer exists
   for a different id, it is destroyed and the "engine refuses segmented sessions" latch (6.3)
   is cleared. If the platform reports that speech recognition is unavailable, the start fails
   with the message "Speech recognition not available." which is written to the log only; the
   user sees nothing (edge-case table).
5. Resolve the recognition language (section 5).
6. Reset all session state: active, no stop requested, no continuation, segments 0, heard
   speech false, quiet restarts 0, session start time now. Decide segmented mode (6.3).
7. Issue the first request. A security failure or any other failure at this point marks the
   session inactive and reports "Microphone permission denied." or "Speech recognition error."
   to the log only.

## 3. Ways a session ends

| Cause | What happens | Cue |
|---|---|---|
| Trigger fired again (Fn hold, strip button, chord, mic key) while active | "stop requested": the keyboard's silence timer is cancelled and the engine is asked to stop listening so it can deliver the words already spoken; the final (or a quiet error) then ends the session. In segmented mode a watchdog of pause + 5000 ms is armed in case the engine never answers. | stop cue when the session ends |
| Silence for the configured pause (restart-loop mode) | keyboard timer expires: recognizer cancelled, composing partial cleared, session ends | stop cue |
| Engine ends a segmented session on the pause | session ends; the recognizer is not cancelled | stop cue |
| Quiet error with no text and the first-words grace exhausted (10 s or 5 restarts) | toast "No text recognized. Try again." or "No speech input detected."; session ends | stop cue |
| Real error (codes 1, 2, 3, 4, 5, 8 outside the grace, 9, 10, 11, 12, 13, unknown) | toast per 6.6; composing partial cleared; session ends | stop cue |
| The field rejected an insert (exception while writing) | "Speech recognition error." to the log; recognizer cancelled; session ends | stop cue |
| Editor gone: the app closes its text field and no new field replaces it within 500 ms | recognizer cancelled, partial cleared, session ends | stop cue |
| Another app takes the editor (a new field whose package differs from the session owner's) | immediately as above | stop cue |
| Keyboard service destroyed | timers cancelled, recognizer destroyed, partial cleared; no session-end bookkeeping | none |
| Pause set to 0 and a final arrives (restart-loop mode) | one utterance per session: the final is committed and the session ends | stop cue |

A new field in the **same** app (the app blinking its field off and on, or the user moving to
another field in the same app) does not end the session: the 500 ms grace after the field
closes is cancelled by the new field's arrival, and dictation continues into whatever field is
current when the next words land.

## 4. Engines

### 4.1 Discovery and ranking in the picker

The "Speech engine" picker lists, in this order:

1. **System default** (id empty string). Detail text: "Follows Android's voice input setting,
   which is X today. Pick this to keep matching the rest of the phone." where X is the friendly
   name of the package in `Settings.Secure` key `voice_recognition_service`; when that key is
   empty or unreadable the detail is "Follows Android's voice input setting".
2. **Android offline engine** (id `ondevice`), only on Android 12 or later and only when the
   platform reports an on-device recognizer. Detail: "Android's own built-in recognizer, always
   on the phone. Starts fastest and needs no signal, but knows fewer words and often skips
   punctuation."
3. Every installed service answering the `android.speech.RecognitionService` intent, in the
   order the package manager returns them, each with id `package/class`. The row whose package
   equals the system default's package carries the accent-coloured tag "Currently the system
   default".

Friendly names: package `com.google.android.tts` is shown as "Google"; `com.google.android.as`
as "Android System Intelligence"; anything else by its app label, or its package name if the
label cannot be read. Details: Google: "The recognizer behind Google voice typing. The usual best
all-rounder for accuracy and punctuation; downloads a language pack so it still works offline."
Android System Intelligence: "Google's on-device engine, the one behind Live Caption. Runs on the
phone, so it is quick and needs no signal; weaker on unusual words and names."
`io.homeassistant.companion.android`: "Sends what you say to your Home Assistant server and uses
the speech-to-text set up there. Only works while that server is reachable."
`com.anthropic.claude`: "Transcribes through the Claude app. Needs a connection and your Claude
account." Any other: "A speech engine added by this app. How well it hears you, and whether it
needs a connection, is up to that app."

The picker dialog opens with the text "If dictation cuts off too early, mishears you or won't
punctuate, try a different engine, that is the whole reason to change this. Engines that run on
the phone start faster and work with no signal; ones that use a server usually understand more."
Choosing a row saves it and closes the dialog; the only button is Cancel.

### 4.2 Resolution at session start

| Stored id | Recognizer created |
|---|---|
| empty | the platform's default recognizer (whatever `voice_recognition_service` names) |
| `ondevice` on Android 12+ with an on-device recognizer available | the platform's on-device recognizer |
| `ondevice` otherwise | falls to the "other" row below with a non-empty id that is not a component: the system default |
| `package/class` still installed as a recognition service | that service |
| `package/class` no longer installed | the system default |
| any creation failure | the system default; if that fails too, no recognizer and the start fails per 2.6 step 4 |

The summary row under "Speech engine" shows the picker label for the stored id, or "System
default" when the id no longer matches any row.

### 4.3 What the release install uses

`dictation_engine` is not touched by the first-run defaults, so a fresh install dictates through
the system default. Which service that is on a stock Titan 2 Elite is not recorded in the
source or the docs; it needs device evidence (the value of `voice_recognition_service`).

## 5. The request

Every request is an `android.speech.action.RECOGNIZE_SPEECH` request to the chosen recognizer
with:

- language model: free-form;
- language: the tag from 5.1;
- partial results: requested;
- calling package: `brobata.physiboard`;
- maximum results: 5 (only the first is ever used);
- prompt: "Speak now..." (never displayed by PhysiBoard; some engines show it in their own UI);
- mask offensive words: the value of `dictation_mask_offensive` (the platform's own default is
  masking on, and the Google voice typing setting inside Gboard does not apply here);
- when the pause is greater than 0: both the "complete silence length" and the "possibly complete
  silence length" hints set to the pause in ms. Google's engines treat these as hints and end the
  request after about 1 s anyway (D4); in a segmented session the complete-silence value is what
  actually ends the session;
- on Android 13 or later, when `dictation_auto_punctuation` is on: the "enable formatting" option
  set to optimise quality (the engine punctuates and capitalises);
- on Android 13 or later, in segmented mode: the segmented-session option keyed to the
  complete-silence length.

### 5.1 Language selection

1. Take the current keyboard subtype's locale: its language tag if non-blank, otherwise its
   legacy locale string. Trim it, replace every `_` with `-`, and parse it as a language tag. If
   the result has a language, use its canonical tag.
2. Otherwise use the device's first configured locale as a tag, if non-empty.
3. Otherwise `it-IT` (the upstream project is Italian).

Examples: `fr_FR` gives `fr-FR`; `it_IT` gives `it-IT`; `de_DE` gives `de-DE`; `pt_BR` gives
`pt-BR`; `fr` gives `fr`; `es-ES` gives `es-ES`; `sr_Latn_RS` gives `sr-Latn-RS`; `___` has no
language and falls through; a missing subtype with a British device gives `en-GB`.

## 6. Session lifecycle and timing

### 6.1 Start and the first cue

The trigger issues the first request at once. When the engine reports "ready for speech" the
first time in the session, the session becomes active: the strip's microphone lights up, Alt
and Ctrl modifier state is cleared and the status text refreshed, and the start cue plays. Later
"ready" reports within the same session (continuations, re-listens) do neither; the cue and the
strip state are once per session.

### 6.2 First-words grace: the quiet re-listen

Google's engines close the microphone about 2 s after any sound and report "no match" if no
words came through (D2). A user who presses the trigger, draws breath and then speaks would get
"No text recognized" for the breath. So, while the session has **not yet heard speech**, a quiet
error (7 or 6) or a busy error (8) does not end the session: the keyboard listens again, in the
same mode (segmented or plain), provided all of these hold:

- the session is active and no stop was requested;
- no partial or final has arrived this session;
- less than 10000 ms have elapsed since the session started;
- fewer than 5 re-listens have already been made this session.

A busy error re-listens after a 300 ms delay (a busy engine is usually the previous request
still winding down); the quiet errors re-listen immediately. Once the grace is exhausted, the
next quiet error is reported as in 6.6. Google's engine plays its own short failure sound at
each of these internal "no speech" endings, so during the grace the user may hear up to five
beeps before the keyboard says anything (D7).

### 6.3 Segmented mode (the engine times the pause)

Used for a session when all of: Android 13 or later; `dictation_continuous_session` on
(default on); the engine has not refused segmented sessions since the recognizer was created;
the pause is greater than 0. The request asks the engine to hold one session open, deliver one
final per utterance ("segment"), and end the session itself after the pause of complete silence.

- Each segment result is committed like a final (section 7). The keyboard then arms a
  **segmented watchdog** for pause + 5000 ms: if the engine neither delivers another segment nor
  ends the session in that time, the session is ended here with the recognizer cancelled. The
  watchdog is also armed when the engine reports end of speech, when an ordinary (non-segment)
  final arrives in segmented mode, and on an explicit stop; it is cancelled by beginning of
  speech, by any non-empty partial, by the end of the segmented session, and by the re-listen
  paths.
- When the watchdog fires with **zero** segments seen, the engine is judged not to understand
  the mode: the refusal latch is set (segmented mode is not asked for again until the recognizer
  is rebuilt, which happens when the engine setting changes) and the session ends. With one or
  more segments seen, the session simply ends and the latch is untouched.
- An ordinary final arriving in segmented mode means the engine ignored the request and ran a
  plain one-shot: the text is committed, and the watchdog (armed with zero segments) will close
  the session pause + 5000 ms later and set the latch.
- **Refusal**: an error within 1200 ms of the session start, with zero segments seen, the
  session active and no stop requested, whose code is **5 (client) or any code not in the list
  {1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13}** (that is, 5, 14, 15 and unknown codes) is the engine
  refusing the segmented request. The keyboard sets the latch, drops segmented mode for this
  session, clears any partial, and immediately re-issues the same session as a plain request;
  the first-words grace still applies. Busy, network, audio, permission, language and silence
  errors are never refusals: they would happen to a plain request too.
- A quiet error in segmented mode after at least one segment is the session running out of
  speech: it ends quietly (no toast), and a leftover partial is finished as in 7.3.

### 6.4 Restart-loop mode (the keyboard times the pause)

Used when segmented mode is not (Android 12 or earlier, setting off, latch set, or pause 0).
After each final:

- if the pause is 0, or a stop was requested, the session ends;
- otherwise the keyboard arms its **silence timer** for max(pause − 1000, 400) ms (the engine
  has already waited about 1 s of silence before delivering the final) and starts a new request
  at once (a "continuation"). Beginning of speech or a non-empty partial cancels the timer.
  Expiry cancels the recognizer, clears any composing partial, and ends the session.

A continuation may itself end quietly (7 or 6) before the pause is over. If more than 700 ms
have passed since the continuation started, this is treated as silence, not failure: any partial
is finished (7.3), and if the silence timer is still pending a fresh continuation is started,
otherwise the session ends. A quiet error **within** 700 ms of a continuation is a failure loop:
it falls through to the later rules (a partial on screen is finished, then the session ends
with the toast of 6.6).

### 6.5 Explicit stop

"Stop requested" cancels the silence timer and asks the engine to stop listening, which makes it
deliver whatever it has as a final. That final is committed and, because a stop was requested,
no continuation follows and the session ends. If instead the engine answers with a quiet error
(nothing was said), the session ends silently with no toast and the partial (if any) cleared.

### 6.6 Error handling, in order

For every engine error the rules are tried top to bottom; the first match wins.

| # | Condition | Action |
|---|---|---|
| 1 | segmented refusal (6.3) | latch, retry as plain request, no message |
| 2 | not a continuation, and the quiet re-listen conditions of 6.2 hold (code 7, 6 or 8) | re-listen; 300 ms delay for code 8 |
| 3 | a continuation older than 700 ms ends with 7 or 6 | finish any partial; continue if the silence timer is pending, else end; no message |
| 4 | segmented mode, 7 or 6, at least one segment seen | end session quietly; finish any partial |
| 5 | session active, 7 or 6, a non-blank partial is on screen | finish the utterance from the partial (7.3); if no stop requested and pause > 0, arm the silence timer and continue; else end session |
| 6 | session active, stop requested, 7 or 6 | end session quietly; clear partial |
| 7 | session already ended (a cancelled request reporting the silence that ended it) | ignore; clear partial |
| 8 | anything else | end session; clear partial; toast |

Toast text for rule 8: code 7 "No text recognized. Try again."; code 6 "No speech input
detected."; code 9 "Microphone permission denied."; code 2 "Network error."; every other code
"Speech recognition error." The toast is short (about 2 s). The same message is also handed to
the keyboard service, which only logs it.

## 7. Text insertion

All writes go through the current input connection on the main thread. If there is no input
connection at the moment of a write, the write is silently skipped (nothing lands, no error).

### 7.1 Partials

A non-empty partial is placed in the field as **composing text with the cursor after it**.
Composing text replaces the previous composing region whatever else is asked, so successive
partials overwrite each other in place. The cursor placement matters: placed at the start of the
region, a session that ends without a final (D3) would leave the cursor in front of the dictated
words and the next typed or dictated text would land ahead of them (changelog 2.0.7).

Before it is shown, a partial gets **first-letter capitalisation only**: if auto-capitalisation
is not disabled for this field (raw-mode app; password field; or a restricted field type while
`auto_capitalize_restricted_fields` is off) and `auto_capitalize_first_letter` is on and the
cursor rule of the text-input document says "capitalise here" (cursor at the very start of the
document, right after a newline, or right after `.`, `!` or `?` followed by whitespace), the
first character is title-cased when it is lowercase. No punctuation-word replacement and no
after-sentence capitalisation is applied to partials. Note that from the second partial on the
cursor sits after the previous partial's composing text, so "cursor at start" is no longer true
and the capital survives only because each new partial is checked against the text that now
precedes it (the previous partial's own words); with automatic punctuation on, Google's engine
capitalises the hypothesis itself, which masks this.

Empty partials are ignored. A non-empty partial marks the session as having heard speech and
cancels both the silence timer and the segmented watchdog.

### 7.2 New utterance inside one request

If a partial arrives while a previous partial is composing and the two do not look like the same
utterance, the previous words are committed first and the new partial starts a fresh composing
region after them. "Same utterance" means, after trimming: either string is empty; or one is a
case-insensitive prefix of the other; or their first words (up to the first space) are equal
ignoring case. Otherwise it is a new utterance: the composing text is finished as-is, and if the
character right before the cursor is a letter or digit a single space is committed, then the new
partial is composed. This is what stops a second sentence within one request from overwriting
the first (changelog 1.0.1, "appends after a pause instead of overwriting").

### 7.3 Finals, empty finals, and quiet errors after a partial

A final with text finishes the utterance with that text. A final **without** text (Google's
system engine sends the whole utterance as its last partial and then an empty final, D3) finishes
the utterance from the last partial instead. A quiet error while a partial is on screen (rule 5
of 6.6) also finishes the utterance from the last partial. A final without text and with no
partial remembered clears any composing region and inserts nothing.

"Finishing the utterance" with text T:

1. T is passed through the punctuation-word table (7.4).
2. T is capitalised (7.5).
3. Spacing is applied (7.6) and the result is written: if a partial is composing, the result
   replaces the composing region (set as composing with the cursor after it, then the composing
   state is finished so it becomes ordinary text); otherwise it is committed at the cursor with
   the cursor after it.
4. The last-partial memory is cleared.

### 7.4 Punctuation words

Applied to finals only, case-insensitively, as plain substring replacement (not word matching),
longest pattern first, every occurrence. The patterns and replacements, in the order tried:

| Pattern (spaces significant) | Replacement |
|---|---|
| ` punto interrogativo ` | `? ` |
| ` punto interrogativo` | `? ` |
| ` punto esclamativo ` | `! ` |
| ` punto esclamativo` | `! ` |
| ` punto e virgola ` | `; ` |
| ` punto e virgola` | `; ` |
| ` due punti ` | `: ` |
| ` due punti` | `:` |
| `due punti ` | `: ` |
| ` virgola ` | `, ` |
| ` virgola` | `,` |
| `virgola ` | `, ` |
| ` punto ` | `. ` |
| ` punto` | `.` |
| `punto ` | `. ` |

Only Italian words are handled; there is no English "period", "comma", "question mark". Because
matching is by substring, `appunto ` becomes `ap. ` and `spunto di vista` becomes `s. di vista`
(edge-case table). A lone `punto` with nothing on either side is not replaced. With automatic
punctuation on (the default) Google's engine already emits `.` and `,`, so the table rarely
fires on Google output.

### 7.5 Capitalisation of a final

Skipped entirely (text returned unchanged) when there is no input connection, or when
auto-capitalisation is disabled for this field (same rule as 7.1). Otherwise:

1. **First letter**: if the cursor rule says "capitalise here" and `auto_capitalize_first_letter`
   is on, the first character is title-cased when lowercase.
2. **After sentence end**: if `auto_capitalize_after_period` is on, every occurrence inside the
   text of `.`, `!` or `?` followed by one or more whitespace characters and then an ASCII
   lowercase letter `a` to `z` has that letter upper-cased. Accented lowercase letters are not
   matched.

The cursor rule reads the field with the cursor where it is at that moment. When a partial is
composing, the cursor is after the partial, so the "before" text ends with the partial's own
words (edge-case table).

### 7.6 Spacing of a final

1. Read up to 10 characters before the cursor. If the last of them is a **letter** (digits and
   punctuation do not count), prepend one space.
2. Always append one space.

So dictating "world" after "Hello" gives "Hello world "; after "Hello " gives "Hello world ";
after "5" gives "5world "; after "Hello." gives "Hello.world " (and no capital, since the period
has no whitespace after it). The trailing space is a real committed space, not an auto-space of
the text-input document, so a following punctuation key does not pull it back. While a partial is
composing, the 10 characters read include the partial itself, whose last character is normally a
letter; the source therefore prepends a space to the replacement in that case, giving a leading
space even at the start of an empty field. Whether this is visible on the Titan with Google's
engine has not been confirmed (edge-case table; needs device evidence).

## 8. Cues

### 8.1 Haptic

Cues play only when `dictation_haptics` is on (default on) **and** the system's own haptic
feedback toggle (`Settings.System` key `haptic_feedback_enabled`, read as on when unreadable) is
on. The start cue plays once per session at the first "ready for speech"; the stop cue plays
when the session ends, and only if a start cue was played for it (a stray error callback after
the session ended can never vibrate twice). The service-destroy path plays no stop cue.

The cue is a plain vibration with no audio attributes: notification-class vibration is muted
whenever the phone's notification vibration is off, which silenced the cues entirely in 1.0.4
(D5). Every level runs at the hardware maximum amplitude 255 except Light; the levels differ in
pulse length, which is what reads as "firmer" (D10). `dictation_haptic_strength`, default
`strong`:

| Level | Start cue (two pulses) | Stop cue (one pulse) |
|---|---|---|
| `light` | 35 ms at amplitude 180, 60 ms gap, 35 ms at 180 | 90 ms at 180 |
| `standard` | 60 ms at 255, 70 ms gap, 60 ms at 255 | 160 ms at 255 |
| `strong` | 150 ms at 255, 90 ms gap, 150 ms at 255 | 300 ms at 255 |

Any stored value other than `light` or `standard` plays the strong pattern. Choosing a level on
the Sound & Haptics screen plays that level's **start** cue immediately as a preview. The strip
button's own tap feedback (a keyboard-tap haptic) is separate and follows the system's
touch-feedback setting.

### 8.2 Audio

PhysiBoard plays no sounds. Google's engine plays its own failure sound at each internal
"no speech" ending, including the ones absorbed by the first-words grace (D7). No setting in
PhysiBoard silences it.

## 9. What the strip shows while listening

The microphone button (icon: a white microphone; content description "Voice input"; state
description "Off") changes when the session becomes active:

- state description "On";
- background becomes red, RGB (255, 80, 80), with the pressed state blue RGB (100, 150, 255);
- as the engine reports the input level in dB (typically −10 to 0), the red is redrawn:
  level = clamp((dB + 10) / 10, 0, 1); intensity = level²; colour = RGB(128 + 127·intensity,
  50·intensity, 50·intensity), so silence is a dark red (128, 0, 0) and a loud voice a bright
  (255, 50, 50).

When the session ends the normal button background returns, the level is reset to −10 and the
state description returns to "Off". The hamburger-menu copy of the button mirrors all of this.
There is no text hint: the hook that would replace the swipe hint with a "listening" message is
empty in 2.x. Nothing else on the strip changes, and no notification is posted. If the strip is
hidden (`status_bar_visibility`), there is no visual sign at all; the haptic cue is the only one.

## 10. Microphone permission

`android.permission.RECORD_AUDIO` is declared in the manifest. A keyboard service cannot show the
runtime permission dialog itself, so:

1. When a trigger fires without the permission, the keyboard remembers "start pending" and opens
   a translucent, title-less permission activity (new task, clear top; excluded from recents;
   single task; empty task affinity).
2. That activity, if the permission is already granted, broadcasts
   `brobata.physiboard.PERMISSION_GRANTED` and finishes. Otherwise it shows the system
   permission dialog and, on the answer, broadcasts `brobata.physiboard.PERMISSION_GRANTED` or
   `brobata.physiboard.PERMISSION_DENIED`. Both broadcasts are restricted to the
   `brobata.physiboard` package. The activity then finishes and removes its task from recents.
3. The keyboard, on GRANTED with a start pending, clears the pending flag and fires the start
   again (the field the user was in is still current, so dictation begins there). On DENIED the
   pending flag is cleared and nothing is shown.

Denying permanently means every trigger opens the permission activity, which returns DENIED at
once without a dialog; the user sees nothing happen. There is no in-app explanation screen.

## 11. The voice assistant

### 11.1 Triggers

- **Hold Sym** for 600 ms, with `sym_long_press_assistant` on (default off) and Sym not chosen
  as the screen trackpad trigger (the keys document has the arming and cancelling rules). When
  it fires the assistant is launched; the Sym release is then swallowed so no Sym page opens.
- **Orange side key long press**, with the vendor slot bound to PhysiBoard (11.3).
- **The "Voice assistant" command** (id `pastiera.voice_assistant`, internal action
  `start_voice_assistant`), bindable to any shortcut key or launcher slot.

If no assistant can be started, a short toast says "No voice assistant is set up on this
device." (the Sym hold also clears its "fired" flag so the release toggles the page normally).

### 11.2 Launch

No public request reproduces the system assist gesture (that calls the voice-interaction session
directly, which is closed to apps), and each assistant decides for itself whether a given request
opens it listening or merely opens it. So the launch tries requests in order and the first that
starts wins:

1. Build the ordered list: the action chosen in `assistant_action` first (if not `auto`), then the
   automatic order `android.intent.action.VOICE_COMMAND`,
   `android.speech.action.VOICE_SEARCH_HANDS_FREE`, `android.intent.action.ASSIST`, duplicates
   removed. `voice_command` maps to the first, `hands_free` to the second, `assist` to the third.
2. Find the assistant package: the first non-empty of `Settings.Secure` keys `assistant` and
   `voice_interaction_service`, taken as a flattened component's package, or as a bare package
   name if it does not parse as a component.
3. If a package was found, try each action targeted at that package (new-task flag), stopping at
   the first that resolves and starts. Targeting avoids the system chooser, which several apps
   would otherwise trigger.
4. Otherwise, or if none started, try each action untargeted; this may show the chooser once.
5. If nothing started, report failure (toast above).

The Voice screen's "How the assistant opens" picker offers: Automatic ("Try each in turn,
listening first"), Voice command ("Meant to take a spoken question straight away"), Hands-free
voice ("Listens without needing the screen"), Assist ("The assist gesture's own request; may
just open the app").

### 11.3 The orange side key

The Titan's orange key never reaches an input method: the vendor layer reads a package and
activity out of `Settings.System` and launches it (D6). PhysiBoard therefore redirects the
**long press** slot to its own transparent trampoline activity (exported; excluded from recents;
own empty task affinity; single instance; no history; a theme with a transparent window, no
preview window, no animations, no dim) which launches the assistant per 11.2 and finishes
immediately. Running it in its own task and drawing no preview is what stops PhysiBoard's own
screens flashing on the way through (D12).

Binding writes three `Settings.System` keys: `func1_long_press_package` = `brobata.physiboard`,
`func1_long_press_activity` = the trampoline's class name, `func1_shortcut_key_enable` = `1`
(stock already has it on; set anyway so the binding cannot land inert). Before the first write
the current package and activity are captured once into `side_key_original_package` and
`side_key_original_activity` with `side_key_original_captured` = true, but only if both values
are well-formed: at most 256 characters matching `^[A-Za-z0-9_][A-Za-z0-9_.$]*$`. Any value that
fails that check is neither recorded nor ever written, because the restore path may hand the
value to the ADB broker's shell (`settings put system <key> <value>`).

Writes go directly when the app holds WRITE_SETTINGS, otherwise through the paired
wireless-debugging broker one `settings put system` at a time, otherwise the outcome is
"needs permission": the Voice screen toasts "PhysiBoard needs permission to change system
settings, or a paired wireless-debugging session." and opens the system "modify system settings"
page for the package; when that page returns, the toggle the user asked for is retried once
automatically. A write failure toasts "Could not change the side key setting."

Unbinding restores the captured pair and clears the capture; with nothing captured it is a no-op
success (the slot is left as is rather than guessed); with a malformed capture the capture is
dropped and nothing is written. "Reset device settings to stock" (Advanced) also turns
`side_key_assistant` off and restores the slot. The system-level change survives an uninstall.

The Voice screen's switch does not trust `side_key_assistant`: on opening it reads the two slot
keys and shows "on" only when they point at PhysiBoard, correcting the stored flag if they
differ (a system update or another app can take the slot back silently, D13). The first-run
defaults write `side_key_assistant` = true without binding the slot, so on a fresh install the
flag says on until the Voice screen is first opened, and the key keeps launching Gemini until
the user actually enables the switch there.

## 12. Screens

### 12.1 Voice (Settings > Voice)

Intro text: "Hold the Fn key in any text field to dictate. Speak, then release, your words are
transcribed inline." (the on-screen copy joins the two halves with a dash) (The release does not end the session; the pause or a second trigger does.)

Section **Triggers**: switch "Long-press Fn for speech input", description "Start speech input
by holding the Fn key alone. Short presses and key combos keep their normal behavior. On Titan
devices, set the Fn key to Ctrl in the system Shortcut keys settings so the key reaches the
keyboard."

Section **Transcription**: navigation row "Speech engine" with the current engine's label;
switch "Automatic punctuation" ("Let the speech engine add punctuation and capitalization to
what you dictate (Android 13+)."); switch "Block offensive words" ("Mask profanity in dictation
results (e.g. f***). Turn off to transcribe words exactly as spoken."); slider "End-of-speech
pause" from 0 to 10000 ms with 19 intermediate stops, snapped to 500 ms, subtitle "System
default" at 0 or "X.X s of silence before dictation stops", hint "How long you can pause before
dictation stops. The speech service may not honor this on all devices."; switch "Let the engine
time the pause" ("Asks the speech service to hold one session open and end it on your pause
(Android 13+). Turn off if dictation cuts out early or never stops.").

Section **Voice assistant**: switch "Orange key opens the assistant" (disabled while a bind or
restore is in flight); switch "Hold Sym for the assistant", shown off and disabled while Sym is
the screen trackpad trigger; navigation row "How the assistant opens" with the current choice.

Every row writes its preference immediately. Back returns to the settings hub.

### 12.2 Sound & Haptics

Switch "Vibrate on dictation start/stop" ("Two quick ticks when the microphone starts
listening, one pulse when it stops. Follows the system haptic setting."). Only while it is on,
a row "Vibration strength" with three chips Light, Standard, Strong; tapping a chip saves and
plays that level's start cue.

### 12.3 Elsewhere

Settings search lists every Voice row, and also the two haptic rows, under the "Voice"
category, with the search target being the Voice screen even for the haptic rows (which live on
Sound & Haptics). Onboarding's essentials list shows "Hold Fn to talk (dictation)". The status
bar buttons screen places or removes the `microphone` slot (strip document).

## 13. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `fn_long_press_speech` | boolean | false (first-run defaults: true) | hold-Fn burst starts or stops dictation and Fn-origin events are consumed | Voice > Triggers | Long-press Fn for speech input |
| `fn_speech_scan_code` | int | 251 | scancode treated as the Fn key for the burst | none (preference only) | none |
| `alt_ctrl_speech_shortcut` | boolean | true (first-run defaults: false) | Alt held plus Ctrl, or Ctrl held plus Alt, starts or stops dictation | none (preference only; in the backup contract) | none |
| `dictation_engine` | string | empty | which recognizer: empty = system default, `ondevice`, or `package/class` | Voice > Transcription | Speech engine |
| `dictation_auto_punctuation` | boolean | true | asks the engine to punctuate and capitalise (Android 13+) | Voice > Transcription | Automatic punctuation |
| `dictation_mask_offensive` | boolean | true (first-run defaults: false) | per-request profanity masking | Voice > Transcription | Block offensive words |
| `dictation_end_silence_ms` | int | 0 (first-run defaults: 2000); stored clamped to 0..10000 | the pause: silence hints, the keyboard's silence timer (pause − 1000, min 400), the segmented watchdog (pause + 5000); 0 = one utterance per session | Voice > Transcription | End-of-speech pause |
| `dictation_continuous_session` | boolean | true | ask for a segmented session on Android 13+ | Voice > Transcription | Let the engine time the pause |
| `dictation_haptics` | boolean | true | start and stop vibration cues (also gated by the system haptic toggle) | Sound & Haptics | Vibrate on dictation start/stop |
| `dictation_haptic_strength` | string | `strong` (`light`, `standard`, `strong`) | pulse lengths of the cues | Sound & Haptics (only while cues on) | Vibration strength |
| `sym_long_press_assistant` | boolean | false | 600 ms Sym hold opens the assistant | Voice > Voice assistant | Hold Sym for the assistant |
| `side_key_assistant` | boolean | false (first-run defaults: true, without binding) | records that the orange key's long press should point at PhysiBoard; the screen re-reads the real slot | Voice > Voice assistant | Orange key opens the assistant |
| `assistant_action` | string | `auto` (`voice_command`, `hands_free`, `assist`) | which request is tried first when opening the assistant | Voice > Voice assistant | How the assistant opens |
| `side_key_original_captured` | boolean | false | whether a restore point for the vendor slot exists | none | none |
| `side_key_original_package` | string | none | the vendor slot's package before PhysiBoard bound it | none | none |
| `side_key_original_activity` | string | none | the vendor slot's activity before PhysiBoard bound it | none | none |
| `impact_defaults_applied` | boolean | false | set once after the first-run defaults above are written; they never re-run | none | none |

The first-run defaults are written once at app start when `impact_defaults_applied` is false,
and only for a fresh install (they are the maintainer's dialed-in configuration re-captured on
2026-08-27, D11). Of the dictation keys, only `alt_ctrl_speech_shortcut` appears in the typed
backup contract; the others are outside it (settings-catalog document).

Read but owned elsewhere: `auto_capitalize_first_letter`, `auto_capitalize_after_period`,
`auto_capitalize_restricted_fields`, raw-mode app list (text-input document);
`screen_trackpad_enabled` and the trackpad trigger key (trackpad document); the strip slots and
`status_bar_visibility` (strip document).

## 14. Device facts

| # | Fact | Evidence |
|---|---|---|
| D1 | Fn reaches apps only as auto-repeat Ctrl events with scancode 251, every ~50 ms starting ~400 ms into the hold; no initial down, no key-up, and a quick tap sends nothing. Five repeats is ~600 ms of hold. | docs/titan2elite/DEVICE.md "Fn event delivery model"; service comment on the burst |
| D2 | Google's speech engines close the microphone about 2 s after any sound and report "no match" when no words came through; on a Titan 2 in Teams speech began 10 ms after the microphone closed. | commit 0f8b040; PHYSIBOARD_CHANGES.md 2.0.7 |
| D3 | Google's system engine delivers the whole utterance as its last partial followed by an empty final. Seen in Teams on a Titan 2. | commit 0f8b040; comment on the partial insertion; PHYSIBOARD_CHANGES.md 2.0.7 |
| D4 | The recognizer treats the silence-length hints as hints and ends a request after about 1 s of silence regardless of the setting. | PHYSIBOARD_CHANGES.md 1.0.5; commit b0f4d18 |
| D5 | Notification-class vibration is muted whenever the phone's notification vibration is off; the 1.0.4 cues were silent for that reason. | PHYSIBOARD_CHANGES.md 1.0.5; comment in the haptics source |
| D6 | The orange side key ("func1") never reaches an input method; the vendor launches the package/activity pair in `Settings.System` keys `func1_long_press_package` / `func1_long_press_activity`, gated by `func1_shortcut_key_enable`; stock points the long press at Gemini's entry activity. | docs/titan2elite/DEVICE.md "Vendor key-config"; PHYSIBOARD_CHANGES.md 1.0.7; side-key source comments |
| D7 | Google's engine beeps at each internal "no speech" ending during the 10 s first-words grace. | .claude-context.md open items (2.0.7 session note); not yet raised with the user |
| D8 | Sym is keycode 63, scancode 253. | docs/titan2elite/DEVICE.md keymap table |
| D9 | With no focused text field the keyboard receives no key events at all, so Fn-hold dictation (like Fn+Home) cannot fire in the camera or on a page without an editor. Structural, not per-app. | .claude-context.md ("Fn+Home with no text field is structural"); memory note "Fn keys need a focused editor" |
| D10 | The default touch-feedback amplitude is easy to miss with the phone on a desk or at arm's length; the cues run at amplitude 255 and are made "firmer" only by longer pulses. | commit a4a79c5; PHYSIBOARD_CHANGES.md 1.0.8 |
| D11 | The maintainer's real configuration, re-captured 2026-08-27 at app 1.2.3, has Fn-hold on, cues on, masking off, a 2000 ms pause, the chord off, and the side key bound. | first-run defaults comment in the settings source |
| D12 | Launching the assistant trampoline without its own task pulled PhysiBoard's task to the front and Android's starting window showed the app; fixed with an own task and a no-preview transparent theme. | commit ab68fde; PHYSIBOARD_CHANGES.md 1.0.8 |
| D13 | A system update or another app can rewrite the vendor slot without notice, leaving the stored flag on while the key does nothing. | commit 2a54273 |

## 15. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| Trigger fired twice before the engine reports "ready" | second trigger restarts the session state and issues another request instead of stopping | "active" is set at ready-for-speech, not at the trigger |
| Fn held with dictation already active | the fifth repeat stops the session (stop requested) | start-or-stop toggles |
| Speech recognition unavailable on the device | trigger does nothing visible; "Speech recognition not available." goes to the log only | the keyboard service only logs start failures |
| Permission denied permanently | every trigger flashes a translucent activity that returns DENIED; no message | no rationale screen; the denied broadcast only clears the pending flag |
| Pause 0 with "Let the engine time the pause" on | segmented mode is not used (needs pause > 0); each session is one utterance | segmented requires a pause to end on |
| Pause 500 ms | keyboard silence timer = 400 ms (floor), watchdog = 5500 ms | pause − 1000 clamped to at least 400 |
| Engine answers a segmented request with a plain final | text committed; session ends pause + 5000 ms later with the stop cue; refusal latch set; the next session uses the restart loop | zero segments seen when the watchdog fires |
| Engine changed in settings | the next session rebuilds the recognizer and clears the refusal latch | the latch is per recognizer |
| Busy error (8) after speech has been heard, in a continuation | rule 8: toast "Speech recognition error.", session ends | busy is only absorbed before speech |
| Quiet error within 700 ms of a continuation, no partial | toast "No text recognized. Try again." or "No speech input detected." and the session ends | fast failure loop guard |
| Leading space before an utterance that had a partial | the 10-character read before the cursor includes the composing partial, whose last character is a letter, so a space is prepended; even at the start of an empty field the committed text is " Hello world " | partials place the cursor after themselves since 2.0.7; needs device evidence to confirm visibility |
| Capital lost on the second and later partials with automatic punctuation off | the cursor rule sees the previous partial's words before the cursor and says "no" | same cause; masked when the engine capitalises |
| "appunto" or "spunto" dictated with automatic punctuation off | "ap. " / "s. " | punctuation words are substring replacements |
| Dictating after "Hello." (no space) | "Hello.world " with no capital | leading space only after a letter; after-period needs whitespace |
| Dictating after a digit | no leading space | only letters trigger the leading space |
| Accented lowercase after a sentence end inside one final ("ciao. èra") | not capitalised | the after-period rule matches ASCII a to z only |
| Raw-mode app or password field | no capitalisation of partials or finals; punctuation words and spacing still apply | capitalisation is the only field-gated step |
| No input connection when a result arrives | the words are dropped silently and the session continues | writes skip when there is no connection |
| App blinks its field off and on while dictating | session continues | the 500 ms editor-gone grace is cancelled by the new field |
| User switches to another app mid-dictation | session ends at once with the stop cue | different owner package |
| Keyboard service destroyed mid-session | recognizer destroyed, no stop cue, strip state not updated | destroy path skips session-end bookkeeping |
| Notification vibration off, system haptic feedback on | cues play | plain vibration, no notification attributes (D5) |
| System haptic feedback off | no cues, whatever `dictation_haptics` says | effective value is gated on the system toggle |
| Strip hidden | no visual sign of listening at all | the microphone button is the only indicator; the hint hook is empty |
| Settings search for "Vibrate on dictation" | opens the Voice screen, where the row does not exist | catalog targets Voice for the haptic rows that live on Sound & Haptics |
| Fresh install, orange key long press | still launches Gemini although `side_key_assistant` reads true, until the Voice screen is opened (flag corrected to false) or the switch is turned on | first-run defaults set the flag without writing the vendor slot |
| Sym is the screen trackpad trigger | "Hold Sym for the assistant" shown off and disabled; holds go to the trackpad | two features cannot share the hold |
| Assistant package registered for none of the three actions | untargeted attempt; the system chooser may appear | fallback after targeted attempts |
| Voice screen intro says "then release" | releasing Fn does nothing (the Titan never sends the release); the pause or a second trigger ends the session | the copy predates the pause handling |
| The legacy one-shot recognizer activity | never launched; its result broadcast `brobata.physiboard.SPEECH_RESULT` is still received (package-internal) and would commit the text after 300 ms, retrying up to 10 times at 100 ms if no input connection | dead upstream path kept for compatibility |
| Language tag falls back to `it-IT` | only when neither the subtype nor the device locale yields a language | upstream origin |
| Max results 5 requested | only the first result is used; alternatives are logged in debug builds only | the release build strips logs below error level |

## 16. Test cases

Encodable as JVM tests against the pure decision rules, the text transforms, and a fake input
connection.

| # | Input | Expected |
|---|---|---|
| T1 | quiet re-listen decision: code 7, active, no stop, not heard, elapsed 3000, restarts 0 | re-listen |
| T2 | same with code 6 | re-listen |
| T3 | same with code 8 | re-listen, 300 ms delay |
| T4 | same with code 3, 2, 9, 5 | no re-listen |
| T5 | same as T1 but heard speech | no re-listen |
| T6 | same as T1 but stop requested; or session inactive | no re-listen |
| T7 | elapsed 9999 | re-listen; elapsed 10000 | no re-listen |
| T8 | restarts 4 | re-listen; restarts 5 | no re-listen |
| T9 | refusal classification: code 5 | refusal; code 99 | refusal; codes 8, 2, 1, 4, 11, 10, 3, 9, 7, 6, 12, 13 | not a refusal |
| T10 | segmented decision: API 33, setting on, not refused, pause 2500 | segmented; API 34 | segmented; API 32 | not; API 29 | not |
| T11 | segmented decision: setting off | not; refused | not; pause 0 | not; pause 500 | segmented |
| T12 | language: subtype `fr_FR`, device en-US | `fr-FR` |
| T13 | language: subtype absent, device en-GB | `en-GB` |
| T14 | language: subtype `it_IT` | `it-IT`; `de_DE` | `de-DE`; `pt_BR` | `pt-BR`; `fr` | `fr`; `es-ES` | `es-ES`; `sr_Latn_RS` | `sr-Latn-RS` |
| T15 | language: subtype `___` | no language (falls to device locale) |
| T16 | language: subtype absent, device locale absent | `it-IT` |
| T17 | silence timer for pause 2500 | 1500 ms; pause 1200 | 400 ms; pause 500 | 400 ms; pause 0 | not armed |
| T18 | watchdog for pause 2500 | 7500 ms |
| T19 | punctuation words: "ciao virgola come stai punto" | "ciao, come stai." |
| T20 | punctuation words: "domanda punto interrogativo si" | "domanda? si" |
| T21 | punctuation words: "a due punti b" | "a: b"; "ok PUNTO E VIRGOLA x" | "ok; x" (case-insensitive) |
| T22 | punctuation words: "appunto oggi" | "ap. oggi" (documents the substring quirk) |
| T23 | punctuation words: "punto" alone | unchanged |
| T24 | after-period capitalisation on: "ciao. come va? bene! ok" | "ciao. Come va? Bene! Ok" |
| T25 | after-period on: "ciao. èra" | unchanged |
| T26 | first-letter on, field empty, final "hello" | committed "Hello " with cursor after |
| T27 | field "Hello", cursor at end, final "world" | "Hello world " |
| T28 | field "Hello " (trailing space), final "world" | "Hello world " |
| T29 | field "5", final "world" | "5world " |
| T30 | field "Hi. " and after-period on, final "there" | "Hi. There " |
| T31 | field "Hi." (no space), final "there" | "Hi.there " |
| T32 | raw-mode app, field empty, final "hello" | "hello " |
| T33 | partial "hello" then partial "hello world" then final "hello world" | composing "Hello", composing "Hello world" (both with cursor after), then the composing region replaced by the final text and finished as ordinary text |
| T34 | partial "hello world" then an empty final | utterance finished from "hello world" with final spacing and capitalisation |
| T35 | partial "hello world" then code 7, session active, pause 2500 | utterance finished from the partial; silence timer armed 1500 ms; continuation started |
| T36 | partial "hello world" then code 7, stop requested | utterance finished from the partial; session ends |
| T37 | new-utterance test: previous "hello world", next "hello world again" | same utterance (prefix) |
| T38 | previous "hello world", next "HELLO there" | same utterance (first word equal ignoring case) |
| T39 | previous "hello world", next "goodbye now" | new utterance: previous committed, a space added if the char before the cursor is a letter or digit, new composing region |
| T40 | previous "", next "x" | same utterance |
| T41 | error order: segmented, 0 segments, 900 ms in, code 5 | retry as plain, latch set, no toast |
| T42 | segmented, 0 segments, 900 ms in, code 2 | not a refusal; falls to the quiet re-listen check (not code 7/6/8) then rule 8: toast "Network error.", session ends |
| T43 | segmented, 0 segments, 1300 ms in, code 5 | not a refusal (window is 1200 ms); rule 8: "Speech recognition error." |
| T44 | continuation started 800 ms ago, code 6, silence timer pending | partial settled, new continuation, no toast |
| T45 | continuation started 800 ms ago, code 6, timer already fired | session ends (it was already ending) |
| T46 | continuation started 300 ms ago, code 7, no partial | rule 8: "No text recognized. Try again." |
| T47 | segmented, 2 segments seen, code 7 | session ends quietly |
| T48 | session inactive, code 7 | ignored |
| T49 | rule 8 messages: 7 | "No text recognized. Try again."; 6 | "No speech input detected."; 9 | "Microphone permission denied."; 2 | "Network error."; 4 | "Speech recognition error." |
| T50 | cues: session start then two error callbacks after end | one start cue, one stop cue |
| T51 | cues: `dictation_haptics` on, system haptic off | no cues |
| T52 | strength `light` start | pattern [0, 35, 60, 35] ms at amplitudes [0, 180, 0, 180]; `standard` | [0, 60, 70, 60] at 255; `strong` | [0, 150, 90, 150] at 255; unknown value | strong |
| T53 | stop cue `light` | 90 ms at 180; `standard` | 160 ms at 255; `strong` | 300 ms at 255 |
| T54 | mic level colour: −10 dB | (128, 0, 0); 0 dB | (255, 50, 50); −5 dB | intensity 0.25: (159, 12, 12) |
| T55 | editor gone: field closes, new field of the same package arrives at 200 ms | session continues; at 500 ms nothing happens |
| T56 | field closes, nothing replaces it for 500 ms | session ends, recognizer cancelled |
| T57 | new field from a different package while active | session ends immediately |
| T58 | assistant action `hands_free` | try order: hands-free, voice command, assist (chosen first, then auto order without duplicates) |
| T59 | assistant action `auto` | voice command, hands-free, assist |
| T60 | assistant package from `assistant` = `com.x/.Y` | package `com.x`; from `voice_interaction_service` = `com.z` (bare) | `com.z`; both empty | untargeted only |
| T61 | side-key value safety: `com.google.android.apps.bard` | safe; `com.x.$Inner` | safe; `a;rm -rf` | unsafe; 257 characters | unsafe; empty | unsafe; `.leading` | unsafe |
| T62 | bind with WRITE_SETTINGS, slot currently `com.g/.Main` uncaptured | capture recorded, three keys written, success |
| T63 | bind with a malformed current slot | no capture, keys written |
| T64 | restore with nothing captured | success, nothing written |
| T65 | restore with a malformed capture | capture dropped, nothing written, success |
| T66 | slot reads `brobata.physiboard` + trampoline class | switch shows on; slot reads Gemini | switch shows off and `side_key_assistant` set false |
| T67 | pause slider: raw 2730 | stored 2500; raw 10000 | 10000; set 12000 programmatically | stored 10000 |

## 17. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| Hold-Fn trigger with the burst rule | keep | the signature trigger; only sane way given D1 |
| Strip microphone button with level colour | keep | the only visual indicator; cheap |
| Alt+Ctrl chord trigger | drop | no Ctrl key on the Titan; with Fn-hold on it can never fire, and with it off it fires by accident 400 ms into any Alt+Fn hold |
| Dedicated microphone keycode 667 | drop | Minimal Phone only |
| Legacy one-shot recognizer activity and `brobata.physiboard.SPEECH_RESULT` receiver | drop | dead since the in-place recognizer; never launched |
| Engine picker with system default, on-device and installed services | keep | the engines really differ in endpointing; the maintainer switches |
| Friendly engine names and details | keep | the picker is useless without them |
| First-words grace (10 s, 5 re-listens, 300 ms busy retry) | keep | D2 makes it necessary |
| Segmented mode with watchdog and refusal latch | undecided | works around D4 on Android 13+ but has needed three rounds of fixes; if the restart loop alone feels fine on the Titan, drop it |
| Restart-loop mode with the keyboard's own silence timer | keep | the guaranteed fallback for D4 |
| Explicit stop delivering the in-flight utterance | keep | |
| Editor-gone end (500 ms grace) and other-app end | keep | otherwise the microphone stays open against nothing |
| Partials as composing text with the cursor after | keep | D3 |
| Empty final and quiet-error-after-partial finishing from the partial | keep | D3 |
| New-utterance detection within a request | undecided | written for a pre-segmented world; check whether Google still restarts hypotheses inside one request |
| Italian punctuation words | drop | Google punctuates itself; the table is substring-based and English-less; if kept, make it word-based and per language |
| First-letter and after-period capitalisation of finals | keep | but read the context before the composing region, not after it |
| Leading-space-after-letter and trailing-space rules | keep | fix the read so the partial's own words are excluded |
| Automatic punctuation request option | keep | default on; the reason Google output is clean |
| Offensive-word masking option | keep | one flag; the maintainer turns it off |
| Language from the keyboard subtype, device fallback | keep | drop the `it-IT` final fallback in favour of the device locale |
| Haptic cues with three strengths, plain vibration | keep | D5, D10 |
| System haptic gating | keep | |
| Strength preview on tap | keep | |
| Microphone permission trampoline activity and broadcasts | keep | a keyboard still cannot ask directly; add a rationale line |
| Hold Sym for the assistant | keep | cheap; the user asked for it |
| Orange side key binding via `func1_*` with capture and restore | keep | Titan-specific and the only route (D6) |
| Assistant action picker (auto / voice command / hands-free / assist) | keep | assistants really differ |
| "Voice assistant" command | keep | one line on top of the launcher |
| Toasts for engine errors | keep | but route start failures (unavailable, permission) to a toast too |
| "Speak now..." prompt extra | drop | never shown by PhysiBoard |
| Max results 5 | drop | only the first is used; ask for 1 |
| First-run defaults for this subsystem | keep | Fn-hold on, cues on, masking off, 2000 ms pause; but do not set `side_key_assistant` without binding |
| Settings search entries for the haptic rows pointing at Voice | drop | move the haptic rows to Voice, or fix the target |
| Voice screen intro "then release" | drop | wrong on the Titan |

## 18. Provenance

- app/src/main/java/brobata/physiboard/inputmethod/SpeechRecognitionManager.kt
- app/src/main/java/brobata/physiboard/inputmethod/SpeechRecognitionActivity.kt
- app/src/main/java/brobata/physiboard/inputmethod/RecognitionEngines.kt
- app/src/main/java/brobata/physiboard/inputmethod/DictationHaptics.kt
- app/src/main/java/brobata/physiboard/inputmethod/PermissionRequestActivity.kt
- app/src/main/java/brobata/physiboard/inputmethod/AssistantLauncher.kt
- app/src/main/java/brobata/physiboard/inputmethod/AssistantTriggerActivity.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (dictation, Fn burst, Sym hold, permission receiver, editor-gone, mic key regions)
- app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt (Alt+Ctrl chord)
- app/src/main/java/brobata/physiboard/inputmethod/AutoCapitalizeHelper.kt (cursor rule and context read)
- app/src/main/java/brobata/physiboard/inputmethod/CandidatesBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonHost.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/StatusBarButtonStyles.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/MicrophoneButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/suggestions/ui/FullSuggestionsBar.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/HamburgerMenuView.kt
- app/src/main/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtils.kt
- app/src/main/java/brobata/physiboard/VoiceSettingsScreen.kt
- app/src/main/java/brobata/physiboard/CustomizationSettingsScreen.kt (Sound & Haptics rows)
- app/src/main/java/brobata/physiboard/VendorSideKeyManager.kt
- app/src/main/java/brobata/physiboard/SystemChangeManager.kt (side key revert)
- app/src/main/java/brobata/physiboard/SettingsManager.kt (dictation, assistant, side key, first-run defaults, strip slot defaults)
- app/src/main/java/brobata/physiboard/SettingsCatalog.kt
- app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- app/src/main/java/brobata/physiboard/OnboardingScreen.kt
- app/src/main/java/brobata/physiboard/commands/CommandExecutor.kt
- app/src/main/java/brobata/physiboard/commands/PhysiBoardCommandSource.kt
- app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- app/src/main/AndroidManifest.xml
- app/src/main/res/values/strings.xml
- app/src/main/res/values/themes.xml
- app/src/test/java/brobata/physiboard/inputmethod/SpeechRecognitionFirstWordsTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SpeechRecognitionManagerLanguageTagTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SpeechRecognitionSegmentedSessionTest.kt
- PHYSIBOARD_CHANGES.md (2.0.7, 1.0.8, 1.0.7, 1.0.5, 1.0.4, 1.0.1, 0.86-physi entries)
- docs/plans/physiboard-roadmap.md (Workstream 5)
- docs/plans/rebuild-from-scratch.md
- docs/titan2elite/DEVICE.md
- docs/spec/README.md
- docs/spec/keys-and-modifiers.md (Fn burst, Sym hold, Alt+Ctrl sections, for consistency)
- docs/spec/text-input.md (format reference)
- .claude-context.md (open items)
- git log messages of commits 0f8b040, b0f4d18, 939d857, 0286208, a4a79c5, ab68fde, 2a54273, dab757d, 7648e1b, 55f44e5, a3f30a3
