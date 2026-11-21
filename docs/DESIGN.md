# Notable UI design

Rules for the app's chrome (library, new notebook, editor toolbar and every
screen built from `ui/theme`). They follow the magnificent-ui skill, adapted
to an e-ink tablet. Code uses the tokens in `ui/theme/Tokens.kt` and the
primitives in `ui/theme/Primitives.kt`; a raw color, dp or sp in a screen is
a defect.

## Direction

- Product: a handwriting notebook for Boox e-ink tablets, used with a pen for
  hours at a time.
- Character: stationery, printed, plain-spoken.
- World: a paper notebook and its printed index: black ink on white paper,
  hairline rules, labels set like a spine label, nothing that a printer could
  not print in one ink.
- Moments: solid ink is spent on two things only: the selected tool in the
  toolbar and the one primary action of a screen.
- Signature: every notebook card carries a spine label under its preview:
  the title, and the page count set as a folio number in tabular figures on
  the right.

Mode: Operate everywhere. Posture: the editor is sovereign (the toolbar is
dense and glyph-first); the library is transient (large targets, labelled
actions).

## E-ink exceptions to the skill

The skill's rules hold except where the display makes them wrong:

- Pure white and pure black. The paper is the screen's white and the ink is
  its black; tinted neutrals render as dither and ghosting. `paper` is
  #FFFFFF and `ink` is #000000.
- No gray fills. Areas of gray dither and ghost on e-ink. Gray is used only
  for text (`ink2`) and hairline rules (`rule`). States that need a fill use
  solid ink with paper-colored content.
- No dark theme. Boox inverts the whole display system-wide when the user
  wants it; the app always draws paper and ink.
- No motion. Every transition is instant: e-ink refresh makes animation
  smear. Press feedback is an instant ink inversion while the finger or pen
  is down (`pressInverts`), which is still feedback within 100 ms.
- No hover and no tooltips. Touch and pen only, so recurring actions in the
  library keep their word next to the glyph; the editor toolbar is glyph-only
  because it is used hundreds of times a session and its glyphs are the
  conventional pen, eraser, lasso and undo shapes.
- Square corners. Radius is 0 everywhere: a printed rule has no radius.

## Tokens

Spacing (dp): 4, 8, 12, 16, 24, 32, 48. Page margin `margin` = 24; every
region's inline padding is `margin`, so the top bar title, section headings,
rows and grid share one left keyline and one right keyline. Gap between
sibling items `gap` = 16; gap between sections `section` = 32, with more
space above a heading than below it.

Type (sp, line height): `meta` 13/18, `body` 16/24, `label` 16/24 weight 500,
`section` 13/16 weight 600 uppercase +0.06em (the one label role: section
headings only), `title` 22/28 weight 500. Nothing below 13 sp: e-ink renders
thin small text poorly. Numbers that align or count use tabular figures.

Color roles: `paper` (background, text on ink), `ink` (text, icons, control
borders, selected and primary fills), `ink2` #4A4A4A (secondary text, 8.9:1),
`rule` #9A9A9A (dividers between regions, preview frames).

Sizes: touch target `target` = 48 (every control); toolbar button = 48;
top bar height = 64; icon 24 in controls, 20 inline with text; hairline 1.

Components (all in `Primitives.kt`): `InkButton` (primary: ink fill; secondary:
ink border), `InkIconButton` (48 square, optional selected ink fill),
`InkTopBar` (back/title on the leading keyline, actions on the trailing one,
bottom rule), `InkSectionHeader`, `InkRule`, `InkTextField`.

Icons: Feather (one stroke family) for every glyph in scope; Material icons
only where Feather has no glyph for the concept.
