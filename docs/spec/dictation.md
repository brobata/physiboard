# Dictation: speech sessions, engines, stopping, cues, permissions, assistant

This document describes voice dictation in PhysiBoard 3.0 on the Titan 2 Elite: how a session
starts and, above all, how it stops; which speech engine transcribes it and with which request;
how provisional and final words land in the text field; audio focus and music; the haptic cues
and the status icon; the microphone permission flow; the Voice settings screen; and the related
"ask the assistant" triggers (hold Sym, the orange side key, a bindable command). How the Fn
burst and the Sym hold are detected at the key level is specified in the keys document; this
document picks up at the moment a trigger fires.

Sections 1 to 9 and 13 to 17 describe 3.0 as built on 2026-10-07, after the root cause of the
maintainer's "it cuts me off" report was found in the phone's log (section 14, D14 to D18).
Sections 10 to 12 carry the 2.x behaviour that 3.0 kept. Where a 2.x rule was dropped, section 17
says why.

## 1. Vocabulary

- **Session**: one dictation, from the trigger to the stop cue. A session may contain several
  engine requests (section 6); the user sees one start cue and one stop cue per session.
- **Request**: one "listen" issued to the speech engine. A **segmented** request (Android 13+)
  asks the engine to hold one session open and deliver one result per utterance; a **plain**
  request ends on the engine's own endpointing after one utterance.
- **Partial** (provisional result): the engine's running hypothesis while the user is still
  talking. Shown in the field as composing text.
- **Final**: the engine's result for a plain request. **Segment**: one utterance's result inside
  a segmented request. Both are committed to the field the same way.
- **Phase**: `STARTING` (request issued, microphone not yet open), `LISTENING` (words land until
  something stops the session), `STOPPING` (the engine has been asked for its last words).
- **Silence limit**: `dictation_stop_after_silence_ms` when greater than 0, else the 60 s safety
  limit. The session stops by itself once nothing has been heard for that long.
- **Quiet error**: the engine reporting "no match" (code 7) or "speech timeout" (code 6). It
  means the engine closed its microphone on silence; it is never a failure of the session.
  Android's numeric codes used throughout: 1 network timeout, 2 network, 3 audio, 4 server,
  5 client, 6 speech timeout, 7 no match, 8 recognizer busy, 9 insufficient permissions, 10 too
  many requests, 11 server disconnected, 12 language not supported, 13 language unavailable,
  14 cannot check support, 15 cannot listen to download events.
- **Heard speech**: the session has received at least one non-empty partial, one segment or one
  final with text.
- **Engine id**: the value of `dictation_engine`: the empty string (system default), the word
  `ondevice`, or a flattened Android component name `package/class` naming one installed
  recognition service.

## 2. Ways a session starts, and the one way it is driven

The Fn burst (keys document 3.3, D1) is the trigger. Fn never delivers a release to the keyboard
(D1, D19), so "hold to talk, release to stop" is impossible on this hardware, and a session
cannot be ended by guessing when the user has finished either (that guess is the whole of the
2.x complaint, D14 to D16). The model is therefore **start with Fn, stop explicitly**:

- a trigger with no session running starts one;
- a trigger while a session is `LISTENING` stops it gracefully (6.5);
- a trigger while the session is still `STARTING` or already `STOPPING` ends it at once (the
  user is saying "off, now").

There is no toggle-off window problem: a session exists from the trigger, so a second Fn burst
always acts on it.

### 2.1 Hold Fn

Only while `fn_long_press_speech` is on (default on). The keys document specifies the burst:
five consecutive Fn-origin key repeats (scancode `fn_speech_scan_code`, default 251, or the
Android FUNCTION keycode 119) with no other key in between and no gap over 200 ms, which on the
Titan is about 600 ms of hold (D1). On the fifth repeat the keyboard clears every Ctrl and Alt
state including "physically pressed", refreshes the status display, and fires the trigger. Every
Fn-origin event is consumed while the setting is on, so the hold never leaves Ctrl stuck. The
keyboard only receives key events at all while an editor is connected (D9), so a hold in the
camera or on a page with no text field does nothing.

### 2.2 Other triggers

The strip's microphone button (status-bar document) fires the same trigger on tap, and so does
the **"Dictation" command** (id `physiboard.toggle_dictation`, internal action
`toggle_dictation`, "Start or stop dictation, like holding Fn"), bindable to any launcher key,
Fn-layer slot or nav-mode key and offered by the quick launcher (launcher document 8.2). The
Alt+Ctrl chord and the keycode-667 microphone key of 2.x are gone (section 17).

### 2.3 The assistant triggers are not dictation

Holding Sym, long-pressing the orange side key, and the "Voice assistant" command open the
device's voice assistant already listening. They never start a dictation session and never
insert text; section 11 covers them.

### 2.4 What starting does

1. If microphone permission (`android.permission.RECORD_AUDIO`) is not granted: remember that a
   start is pending and open the permission activity (section 10). Nothing else happens until
   the grant broadcast arrives.
2. Capture the package of the current editor as the session's owner (section 3) and the text
   before the cursor as the first utterance's frozen context (7.5).
3. Plan the request (section 5) from the settings and the engine's refusal latch (6.3).
4. Hold the keyboard visible for the system (6.8): without it the microphone is silenced.
5. If `dictation_pause_media` is on, take exclusive transient audio focus (6.7), before the
   microphone opens.
6. Once the system's own answer for the keyboard's RECORD_AUDIO reads allowed (6.8), create a
   recognizer for the engine id (4.2) and issue the request. If no recognizer can be created, or
   the request cannot be issued, the session ends with a message (6.6).

The session is now `STARTING`. It becomes `LISTENING` on the engine's "ready for speech"; the
start cue plays at the first audio level report (8.1).

## 3. Ways a session ends

Every ending gives audio focus back (6.7), releases the keyboard's visibility hold (6.8), plays
the stop cue if the start cue played (8.1), and releases the recognizer so the next session binds
a fresh one (D20).

