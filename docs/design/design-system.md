# PhysiBoard design system

One design language for everything PhysiBoard draws: the settings app, the keyboard's panels,
the launcher icon, the splash, the notification and tile icons. It is a terminal on a phone made
for a physical keyboard: mono type for the machine's voice, hairline panes, barely rounded keys,
one warm accent on navy. Timeless means few decisions, made once, applied everywhere.

Where the numbers live:

| What | Where |
|---|---|
| Colours, spacing, corners, borders, type sizes, opacities, motion | `design/src/main/kotlin/brobata/physiboard/design/DesignTokens.kt` (pure Kotlin, no Android imports; contrast pinned by `DesignTokensTest`) |
| The two typefaces | `design/src/main/res/font` (SIL OFL 1.1, LICENSING.md) and `PhysiFonts` for View code |
| The icon family and the launcher mark | `design/src/main/res/drawable/pb_*` |
| The motion helpers | `DesignMotion` (springs, press, reduced motion) |
| The settings app's Material theme | `app/.../settings/ui/PhysiBoardTheme.kt`, reads the tokens (app-shell.md SS22.1) |
| The keyboard's panel skin | `ime/.../skin/PanelSkin.kt` (app-shell.md SS22.7) |

`:design` is an Android library that both `:app` and `:ime` (and `:device:privileged`, for its
icons) depend on. Nothing in `core/*` depends on it; a `core` module that needs a number the
design system also names (the Sym key's corner, `SymGridGeometry.CORNER_DP`) holds its own
constant and says which token it mirrors.

## 1. Colour

### 1.1 The palette

| Name | Hex | Used as |
|---|---|---|
| Ink | `#0F172A` | dark page, ink on an amber fill, the launcher background |
| Pane (dark) | `#111B2E` | a dark pane, one step above Ink |
| Slate | `#1E293B` | dark raised surface, the launcher keycap's face |
| Slate raised | `#334155` | the launcher keycap's skirt |
| Signal Amber | `#F59E0B` | the accent on dark, the prompt on the launcher mark |
| Amber Deep | `#B45309` | the accent on light (Signal Amber is about 2:1 on white) |
| Sky | `#38BDF8` | secondary; the DEV pill on the sideload icon |
| Cloud | `#F1F5F9` | light page |
| White | `#FFFFFF` | a light pane |
| Slate 400 | `#94A3B8` | muted text on dark |
| Slate 550 | `#5B6B80` | muted text on light |
| Pane border | `#2A3A52` dark, `#CBD5E1` light | every 1 dp hairline |
| Comment | `#B8925A` dark, `#7A5C2E` light | `# section labels` |
| Error | `#EF4444` dark, `#DC2626` light | destructive rows, failures |

### 1.2 Roles

Paint with a role, never a name. `DesignTokens.DARK` and `DesignTokens.LIGHT`:

| Role | Dark | Light |
|---|---|---|
| page | Ink | Cloud |
| pane | Pane (dark) | White |
| raised | Slate | White |
| border | `#2A3A52` | `#CBD5E1` |
| text | Cloud | Ink |
| muted | Slate 400 | Slate 550 |
| accent | Signal Amber | Amber Deep |
| onAccent | Ink | White |
| comment | `#B8925A` | `#7A5C2E` |
| error | `#EF4444` | `#DC2626` |

### 1.3 Contrast

Every text role meets WCAG AA (4.5:1) on both the page and a pane, in both themes, and the ink
on an accent fill meets it too. A test fails the build otherwise. Measured: muted 7.0 / 5.0,
comment 6.2 / 5.6, accent 8.3 / 4.6 on the page (5.0 on white), selected chip text 8.3 / 5.0.
Borders are decoration: they are never the only thing that tells two states apart.

### 1.4 The keyboard theme

The keyboard's panels are painted in the user's keyboard theme, not in these roles
(status-bar.md SS9.4): Background, Keys, Buttons, Key outlines, Text and icons, Accent. The
"Terminal (Amber)" preset is the design palette. Whatever the theme, the panel skin keeps the
same structure: muted text is the theme's text at 72 %, a press is the theme's accent at 25 %
over the key, a selection the accent at 20 % with an accent outline. Surfaces that have no
keyboard theme (the trackpad hint, the expansion popup, the quick launcher) use the roles above
for the system's light or dark mode.

## 2. Type

Two families, both vendored, both SIL OFL 1.1:

- **JetBrains Mono** is the machine's voice: titles, prompts, labels, key letters, tabs, chips,
  buttons, fields, values, codes. Regular, Medium, Bold.
- **Inter** is for what a person reads through: descriptions, dialog bodies, clip previews,
  status lines; and for a character shown as itself (Sym page characters, symbol and kaomoji
  cells, accent tiles), where a monospace cell would sit it oddly. Regular, Medium, SemiBold.
- Emoji draw in the system's emoji font. Never force a typeface on an emoji.

### 2.1 Scale

Settings (Material roles, app-shell.md SS22.1): display 48/40/32, headline 28/24/19, title
18/15/13, body large 14 mono medium, body medium 14 Inter, body small 13 Inter, label 14/12/11
mono medium, plus prompt (mono bold 18), section label and value (mono medium 13), code (mono
12), reading (Inter 16/23), glyph (Inter 22/28).

Panels (`DesignTokens.Type`):

| Token | Size | Face | Use |
|---|---|---|---|
| `KEY_LETTER_SP` | 10 sp | mono medium, 72 % | the letter on a Sym key, the digit and key under a chooser form (11 sp) |
| `BADGE_SP` | 11 sp | mono bold | the caret badge |
| `LABEL_SP` | 13 sp | mono medium | panel titles, tabs, chips, buttons, the trackpad hint |
| `BODY_SP` | 14 sp | mono / Inter | fields, chooser rows, status lines |
| `READING_SP` | 13 sp | Inter | longer text |
| `GLYPH_SP` | 22 sp | Inter | a character shown as itself |
| `CODE_SP` | 20 sp | mono bold, +0.08 em | one-time codes |

A screen or a panel uses a token, never a size of its own. Text never goes below 9 sp.

### 2.2 Loading

The keyboard loads both faces off the main thread when its service starts
(`PhysiFonts.prewarm`), so no key press ever waits on the disk; a face not ready yet is loaded
on first use and cached; a face that fails falls back to the system's monospace or sans.

## 3. Space, shape and borders

- **Spacing** steps: 4, 8, 12, 16, 24 dp. The side margin is 16 dp. Gaps between keys are 4 dp.
- **Corners**: 2 dp fields, 4 dp keys / chips / buttons / tabs, 6 dp
  panes / cards / clips / GIF tiles / popups, 8 dp dialogs and sheets. Nothing is pill-shaped
  except the switch (a pill reads on/off at a glance) and the DEV badge.
- **Borders**: one 1 dp hairline, in the border role (Key outlines on the keyboard). A pane is a
  fill and a hairline, never a shadow. The one accent-bordered pane on a screen is the one that
  wants attention.
- **Touch**: 48 dp minimum target in settings; keyboard keys follow the Sym grid's geometry
  (56 dp tall). The close button is 36 by 32 dp on every panel.
- **The Titan's corners**: a full-width panel on the bottom edge pads away from the display's
  rounded corners in its own background (status-bar.md SS4, `BottomOverlay`).

## 4. Iconography

- One family, drawn here, not borrowed: 24 dp grid, 1.75 dp strokes, round caps and joins,
  drawn white and tinted at the point of use. `pb_ic_close`, `pb_ic_search`, `pb_ic_edit`,
  `pb_ic_globe`, `pb_ic_chevron_down`.
- Small system icons (notifications, Quick Settings) are filled silhouettes, because the system
  draws them as an alpha mask: `pb_ic_mark` (the mark), `pb_ic_backlight` (keycap with light
  rising off it), `pb_ic_ring` (a dot inside a ring).
- The settings app's row icons are Material outlined icons in a 36 dp keycap; that is the one
  place a borrowed set is used, and always inside the keycap.
- An icon that does something has a content description that says what ("Close", "Search
  symbols", "Edit this page", "Switch keyboard"). An icon beside a label that says the same is
  hidden from TalkBack.
- No emoji as UI chrome. Emoji are content (a category tab, a picker cell), never a button's icon.

### 4.1 Identity

The mark is a Titan keycap seen face on, with a terminal prompt `>_` in Signal Amber on its
face: a physical keyboard that is also a terminal. Adaptive launcher icon: flat Ink background,
the cap (skirt Slate raised, face Slate) and the prompt in the foreground, everything inside the
66 dp safe zone. Monochrome layer for themed icons: the bezel silhouette with the prompt in it.
The sideload build adds a Sky DEV pill under a lifted cap and is labelled "PhysiBoard Dev". The
splash is the mark on an Ink disc on the page colour (app-shell.md SS22.6).

## 5. Motion

Motion says where a thing came from and that a touch landed. It never delays input.

| Moment | Motion | Numbers |
|---|---|---|
| A panel opens | rises into place on a spring while fading in | 28 dp, stiffness 600, damping ratio 0.86 (no visible bounce); fade 140 ms decelerate |
| A panel closes | drops and fades, ignoring touches from the first frame | 16 dp, 140 ms accelerate; the window is removed at the end |
| A panel is replaced in place | fades in | 110 ms (the Sym key stepping pages, a reopened accent bar, a redrawn Fill page) |
| A key, chip, card or cell is pressed | dips, takes an accent wash and outline; springs back on release | 94 % in 60 ms; release stiffness 900, damping 0.6; the wash fades out over 120 ms |
| A switch flips (settings) | thumb slides | 180 ms ease |
| The home cursor | breathes | 1.2 s each way, 100 % to 25 % |
| A screen opens (settings) | slides in from the right, the old one drifts a fifth left | app-shell.md SS22.2 |

Surfaces redrawn on every keystroke (the expansion popup) never animate. The quick launcher keeps
its own slide (expansion-clipboard-pickers-launcher.md SS7.1).

**Reduced motion.** With the system's animator duration scale at 0 (Remove animations, or a
battery saver that sets it), nothing moves: panels appear and go in place, presses do not dip,
the cursor holds still. Check `DesignMotion.reduced()` (Views) or `rememberReducedMotion()`
(Compose); never a preference of our own.

## 6. Haptics

Haptics follow the spec's haptic language (the section being added to the spec alongside this
document; today's settings are settings-catalog.md SS9.4, "Sound & Haptics"). The design rules
that apply to every surface here:

- A haptic confirms something the user did with a key or a touch; it never announces something
  the app did on its own.
- One tick per action. A panel opening by key press does not add a second tick of its own.
- Haptics respect the system's touch-feedback setting and the user's own switch; motion and
  haptics are independent (reduced motion does not silence haptics).

## 7. Copy

- **Plain, short, sentence case.** "Pick PhysiBoard from the input switcher", not "Select
  PhysiBoard As Your Input Method".
- **Say what happens, then why.** "Pair once to keep the keys lit in the dark."
- **The terminal voice is in the chrome, not the sentences.** Titles are prompts
  (`physiboard:~/theme$`), section and panel titles are comments (`# clipboard history`),
  search fields end in `_`; the descriptions under them are ordinary English.
- **Numbers and keys as they are printed.** "press its letter", "Alt+W", "4 min ago".
- **No exclamation marks, no "Oops", no blame.** "GIF search isn't set up in this build", not
  "Error!".
- **Private and offline states say so in one line** (app-shell.md SS31.4).

## 8. Panels, put together

Every keyboard panel (app-shell.md SS22.7) is the same few parts:

1. The theme's Background with a 1 dp Key-outline rule along the top.
2. A title, when it has one, as a comment: `#` in the accent, the words lower-cased.
3. Keys: Keys fill, 1 dp Key-outline stroke, 4 dp corners; a mono letter at the top left when a
   key is typed by a hardware key; the character in Inter, centred.
4. Chrome keys (pencil, globe, search, mode, close): Buttons fill, the shared icons.
5. A search field as a prompt, outlined in the accent while it takes keys.
6. The close button at the end of the title row, or the bottom end corner of a grid.
7. The open and close motion of section 5.

## 9. Do and don't

| Do | Don't |
|---|---|
| Take a colour from a role or the keyboard theme | Write a hex value in a screen or a panel |
| Use a type token | Set a text size inline |
| One accent per screen, for the thing to act on | Amber everywhere; a second accent hue |
| A hairline pane | A drop shadow to separate a card |
| 4 dp keys, 6 dp panes | 12 dp "friendly" corners, pills for buttons |
| The shared close icon | "✕", "×", "X" or an emoji as a close button |
| Mono for what the machine says, Inter for what a person reads | A paragraph in mono; a key letter in Inter |
| Lower-case comment titles: `# clipboard history` | `CLIPBOARD HISTORY:` or "Clipboard History" in a panel header |
| Spring in, ease out, fade on swap | Animate a surface that redraws on every keystroke |
| Respect reduced motion | Our own "animations" switch |
| Tell TalkBack what an icon does | An unlabeled icon button |