| Cause | What happens | Words on screen |
|---|---|---|
| Fn again while `LISTENING` | graceful stop (6.5): the engine is asked for its last words; the session ends on their arrival or 1500 ms later | committed (the engine's final text if it comes, else the partial as shown) |
| Fn again while `STARTING` or `STOPPING` | immediate end, recognizer cancelled | committed as shown |
| Any key other than a modifier goes down, with `dictation_stop_on_typing` on (default) | immediate end, recognizer cancelled; the key then does its usual work (a letter types, Enter sends, Backspace deletes) | committed as shown, so what the user saw is what the key acts on |
| The silence limit (6.4) | graceful stop | committed |
| The 10-minute session cap (6.4) | graceful stop | committed |
| Another app takes audio focus for good (6.7) | immediate end | committed |
| Real error (6.6) | session ends; message | committed |
| Five fast failures or busy answers in a row (6.6) | session ends; "Speech recognition error." | committed |
| Offline language pack missing and nothing else allowed (4.3) | session ends; message | committed |
| The field rejected an insert (exception while writing) | recognizer cancelled; session ends | cleared |
| Editor gone: the app closes its text field and no new field replaces it within 500 ms | recognizer cancelled; session ends | cleared |
| Another app takes the editor (a new field whose package differs from the owner's) | immediately as above | cleared |
| The request could not be issued (2.4 step 5) | session ends; message | cleared |
| Keyboard service destroyed | timers cancelled, recognizer destroyed, focus given back; no cue | left as is |

A new field in the **same** app (the app blinking its field off and on, or the user moving to
another field in the same app) does not end the session: the 500 ms grace after the field closes
is cancelled by the new field's arrival.

With `dictation_stop_on_typing` off, a key leaves the session running; an edit the key makes to
the field then invalidates the utterance in progress (7.4), so the deleted words are never typed
back.

## 4. Engines

### 4.1 Discovery and ranking in the picker

The "Speech engine" picker lists, in this order:

1. **System default** (id empty string). Detail text: "Follows Android's voice input setting,
   which is X today. Pick this to keep matching the rest of the phone." where X is the friendly
   name of the package in `Settings.Secure` key `voice_recognition_service`; when that key is
   empty or unreadable the detail is "Follows Android's voice input setting".
2. **Android offline engine** (id `ondevice`), only on Android 12 or later and only when the
   platform reports an on-device recognizer. On the Titan 2 it never does (D18), so the row is
   absent there.
3. Every installed service answering the `android.speech.RecognitionService` intent, in the
   order the package manager returns them, each with id `package/class`. The keyboard declares a
   `<queries>` interest in that intent, without which package visibility hid the services it had
   not named (D18). The row whose package equals the system default's package carries the
   accent-coloured tag "Currently the system default".

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
| `ondevice` on Android 12+ with an on-device recognizer available | the platform's on-device recognizer (`createOnDeviceSpeechRecognizer`) |
| `ondevice` otherwise | the system default |
| `package/class` still installed as a recognition service | that service |
| `package/class` no longer installed | the system default |
| any creation failure | the system default; if that fails too, no recognizer and the start fails per 2.4 step 5 |

The summary row under "Speech engine" shows the picker label for the stored id, or "System
default" when the id no longer matches any row.

### 4.3 On the phone, or online

`dictation_prefer_offline` (default on) sets the request's `EXTRA_PREFER_OFFLINE`, which asks the
engine to use only its on-device recognizer. On the Titan the system default is Google's Speech
Services (D4), which runs an on-device engine ("SODA") beside its network one and, per the
phone's own log, **only punctuates when offline is preferred** (D15: "EXTRA_ENABLE_FORMATTING
can't be used when EXTRA_PREFER_OFFLINE is false"). So the on-device path is the fast one, the
private one and the punctuating one. The platform's own on-device recognizer
(`SpeechRecognizer.createOnDeviceSpeechRecognizer`) is not configured on this phone (D18), which
is why the preference goes through the system default rather than the `ondevice` engine id.

When the on-device recognizer has no pack for the session's language (code 12 or 13):

- outside private mode, the same session re-issues its request online, once, with a log line
  ("offline recognizer has no pack for this language; this session goes online") and nothing
  shown; a second language error ends the session with the message below;
- in private mode (app-shell.md 31), or with `dictation_prefer_offline` off and the engine still
  answering 12/13, the session ends with "Private mode keeps speech on the phone, and the offline
  speech pack for this language isn't installed." or "The offline speech pack for this language
  isn't installed. Download it in Speech Services by Google, or turn off \"Keep speech on the
  phone\"." respectively.

Private mode forces `EXTRA_PREFER_OFFLINE` on whatever the setting says; turned on in the middle
of a session that has already gone online, it stops that session gracefully. The pack is downloaded
in the engine's own settings (Speech Services by Google > Offline speech recognition); the
platform's `triggerModelDownload` is not used.

### 4.4 What the release install uses

`dictation_engine` is empty on a fresh install, so dictation goes through the system default.
On the maintainer's Titan 2 that is
`com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`
(D4), read from `voice_recognition_service` on 2026-10-07.

## 5. The request

Every request is an `android.speech.action.RECOGNIZE_SPEECH` request to the chosen recognizer
with:

- language model: free-form;
- language: the tag from 5.1;
- partial results: requested;
- calling package: the keyboard's package;
- mask offensive words: the value of `dictation_mask_offensive` (the platform's own default is
  masking on; the first-run default is off);
- prefer offline: per 4.3 (on by default; forced on in private mode; turned off for the one
  online fallback);
- complete silence length: the silence limit (section 1) plus 1000 ms, as an **int**. This is
  the length that ends a segmented session; the margin keeps the keyboard's own silence timer
  (6.4) ahead of the engine's, so the keyboard decides. Google reads it with `getIntExtra`
  (D23): the long that 2.x and the first two 3.0 builds sent was thrown away ("expected Integer
  but value was a java.lang.Long. The default value 0 was returned"), after which the service
  ignored the segmented request too ("EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS is not
  set with positive value; ignoring EXTRA_SEGMENTED_SESSION"). So no build had ever actually
  asked Google for a segmented session, and every request ran in its `AMBIENT_ONESHOT` domain
  and ended at the first breath; that, not the keyboard's timers, is the restart the user sees
  as the privacy indicator blinking;
- minimum length: the same value, as an **int** (`EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`,
  "the recognizer will not stop recognizing speech before this amount of time"), so that even an
  engine that ignores the segmented request keeps one request open across pauses and streams
  its hypothesis as partials (which 7.2 and 7.3 already handle); Fn's stop still ends it at
  once through `stopListening`;
- `android.speech.extra.DICTATION_MODE` = true: Google's own continuous-dictation flag, the one
  Chrome's Web Speech glue sets for a continuous session (D17). Undocumented, so nothing depends
  on it; it is sent because it costs nothing and the engine that ignores it ignores it;
- on Android 13 or later, when `dictation_auto_punctuation` is on: `EXTRA_ENABLE_FORMATTING` =
  `quality` (the engine punctuates and capitalises; the result list then carries the formatted
  hypothesis first and the raw one second, and only the first is used);
- on Android 13 or later, for a segmented request: `EXTRA_SEGMENTED_SESSION` =
  `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`, that is, the **name of the extra** that
  ends the session, as a String. The platform documents the value as "the extra used as the
  string value here"; 2.x and the first 3.0 build put the pause itself (a long) here, which
  Google's service rejected on every single request ("Key android.speech.extra.SEGMENTED_SESSION
  expected String but value was a java.lang.Long ... Wrong value passed to
  EXTRA_SEGMENTED_SESSION; ignoring it", D15). Segmented mode had therefore never once been in
  force.

Not sent: the "possibly complete silence" hint (the platform says to leave it alone), a prompt,
a maximum-results count (with formatting the engine returns two hypotheses by design).

Whether Google honours the segmented request or the minimum length once they are sent as ints
is not yet observed on the Titan (every earlier log had them discarded); the first log with
`applicationDomain` other than `AMBIENT_ONESHOT`, or a `SODA session` outliving a pause,
settles it. If it honours neither, the next step is the seamless restart of 6.2's last
paragraph.

A request is segmented when the Android version is 13 or later and the engine has not refused
segmented sessions (6.3). `dictation_continuous_session` is gone: segmented mode is simply used
where it works, with automatic fallback.

### 5.1 Language selection

1. Take the current keyboard subtype's locale: its language tag if non-blank, otherwise its
   legacy locale string. Trim it, replace every `_` with `-`, and parse it as a language tag. If
   the result has a language, use its canonical tag.
2. Otherwise use the device's first configured locale as a tag, if non-empty.
3. Otherwise `en-US`.

Examples: `fr_FR` gives `fr-FR`; `it_IT` gives `it-IT`; `de_DE` gives `de-DE`; `pt_BR` gives
`pt-BR`; `fr` gives `fr`; `es-ES` gives `es-ES`; `sr_Latn_RS` gives `sr-Latn-RS`; `___` has no
language and falls through; a missing subtype with a British device gives `en-GB`.

## 6. Session lifecycle and timing

### 6.1 Start and the first cue

The trigger issues the first request at once (`STARTING`). The engine's "ready for speech" moves
the session to `LISTENING` and arms a 300 ms cue fallback. The **start cue plays at the engine's
first audio level report** of the session, which is the proof that the microphone is open and
audio is flowing (on the Titan, about 40 ms after the request, D14), or at the fallback for an
engine that reports no levels. It plays once per session: later "ready" reports and level
reports (re-listens, 6.2) do nothing. A trigger before "ready" ends the session at once; from
"ready" on it is the graceful stop (section 2).

Users start talking at the cue. The cue follows the open microphone rather than the request, so
the first syllable is not spoken into a microphone that is still opening; the 2.x cue was tied to
"ready", which Google reports before its on-device engine has initialised (D14).

### 6.2 The engine's own endings are re-listened, never surfaced

While the session is `LISTENING`, any of these is answered by **committing whatever partial is
on screen (7.3) and issuing the next request at once**, with the same request shape, no cue, no
message:

- a quiet error (7 or 6). Google's engine closes its microphone after about five seconds with
  nothing heard (D14: "mics audio processed in millis: 5000", then NO_SPEECH_DETECTED) and
  after about a second of silence following speech;
- the end of a segmented session (the engine reached the complete-silence length, or ended for
  its own reasons);
- an ordinary final (the engine ran one request per utterance; see 6.3 for the latch).

These endings only ever arrive in silence, so the ~40 to 100 ms the microphone takes to reopen
(D14, D23) is the one thing that can be lost, and only if the user starts a word exactly then.
Should the engine keep ending at every breath even with the request of section 5 honoured, the
planned next step is to overlap requests: open the next request on a second recognizer before
the first delivers its final (Google's service runs two on-device sessions concurrently,
`ConcurrentSodaManager ... enableConcurrency: true`, D23), and let 7.2's echo stripping remove
the words both heard. Not built yet. There is no
cap on how many times this happens: the 2.x "first-words grace" of ten seconds or five restarts,
after which the engine's silence was reported as "No speech input detected", is gone (D14 shows
exactly that: two five-second engine timeouts and the toast). The only thing that ends a silent
session is the silence limit (6.4).

An ending that **brought no words and arrived within 700 ms of its request** (a quiet error, an
empty final, an instant end of the segmented session) is the engine failing fast, not silence:
the re-listen waits 500 ms and the failure is counted; five in a row end the session with
"Speech recognition error." (6.6). Any speech, or any ending that brought words, resets the
count.

### 6.3 Segmented sessions and the refusal latch

A segmented request (section 5) makes the engine deliver one `onSegmentResults` per utterance
and hold its microphone open between them. Each segment is committed (7.3) and the session
simply goes on; nothing is re-issued and nothing is lost between utterances.

An engine may not support the mode. Two signs, both handled in the same session with no message:

- **Refusal**: an error within 1200 ms of the session start, with nothing heard yet, whose code
  is 5 (client) or any code not in the list {1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13} (so 5, 14,
  15 and unknown codes). The request is re-issued as a plain one.
- **Ignored**: an ordinary final (`onResults`) arrives for a segmented request. The text is
  committed, the next request is plain and is issued at once (6.2).

Either sets the **refusal latch**: later sessions ask for plain requests from the start, until
the engine setting changes (the latch is kept per engine id, not per recognizer object, since
every session binds a fresh recognizer, D20). The latch is logged ("segmented session refused by
the engine; falling back to one request per utterance").

In 2.x and the first 3.0 build an ordinary final in "segmented" mode armed a pause + 5000 ms
watchdog and issued **no** new request: the first sentence landed, the microphone stayed shut
for seven seconds while the user kept talking, and then the stop cue played (D15). That is the
"cuts me off in the middle of a conversation" of the maintainer's report.

### 6.4 The silence limit and the session cap

Two timers, both the keyboard's own, both ending the session **gracefully** (6.5) so the engine's
last words still land:

- **Silence limit**: the session stops once nothing has been heard for the silence limit
  (`dictation_stop_after_silence_ms`, or 60 s when that is 0). "Heard" is the engine's beginning
  of speech, a non-empty partial, a segment or a final with text; the limit is measured from the
  last of those, or from the session start. So a 20 s think between two sentences never ends a
  session with the setting at 0, and ends it only with the setting at 20 s or below.
- **Session cap**: 10 minutes after the start, whatever is heard.

The engine's own complete-silence length is the keyboard's limit plus 1000 ms (section 5), so
the keyboard's timer fires first; should the engine end first anyway, 6.2 re-listens and the
keyboard's timer still ends the session on time.

With the default (0), the session therefore runs until Fn, a key, a minute of silence, ten
minutes, or the field going away. That is the toggle-microphone model the maintainer asked for,
with a safety net for a microphone left open.

### 6.5 Explicit stop

The graceful stop asks the engine to stop listening ("Speech captured so far will be recognized
as if the user had stopped speaking at this point"), moves the session to `STOPPING`, drops any
pending re-listen or busy retry, and arms a **1500 ms stop watchdog**. The session then ends on
the first of:

- a final or the end of the segmented session: committed (7.3), session ends; a segment is
  committed and the session waits for the end that follows it (or the watchdog);
- any error, quiet or not: the partial on screen is committed, session ends, **no message**
  (a "no speech" answer to a stop is the normal case when the user said nothing after the last
  segment);
- the watchdog: the partial on screen is committed, the request is cancelled, session ends.

A partial arriving while `STOPPING` still composes (the final that follows replaces it). The
immediate stop (a key, lost focus, a second Fn) commits the partial as shown and cancels the
request straight away; whatever the cancelled request says afterwards is ignored, because the
session no longer exists.

### 6.6 Errors, in order

For every engine error the rules are tried top to bottom; the first match wins.

| # | Condition | Action |
|---|---|---|
| 1 | session `STOPPING` | commit the partial; end quietly (6.5) |
| 2 | segmented refusal (6.3) | re-issue plain; latch; log line |
| 3 | quiet error (7 or 6) | commit the partial; re-listen at once, or after 500 ms if it came within 700 ms of the request and brought no words; the fifth fast failure in a row ends the session with "Speech recognition error." (6.2) |
| 4 | busy (8) | retry after 300 ms; the fifth in a row ends the session with "Speech recognition error." |
| 5 | language (12 or 13) | per 4.3: once online, or end with the pack message |
| 6 | anything else | commit the partial; end the session with the message below |

Messages for rule 6: code 9 "Microphone permission denied."; codes 1, 2, 4 and 11 "Network
error."; every other code "Speech recognition error." A failure to issue the first request
(2.4 step 5) shows "Speech recognition isn't available on this phone.", "Microphone permission
denied." or "Speech recognition error." The messages are toasts (long); the two fallbacks of
rules 2 and 5 are log lines only.

### 6.7 Audio focus and music

With `dictation_pause_media` on (default), the session takes **exclusive transient audio focus**
(`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, usage media, content type speech) at the trigger, before
the microphone opens, and gives it back at every ending (section 3). The platform documents this
focus kind for exactly this: a short period during which no other app or system component should
play anything, with speech recognition and voice memos as the examples; a player that honours
focus pauses on the transient loss and resumes on the gain.

Why the keyboard holds it rather than leaving it to the engine: Google's recognizer takes
transient-exclusive focus of its own per request and abandons it when the request ends. With the
2.x restart loop that was every five seconds: on the Titan, Audible paused and resumed with every
request (D14, MediaFocusControl: `handleLoss` on each request, `handleGain` on each abandon).
With the keyboard's request underneath, the engine's own request takes focus from the keyboard
(a transient loss the keyboard ignores) and hands it back to the keyboard when it ends, so the
player stays paused for the whole session and resumes once, at the stop cue.

A **permanent** loss (`AUDIOFOCUS_LOSS`: a call, a video the user started) ends the session at
once, committing the words on screen. A failed focus request (during a call, for instance) is
logged and the session goes on without it.

### 6.8 The microphone and the keyboard window

RECORD_AUDIO is a "while in use" permission: the system evaluates it at the moment a recording
starts, from the uid's process state and its **microphone capability**
(`AppOpsUidStateTracker.evalModeInternal`: an op whose capability is
`PROCESS_CAPABILITY_FOREGROUND_MICROPHONE` is ignored unless the uid holds that capability). The
recognizer records on the keyboard's behalf (its attribution chain names the keyboard as the
next source), so the keyboard's own grant is what counts; when it fails, audioserver logs
`App op 27 missing, silencing record` and the engine hears zeros for the whole request (D22).

An input method holds that capability only while the input-method service considers it
**shown**: `InputMethodManagerService.showCurrentInputLocked` binds the keyboard a second time
with `IME_VISIBLE_BIND_FLAGS` (`BIND_TREAT_LIKE_ACTIVITY | BIND_FOREGROUND_SERVICE |
BIND_INCLUDE_CAPABILITIES | BIND_SHOWING_UI`), and `hideCurrentInputLocked` drops that binding.
On the Titan that is the difference between `curProcState=5` with a `FGS LACT UI CAPS`
connection record and `curProcState=16` with none (D22). The keyboard's own candidates window
(the strip) does not go through that path: it is shown by `setCandidatesViewShown`, which the
service never sees as "the input is shown". So a hardware-keyboard session with the strip
collapsed (PersaLink, a web terminal in the raw-mode list) had a 0 by 0 window, no visible
binding, and a silenced microphone; the one transcribed session of 2026-10-07 ran in Messages,
which had itself asked for the keyboard (`SHOW_SOFT_INPUT fromUser`).

So the session **holds the keyboard visible**: at the trigger, before anything else, the keyboard
calls `requestShowSelf(0)`, which reaches `showCurrentInputLocked` and the visible binding. On
this phone that shows nothing new: the platform asks `onShowInputRequested`, which answers no for
a hardware keyboard, and the candidates window stays exactly as it was; the keyboard ignores
that one refusal for the per-app dip (status-bar document 12.2), since it is not an app asking.
The request is repeated on every field start while the session runs (a web terminal restarts its
field on every key), since it is idempotent for the service. Every request of the session waits
for the grant: the keyboard polls the system's own answer
(`AppOpsManager.unsafeCheckOpNoThrow(OPSTR_RECORD_AUDIO)`, the same `evalMode`) every 40 ms, up
to 400 ms, and issues the request as soon as it reads allowed; after 400 ms it issues it anyway
and logs an error. A re-listen normally reads allowed at once; one that does not (the keyboard
was hidden under the running session: an app's own hide, Back with stop-on-typing off) holds
the keyboard visible again first and then waits.

At every ending the hold is released. The service is told to hide (`requestHideSelf(0)`) only
when all of these hold: the ending was not caused by a field change (the next field, or none,
is the system's to decide, and a hide sent now would land on it); the app had not itself asked
for the keyboard during this field (then the system hides it when the app says so); and nothing
of the keyboard is on screen anyway (a collapsed or hidden strip). A strip the user can see
stays, because the platform's hide would take the strip down with it.

A key bound to the "Dictation" command (2.2) first passes the key hook of section 3, which with
`dictation_stop_on_typing` on has already ended the session; the command that follows within
1500 ms is that same press and starts nothing. With no session running and no editable field,
the command fails with "No input context".

Why not a foreground service of type microphone: Android 14 and later refuse to start one from
the background, and the keyboard with its window hidden is exactly that; an input method's own
visible binding is the mechanism Android provides for voice typing, and it needs no notification.

## 7. Text insertion

All writes go through the current input connection on the main thread, in one batch edit. If
there is no input connection at the moment of a write, the write is silently skipped.

### 7.1 Partials

A non-empty partial is placed in the field as **composing text with the cursor after it**.
Composing text replaces the previous composing region, so successive partials overwrite each
other in place. Before it is shown, a partial gets **first-letter capitalisation only**, decided
against the utterance's frozen context (7.5): if capitalisation is allowed for this field and
`auto_capitalize_first_letter` is on and the context says "capitalise here", the first character
is title-cased. No after-sentence capitalisation is applied to partials; with automatic
punctuation on, Google's engine capitalises the hypothesis itself.

Empty partials are ignored. A non-empty partial marks the session as having heard speech and
refreshes the silence limit.

### 7.2 Echo of committed words

Google's segmented session reports the whole session's transcript so far in every partial and
every segment (D21). Before anything is composed or committed, the words this session has already
finished into the field are stripped from the front of the result, comparing word by word,
ignoring case and the punctuation the finisher added. A result that only repeats the committed
words writes nothing. A user who deliberately repeats the exact words just dictated loses the
repeat; accepted, since the alternative duplicated every segment.

### 7.3 Finishing an utterance

A final or segment with text finishes the utterance with that text. A final **without** text
(Google's engine sends the whole utterance as its last partial and then an empty final, D3)
finishes it from the last partial. Every ending of section 3 marked "committed" finishes the
utterance from the last partial as well. A final without text and with no partial remembered
inserts nothing.

"Finishing the utterance" with text T:

1. T is capitalised (7.5).
2. Spacing is applied (7.6) and the result is set as the composing text with the cursor after
   it, then the composing state is finished so it becomes ordinary text.
3. The next utterance's frozen context is this one's context plus the text just written (never a
   fresh read), and the written words are remembered for 7.2.

In a field that cannot be trusted with a composing region (a web terminal that drew the staged
sentence inverted and adopted none of it, 2026-09-26; a field that asks for no suggestions), the
**direct-commit** translation holds the words back and commits them once, when the utterance
finishes; such fields get no running preview, and nothing is ever deleted from them.

### 7.4 Words the user deleted are never typed back

With `dictation_stop_on_typing` off, a key that edits the field while an utterance is composing
moves that utterance to **invalidated**: every later partial of it writes nothing, and its final
or segment (which normally repeats the very words the user removed) writes nothing. Invalidation
ends at the utterance boundary; the next utterance composes normally. With the setting on
(default) the key ends the session first, so this never arises.

### 7.5 Capitalisation of a final

Skipped entirely when capitalisation is disabled for this field (raw-mode app; password field).
Otherwise:

1. **First letter**: if the frozen context says "capitalise here" (empty, ends in a newline, or
   ends in `.`, `!` or `?` followed by whitespace) and `auto_capitalize_first_letter` is on, the
   first character is title-cased when lowercase.
2. **After sentence end**: if `auto_capitalize_after_period` is on, every `.`, `!` or `?`
   followed by whitespace and an ASCII lowercase letter has that letter upper-cased.

The frozen context is the text before the cursor captured once at the session start (a 240
character window), extended by what this session itself wrote. It is never re-read from the
field, so this session's own composing text can never pose as context (the 2.x bug of the lost
capital on the second partial).

### 7.6 Spacing of a final

A leading space if the frozen context ends in a letter (digits and punctuation do not count),
and always a trailing space. So dictating "world" after "Hello" gives "Hello world "; after
"Hello " gives "Hello world "; after "5" gives "5world "; at the start of an empty field gives
"World " with no leading space (the 2.x leading-space quirk came from reading the live field,
whose last character was the partial's own).

## 8. Cues

### 8.1 Haptic

Cues play only when `dictation_haptics` is on (default on) **and** the system's own haptic
feedback toggle (`Settings.System` key `haptic_feedback_enabled`, read as on when unreadable) is
on. The start cue plays once per session, at the first audio level report (6.1); the stop cue
plays when the session ends, and only if a start cue was played for it (a session that failed
to start, or was ended by Fn before the microphone opened, has no stop cue). The service-destroy
path plays no stop cue.

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
the Sound & Haptics screen plays that level's **start** cue immediately as a preview.

### 8.2 Audio

PhysiBoard plays no sounds. Google's engine plays its own short failure sound at some of its
internal "no speech" endings (D7); whether it does so for a re-listened segmented request on the
Titan is not yet observed.

## 9. What the user sees while listening

- The **system status bar icon** (the keyboard's `showStatusIcon` slot) shows a microphone for
  the whole session, from the trigger to the end, and wins that slot over the nav-mode, modifier
  and Sym icons (keys document 13.1). It needs no keyboard window on screen and no overlay
  permission, so it is the indicator that is always there, including with the status bar hidden.
- The strip's microphone button, when the status bar is visible, turns red for the session and
  is redrawn from the engine's level reports (status-bar document 6.1).

Nothing else changes and no notification is posted.

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
defaults no longer set `side_key_assistant` (section 17).

## 12. Screens

### 12.1 Voice (Settings > Voice)

Section **Triggers**: switch "Long-press Fn for speech input".

Section **Transcription**: navigation row "Speech engine" with the current engine's label;
switch "Automatic punctuation"; switch "Block offensive words"; slider "Stop after silence" from
0 to 60 s in 5 s steps, labelled "Never: Fn or any key stops it" at 0 and "N s of silence"
otherwise; switch "Typing stops dictation" ("Any key except a modifier ends the session, keeps
the words on screen, then does its usual job. Hold Fn again stops it either way."); switch "Keep
speech on the phone" ("Use the engine's on-device recognizer: faster, works with no signal, and
it is the one that punctuates. Falls back online only when the language pack is missing. Private
mode always keeps speech on the phone."); switch "Pause music while dictating" ("Takes the audio
for the whole session, so a player pauses once when you start and resumes once when you stop.").

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

Settings search lists every Voice row under "Voice" and the two haptic rows under "Sound &
Haptics". Onboarding's essentials list shows "Hold Fn to talk (dictation)". The status bar
buttons screen places or removes the `microphone` slot (status-bar document).

## 13. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `fn_long_press_speech` | boolean | true | hold-Fn burst starts or stops dictation and Fn-origin events are consumed | Voice > Triggers | Long-press Fn for speech input |
| `fn_speech_scan_code` | int | 251 | scancode treated as the Fn key for the burst | none (preference only) | none |
| `dictation_engine` | string | empty | which recognizer: empty = system default, `ondevice`, or `package/class` | Voice > Transcription | Speech engine |
| `dictation_auto_punctuation` | boolean | true | asks the engine to punctuate and capitalise (Android 13+) | Voice > Transcription | Automatic punctuation |
| `dictation_mask_offensive` | boolean | false | per-request profanity masking | Voice > Transcription | Block offensive words |
| `dictation_stop_after_silence_ms` | int | 0; stored clamped to 0..60000 | the silence limit; 0 = the session runs until stopped (60 s safety) | Voice > Transcription | Stop after silence |
| `dictation_stop_on_typing` | boolean | true | any key other than a modifier ends the session before doing its work | Voice > Transcription | Typing stops dictation |
| `dictation_prefer_offline` | boolean | true | `EXTRA_PREFER_OFFLINE` on the request; one online fallback when the pack is missing | Voice > Transcription | Keep speech on the phone |
| `dictation_pause_media` | boolean | true | exclusive transient audio focus for the session | Voice > Transcription | Pause music while dictating |
| `dictation_haptics` | boolean | true | start and stop vibration cues (also gated by the system haptic toggle) | Sound & Haptics | Vibrate on dictation start/stop |
| `dictation_haptic_strength` | string | `strong` (`light`, `standard`, `strong`) | pulse lengths of the cues | Sound & Haptics (only while cues on) | Vibration strength |
| `sym_long_press_assistant` | boolean | false | 600 ms Sym hold opens the assistant | Voice > Voice assistant | Hold Sym for the assistant |
| `side_key_assistant` | boolean | false | records that the orange key's long press should point at PhysiBoard; the screen re-reads the real slot | Voice > Voice assistant | Orange key opens the assistant |
| `assistant_action` | string | `auto` (`voice_command`, `hands_free`, `assist`) | which request is tried first when opening the assistant | Voice > Voice assistant | How the assistant opens |
| `side_key_original_captured` | boolean | false | whether a restore point for the vendor slot exists | none | none |
| `side_key_original_package` | string | none | the vendor slot's package before PhysiBoard bound it | none | none |
| `side_key_original_activity` | string | none | the vendor slot's activity before PhysiBoard bound it | none | none |

Gone from 3.0: `dictation_end_silence_ms` (the 2.x pause; a 2.x value of 2000 would have become
a two-second auto-stop, the very cutoff, so the importer drops it) and
`dictation_continuous_session` (segmented mode is always asked for where it works).
`alt_ctrl_speech_shortcut` is accepted and ignored by the importer (keys document).

Read but owned elsewhere: `auto_capitalize_first_letter`, `auto_capitalize_after_period`,
`auto_capitalize_restricted_fields`, raw-mode app list (text-input document); `private_mode`
(app-shell document 31: forces the on-device recognizer); `screen_trackpad_enabled` and the
trackpad trigger key (trackpad document); the strip slots and `status_bar_visibility`
(status-bar document).

## 14. Device facts

| # | Fact | Evidence |
|---|---|---|
| D1 | Fn reaches apps only as auto-repeat Ctrl events with scancode 251, every ~50 ms starting ~400 ms into the hold; no initial down, no key-up, and a quick tap sends nothing. Five repeats is ~600 ms of hold. | docs/titan2elite/DEVICE.md "Fn event delivery model"; service comment on the burst |
| D2 | Google's speech engines close the microphone about 2 s after any sound and report "no match" when no words came through; on a Titan 2 in Teams speech began 10 ms after the microphone closed. | commit 0f8b040; PHYSIBOARD_CHANGES.md 2.0.7 |
| D3 | Google's system engine delivers the whole utterance as its last partial followed by an empty final. Seen in Teams on a Titan 2. | commit 0f8b040; PHYSIBOARD_CHANGES.md 2.0.7 |
| D4 | The Titan 2's `voice_recognition_service` is `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService` (Speech Services by Google, version googletts.google-speech-apk_20260817.01). Four recognition services are installed: it, Android System Intelligence (`com.google.android.as/...AiAiSpeechRecognitionService`), Home Assistant, and the Claude app. | `settings get secure voice_recognition_service`, `cmd package query-services -a android.speech.RecognitionService`, 2026-10-07 |
| D5 | Notification-class vibration is muted whenever the phone's notification vibration is off; the 1.0.4 cues were silent for that reason. | PHYSIBOARD_CHANGES.md 1.0.5; comment in the haptics source |
| D6 | The orange side key ("func1") never reaches an input method; the vendor launches the package/activity pair in `Settings.System` keys `func1_long_press_package` / `func1_long_press_activity`, gated by `func1_shortcut_key_enable`; stock points the long press at Gemini's entry activity. | docs/titan2elite/DEVICE.md "Vendor key-config"; PHYSIBOARD_CHANGES.md 1.0.7; side-key source comments |
| D7 | Google's engine beeps at each internal "no speech" ending of a plain request. | 2.0.7 session note |
| D8 | Sym is keycode 63, scancode 253. | docs/titan2elite/DEVICE.md keymap table |
| D9 | With no focused text field the keyboard receives no key events at all, so Fn-hold dictation (like Fn+Home) cannot fire in the camera or on a page without an editor. Structural, not per-app. | memory note "Fn keys need a focused editor" |
| D10 | The default touch-feedback amplitude is easy to miss with the phone on a desk or at arm's length; the cues run at amplitude 255 and are made "firmer" only by longer pulses. | commit a4a79c5; PHYSIBOARD_CHANGES.md 1.0.8 |
| D11 | The maintainer's real configuration, re-captured 2026-08-27 at app 1.2.3, has Fn-hold on, cues on, masking off, a 2000 ms pause, the chord off, and the side key bound. | first-run defaults comment in the settings source |
| D12 | Launching the assistant trampoline without its own task pulled PhysiBoard's task to the front and Android's starting window showed the app; fixed with an own task and a no-preview transparent theme. | commit ab68fde; PHYSIBOARD_CHANGES.md 1.0.8 |
| D13 | A system update or another app can rewrite the vendor slot without notice, leaving the stored flag on while the key does nothing. | commit 2a54273 |
| D14 | A PhysiBoard session on 2026-10-07 09:22: `RecognitionService#onStartListening` at 24.872, microphone open at 24.911 (+39 ms), the on-device engine's first audio buffer at 25.036 (+164 ms); no speech detected; `SODA stopped processing audio, mics audio processed in millis: 5000` and `NO_SPEECH_DETECTED` at 30.011; the keyboard's re-listen at 30.032 (+21 ms); the second `NO_SPEECH_DETECTED` at 35.183 and the recognizer destroyed at 35.200 (the first-words grace's 10 s exhausted: the "No speech input detected." toast). The engine's own `requestAudioFocus ... req=4` (transient exclusive) at each request and `abandonAudioFocus` at each end made Audible (`com.audible.application`, focus flags `PAUSES_ON_DUCKABLE_LOSS`) `handleLoss` and `handleGain` every five seconds. The same shape again at 09:26:58. | logcat 09:20 to 09:40, scratchpad `dictation-evidence.log` lines 307 to 875 and 3107 to 3671; `dumpsys audio` focus event log |
| D15 | On every PhysiBoard request Google's service logged `Key android.speech.extra.SEGMENTED_SESSION expected String but value was a java.lang.Long. The default value <null> was returned` and `Wrong value passed to EXTRA_SEGMENTED_SESSION; ignoring it`, and `EXTRA_ENABLE_FORMATTING can't be used when EXTRA_PREFER_OFFLINE is false`. No such warning for the complete-silence extra sent as a long. So segmented mode was never in force, every request was a plain one-shot, and punctuation was never asked for successfully. | the same log, lines 311 to 318 and 3111 to 3118 |
| D16 | The 2.x/first-3.0 engine, told it was segmented, treated the plain final as "the engine ignored the request", armed a pause + 5000 ms watchdog and issued no new request; the latch it set was cleared again at the next session because the recognizer object is rebuilt per session. Every session: first utterance lands, seven seconds of closed microphone, stop cue. | DictationEngine.handleFinalResult before this change; DictationController.ensureRecognizer clearing the latch |
| D17 | Chrome's Web Speech sessions in the same log (`callingApp: com.android.chrome`) ran up to 19 s of partials per request, ended on `onEndOfSpeech`, and were followed by Chrome's own `RecognitionService#onDestroy` (`CANCELLED`) and a new request within ~70 ms; Chrome sends Google's `android.speech.extra.DICTATION_MODE` boolean and restarts nothing itself. The CANCELLED lines in the log are Chrome's, not the keyboard's. | the same log, lines 878 to 3045; Chromium `SpeechRecognitionImpl.java` |
| D18 | `config_defaultOnDeviceSpeechRecognitionService` is the empty string in the Titan 2's `framework-res.apk` (Android 16, API 36), so `SpeechRecognizer.isOnDeviceRecognitionAvailable` is false and `createOnDeviceSpeechRecognizer` would throw. `AppsFilter` logged `brobata.physiboard.dev3 -> com.google.android.as BLOCKED` at each trigger: package visibility hid the Android System Intelligence recognizer from the keyboard. | `aapt2 dump resources` on the pulled framework-res.apk; log line 306 |
| D19 | The vendor shortcut layer in `system_server` (`A85ShortcutFunction`) sees both an `ACTION_DOWN` and an `ACTION_UP` for scancode 251 (keycode CTRL_LEFT), ~600 to 800 ms apart; neither reaches the input method. Back (scancode 158), Enter (28) and the letters deliver clean down and up pairs. | the same log, lines 305, 527, 3104, 3105 and the Back/Enter/letter events |
| D20 | A recognizer kept between sessions goes stale: Android unbinds the remote service while nothing is listening, and the next request reached a dead connection ("Connection to speech recognition service lost, but no #startListening has been invoked yet"). | the maintainer's Titan, 2026-09-26 |
| D21 | Google's segmented session reports the whole session's transcript so far in every partial and segment. | the maintainer's Titan, 2026-09-25 |
| D23 | 2026-10-07 17:09, Chrome tab: at every request `W/Bundle: Key android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS expected Integer but value was a java.lang.Long. The default value 0 was returned` (stack: `Intent.getIntExtra` from `GoogleTTSRecognitionService.onStartListening`) then `E/RecognitionServiceInten: EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS is not set with positive value; ignoring EXTRA_SEGMENTED_SESSION`; `applicationDomain: AMBIENT_ONESHOT` on every request; no `App op 27` line; the offline engine ran (`Offline recognizer`, en-US pack v3072); each request ended at the first ~1 s pause (`SODA session stopped due to: MIC_END_OF_DATA` after 4.3 and 5.4 s) and the next began ~40 ms later. The third request (29.515) ran 10 020 ms of audio with `onStartOfSpeech` at 31.833 and ended with `Final recognition has been created. Size: 0` and NO_SPEECH_DETECTED, with no partial ever reported; a Chrome field re-attach (`SHOW_SOFT_INPUT` + `ATTACH_NEW_INPUT`, 35.47) and the keyboard's own show request fell inside it, and nothing on the keyboard's side cancelled, stopped or restarted the request (its focus held from 19.524 to 42.046). The engine's `SodaDetectionHandler#connect: enableConcurrency: true`. | scratchpad `third.log` lines 28235 to 30755 |
| D22 | 2026-10-07 16:30, PersaLink (Chrome WebAPK, strip collapsed to a 0 by 0 window, `InputDispatcher ... info.frame: (2, 1200, 2, 1200)`): at each PhysiBoard request (16:30:27.548, 37.330, 48.977, 58.759) audioserver logged `App op 27 missing, silencing record AttributionSourceState{... packageName: com.google.android.tts ... next: ...}` within ~15 ms, the engine heard nothing, and `appops get brobata.physiboard.dev3 RECORD_AUDIO` showed `Uid mode: foreground` with a fresh `rejectTime`. `dumpsys input_method` read `mVisibleBound=false`, `dumpsys activity processes` `curProcState=16 curCapability=--------` with one IME connection (`!FG IMPB SLTA !VIS`, flags 0x40880005); later, with Chrome having shown the keyboard, `mVisibleBound=true`, `curProcState=5` and a second connection `FGS LACT UI CAPS` (flags 0x2c001001). The 16:28:43 session in Messages (Messages had sent `SHOW_SOFT_INPUT fromUser true`) was not silenced and reached `#onResults withSpeech: true`; the 16:31:11 session was Chrome's own Web Speech, not the keyboard's. | scratchpad `dictation-evidence-2.log`; the read-only `dumpsys`/`appops` queries of the same day; AOSP `InputMethodBindingController.IME_VISIBLE_BIND_FLAGS`, `InputMethodManagerService.showCurrentInputLocked`/`hideCurrentInputLocked`, `AppOpsUidStateTrackerImpl.evalModeInternal` |

## 15. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| Fn again before the microphone opened | session ends at once, no cues | a session exists from the trigger; a stop before the start cue has nothing to stop gracefully |
| Fn held with dictation already listening | the fifth repeat stops the session gracefully | start-or-stop |
| Key typed while a partial is on screen | the partial is committed as shown, the session ends, the key acts on the committed text | `dictation_stop_on_typing` |
| Enter pressed while dictating into a chat field | the words on screen are committed, the session ends, Enter sends | same |
| Backspace while a partial is on screen | the partial is committed, the session ends, one character is deleted | same; the user sees what the key acts on |
| Speech recognition unavailable on the device | toast "Speech recognition isn't available on this phone." | start failures are shown now |
| Permission denied permanently | every trigger flashes a translucent activity that returns DENIED; no message | no rationale screen; the denied broadcast only clears the pending flag |
| Engine answers a segmented request with a plain final | text committed; the next request is plain and issued at once; latch set for this engine | 6.3 |
| Engine changed in settings | the latch is cleared for the new engine id | the latch is per engine |
| Engine reports busy five times in a row | toast "Speech recognition error.", session ends | 6.6 rule 4 |
| Quiet errors every five seconds with nobody speaking | re-listened each time; session ends after the silence limit with no message | 6.2, 6.4 |
| "Stop after silence" 5 s, a 4 s pause between sentences | session continues | the limit counts from the last speech |
| Offline pack missing, private mode off | the same session goes online once, silently | 4.3 |
| Offline pack missing, private mode on | toast about the pack; session ends | private mode never goes online |
| Audible or another player with focus | pauses at the trigger, resumes at the stop cue | 6.7 |
| A phone call starts mid-dictation | the session ends, words on screen committed | permanent focus loss |
| Raw-mode app or password field | no capitalisation of partials or finals; spacing still applies | capitalisation is the only field-gated step |
| No input connection when a result arrives | the words are dropped silently and the session continues | writes skip when there is no connection |
| App blinks its field off and on while dictating | session continues | the 500 ms editor-gone grace is cancelled by the new field |
| User switches to another app mid-dictation | session ends at once with the stop cue | different owner package |
| Keyboard service destroyed mid-session | recognizer destroyed, focus given back, no stop cue | destroy path skips session-end bookkeeping |
| Notification vibration off, system haptic feedback on | cues play | plain vibration, no notification attributes (D5) |
| System haptic feedback off | no cues, whatever `dictation_haptics` says | effective value is gated on the system toggle |
| Status bar hidden | the system status bar's microphone icon is the only visual sign | 9 |
| Strip collapsed or hidden when the session starts | the keyboard is held visible for the system with nothing new on screen; released with a hide at the end | 6.8 |
| The app had asked for the keyboard during this field | the hold is a no-op for the system; nothing is hidden at the end | 6.8 |
| Session ends because the user moved to another field or app | nothing is hidden; the system decides the keyboard for the new field | 6.8 |
| A launcher key bound to "Dictation" pressed while listening | the key hook ends the session; the command is that same press and starts nothing | 2.2, 6.8 |
| The grant takes longer than 400 ms | the request goes out anyway and an error is logged; the engine's first five seconds may be silent | 6.8 |
| `ondevice` chosen on the Titan 2 | the row is not offered; a stored value falls to the system default | D18 |
| Language tag falls back to `en-US` | only when neither the subtype nor the device locale yields a language | 5.1 |

## 16. Test cases

Encoded as JVM tests in `core/speech` against the state machine, driven through a harness that
replays recognizer callbacks and applies the text ops to a model of a field with a composing
region (`DictationHarness`). Numbered here; the test names cite the row.

| # | Input | Expected |
|---|---|---|
| T1 | trigger with no session, stop-after-silence 10 s | `STARTING`; silence limit 10 000; engine silence 11 000; segmented request |
| T2 | ready | `LISTENING`; cue fallback armed at +300 ms |
| T3 | first audio | start cue; fallback cleared |
| T4 | trigger while `LISTENING` | `STOPPING`; `StopListening`; watchdog at +1500 ms; no silence deadline |
| T5 | trigger while `STOPPING` | session ends; `CancelListening` |
| T6 | final while `LISTENING`, segmented request | text committed; immediate plain re-listen; latch true |
| T7 | empty final while `STOPPING` with a partial | committed from the partial; session ends |
| T8 | segment | committed; no effects; last-speech updated |
| T9 | beginning of speech, partial | silence deadline moves with each |
| T10 | editor rejected the insert | partial cleared; session ends with cancel |
| T11 | any ending | exactly one `ReleaseAudioFocus`; stop cue only after a start cue |
| T12 | whitespace-only final with a partial | committed from the partial |
| T13 | trigger; every ending | `HoldImeVisible` is the first effect; every ending has exactly one `ReleaseImeVisible` |
| D14 | two 5 s quiet errors then speech | two silent re-listens, no message, words land, one start cue |
| D15 | plain final for a segmented request | committed; plain re-listen; latch; next sentence lands |
| D16 | server disconnected mid-partial | partial committed; stop cue; focus back; "Network error." |
| late | results after the session ended | ignored: no session, no effects, no ops |
| speech | 30 s of partials every 500 ms with every timer fired | no start, stop or cancel effect |
| stop | Fn, segment, end of segmented session | committed; ends with stop cue and focus back |
| watchdog | Fn, nothing from the engine for 1500 ms | partial committed; cancel; ends |
| quiet stop | Fn, then a quiet error | ends silently |
| key | key down with a partial | committed; cancel; ends; a late segment is ignored |
| key off | stop-on-typing off, key down, user edit, segment | session runs on; the segment writes nothing |
| focus | trigger | `AcquireAudioFocus` before `StartListening`; permanent loss ends the session; setting off touches no focus |
| cue | ready without audio | cue 300 ms later; never twice |
| cue 2 | start failed before audio | no stop cue; focus back; message |
| early Fn | trigger twice before ready | second ends the session; one request issued |
| silence | stop-after-silence 5 s, last segment at 3 s | stop at 8 s; a quiet answer ends silently |
| never | stop-after-silence 0, quiet errors for 50 s | no stop until 60 s; a 10-minute session ends |
| end | end of segmented session with a partial, `LISTENING` | committed; re-listened |
| pack | language unavailable, private off | one online re-listen, no message; a second ends with the pack message; private mode ends at once with its message |
| refusal | client error at 900 ms | plain re-listen, latch; at 1300 ms a real error |
| fast | quiet errors within 700 ms | 500 ms backoff each; the fifth ends with "Speech recognition error." |
| busy | busy at 100 ms | retry at 400 ms |
| editor | field closed, same-app field at +200 ms | continues; closed with no replacement ends at +500 ms with cancel; another app's field ends at once |

Plus the text tests kept from before (capitalisation, spacing, the frozen context, the session
echo, direct commit, deleted words never typed back) and the cue table.

## 17. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| Hold-Fn trigger with the burst rule | keep | the signature trigger; only sane way given D1 and D19 |
| "Hold to start, guess the end" | drop | the guess was every cutoff the maintainer reported; D14 to D16 |
| Explicit stop: Fn again, any key, silence limit, session cap | new | the toggle-microphone model the maintainer asked for |
| First-words grace (10 s, 5 re-listens) | drop | it was the "No speech input detected." toast; quiet errors re-listen without a cap now |
| Restart-loop silence timer (pause − 1000) | drop | it ended sessions on a natural breath |
| Segmented mode with watchdog and refusal latch | keep, fixed | the extra was sent with the wrong type (D15); the watchdog is gone, an ignored request re-listens at once |
| `dictation_continuous_session` | drop | segmented mode is always asked for where it works |
| `dictation_end_silence_ms` | drop | replaced by `dictation_stop_after_silence_ms`, default never |
| Prefer offline | new | faster, private, and the only path that punctuates on this phone (D15) |
| Session-long audio focus | new | one pause and one resume per session instead of one per request (D14) |
| Typing stops dictation | new | the stop that needs no second thought |
| Start cue at ready | drop | tied to the open microphone instead (first audio report) |
| Status bar icon while listening | new | the strip may be hidden; the icon needs no permission |
| The keyboard held visible for the system during a session | new | the only way an input method gets the microphone capability (D22) |
| "Dictation" catalog command | new | a second trigger for any bindable key; the same action as the Fn burst |
| Fresh recognizer per session | keep | D20 |
| Session echo stripping | keep | D21 |
| Partials as composing text with the cursor after | keep | D3 |
| Direct commit for untrusted fields | keep | the web terminal |
| Frozen utterance context | keep | the lost-capital and leading-space bugs |
| Italian punctuation words | drop | the engine punctuates |
| Alt+Ctrl chord, keycode 667 | drop | keys document |
| Engine picker | keep | plus the `<queries>` declaration (D18) |
| Microphone permission trampoline activity and broadcasts | keep | a keyboard still cannot ask directly |
| Hold Sym, orange key, assistant action picker, "Voice assistant" command | keep | |
| Toasts for engine errors | keep | and start failures are toasts now |
| First-run defaults for this subsystem | keep | Fn-hold on, cues on, masking off; no pause; do not set `side_key_assistant` without binding |

## 18. Provenance

- core/speech/src/main/kotlin/brobata/physiboard/core/speech/DictationEngine.kt and its
  companions (settings, session, events, effects, timing, error codes, text)
- core/speech/src/test/kotlin/brobata/physiboard/core/speech/LoggedRecognizerScenariosTest.kt,
  DictationEngineLifecycleTest.kt, DictationEngineRequiredScenariosTest.kt, DictationEndingsTest.kt
- ime/src/main/kotlin/brobata/physiboard/ime/DictationController.kt (recognizer, request, audio
  focus, cues, permission)
- ime/src/main/kotlin/brobata/physiboard/ime/KeyboardSession.kt (the Fn burst command, the key
  hook, the status icon, private mode)
- ime/src/main/AndroidManifest.xml (`<queries>` for recognition services)
- app/src/main/kotlin/brobata/physiboard/app/settings/ui/screens/VoiceScreen.kt
- the phone log of 2026-10-07 09:20 to 09:40 and the read-only `adb` queries of the same day
  (D4, D14 to D19)
- Android reference: `RecognizerIntent` (EXTRA_SEGMENTED_SESSION, EXTRA_PREFER_OFFLINE,
  EXTRA_ENABLE_FORMATTING, the silence-length extras), `SpeechRecognizer`
  (`isOnDeviceRecognitionAvailable` reads `config_defaultOnDeviceSpeechRecognitionService`;
  `stopListening`; the error codes), `RecognitionListener` (`onSegmentResults`,
  `onEndOfSegmentedSession`, `onRmsChanged` "no guarantee that this method will be called"),
  `AudioManager` (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`)
- the 2.x documents and commits listed in the previous revision of this file, for sections 10
  to 12 and the D1 to D13 facts
