# UI_SPEC.md

Source of truth: the "Sticker app: UI designs" artifact.
- Page **Kisscut**: the target look. Dark screens in the top row, light screens directly below. Boards: Look (design language), Keyboard setup, Library, Inside a collection, Keyboard in a chat, Save with nested picker.
- Page **Skeletal**: the plain reference version of every screen.

All eight screens now exist in Kisscut, dark and light. Skeletal's Collections list and Settings were also updated (nested tree, theme row), so the Skeletal page matches this spec.

Boards on the Kisscut page for the three most recent screens: "Collections, nested", "Select mode", "Settings" (each with a light twin).

Screen-specific notes from those designs:
- **Collections list:** each row is a Surface card with up to three overlapping mini stickers, name (Bricolage 17/700), total count including subcollections, and a chevron button (48dp) to expand or collapse. Children indent with a 2dp Line-colored rail on the left; depth 3 is the maximum. An expanded top-level row uses Raised fill. System rows: Favorites (heart on pink tint) and Unsorted (tray icon) have no chevron. A dashed "New collection" row ends the list; a lime "New" pill sits in the header.
- **Select mode:** the Library without the hero tile. Every tile shows a selection circle top-left: empty ring when off, lime filled check when on, plus a 3dp lime inset ring on the tile. The floating nav is replaced by an action bar: lime "Move" pill, outlined "Favorite", outlined trash icon button in the danger color (#FF8A7A dark, #B3261E light). Count in the title ("3 selected"), "Select all" at the right, close button at the left.
- **Settings:** keyboard status card (lime tint, tappable, leads to keyboard setup), a Theme segmented control (System, Dark, Light; selected segment uses the inverted fill), grouped rows with violet icon tiles for Backup and sharing, switches with a lime track and Ink knob, and a storage row. The nav shows Settings as the active lime pill.
- Light-mode substitutions for these: violet icons use #5B4BD6, selection ring and check badge use Olive (#4D6B00) with a white check.

## Look: "Kisscut"

Dark, sticker-first. Every transparent sticker gets a white cut line and a soft shadow, like a real sticker. Color lives in the stickers; the interface stays quiet.

## Tokens

Define once in `Theme.kt` as a Material 3 color scheme plus an extras object. Do not hard-code hex values in screens.

### Dark
| Token | Hex | Use |
|---|---|---|
| Ink | #0F0E15 | background |
| Surface | #181722 | cards, sheets |
| Raised | #211F2E | chips, nav bar |
| Line | #2D2B3D | borders |
| Text | #F5F4FB | primary text |
| Muted | #A9A7BC | secondary text |
| Lime | #C8F23D | primary and active; text on lime is Ink |
| Violet | #9B8CFF | accent |

### Light
| Token | Hex | Use |
|---|---|---|
| Paper | #F7F6FB | background |
| Surface | #FFFFFF | cards, sheets |
| Raised | #ECEAF4 | chips, nav bar |
| Line | #DDDAEA | borders |
| Ink | #14131C | primary text |
| Muted | #5E5C72 | secondary text |
| Lime | #C8F23D | button and active pill fill, with Ink text on it |
| Olive | #4D6B00 | lime used as text or icon |
| Violet | #9B8CFF | accent |

Other constants: sticker eye/mouth ink `#14131C`. Collection tiles use soft tinted fills taken from the designs (flat colors, no gradients). Light mode uses pastel tints and a stronger sticker shadow (about 25% black versus 40% in dark; match the designs).

Theme: follow the system setting by default; Settings can force dark or light. One color scheme swapped, not two sets of screens.

### Type
- **Bricolage Grotesque** (700, 800): titles and buttons.
- **Figtree** (500, 600, 700): body and labels.
- Bundle both as font resources (do not use downloadable fonts; offline only).
- Sizes: Title 36 (800, letter-spacing about -1), Heading 18 (700), Body 15 (600), Label 12 (700).

### Shape
Corner radii: 20 (tiles), 26 (cards), 32 (sheets, top corners), pill (buttons, chips, nav). Primary button is a 44dp-high pill; chips 36dp.

### Parts
- **Primary button:** Lime fill, Ink text, pill, Bricolage 15/800.
- **Selected chip:** Text-colored fill with Ink text; **Chip:** transparent, Line border, Muted text.
- **Floating pill nav bar:** Raised fill, floats above content with a short fade behind it (the only gradient allowed, purely functional).
- **Sticker tile:** see below.

## Sticker rendering

| Image | Look |
|---|---|
| Transparent PNG or WebP (`hasAlpha`) | White cut line about 3dp around the shape plus a soft shadow offset down. Generate the outline once at import into the thumbnail (cache it); never compute per frame. |
| Opaque image | Rounded tile (radius 20), no cut line |
| Animated (GIF, animated WebP) | Plain rounded tile with a small "GIF" badge bottom-left; plays only while visible and only if animation is enabled |

If building the true cut line proves too slow or ugly, fall back to a white 3dp rounded-shape border and tell the user.

## Screens

1. **Keyboard setup (onboarding):** three steps (turn on, choose, add stickers) with one main button for the current step: open the system keyboard settings, show the keyboard picker, or add stickers. Step states follow the system live (it watches the enabled and default keyboard settings; the picker is a dialog, so a resume check alone misses it). Shown at launch until Skip or Done, never after a share; reachable later from the keyboard card in Settings.
2. **Library (main):** top title, search is not in v1; filter chips (All, Favorites, Recents, then collections), sticker grid with a spanning hero tile at the top for the latest or a favorite, floating pill nav (Library, Collections, Settings).
3. **Select mode:** long-press enters; top bar shows count; actions: move, favorite, delete.
4. **Collections list:** nested list with expand/collapse, counts (total including descendants), add collection, long-press for rename, move, delete.
5. **Inside a collection:** breadcrumb of parents, child collections as tiles first, then stickers.
6. **Save (from share sheet):** bottom sheet with preview, a nested collection picker (tree with disclosure), "New collection", Save button. Handles multiple incoming images. While saving, the button reads "Saving n of m"; while a large GIF is shrunk it reads "Saving n of m, 40%" (n is the item being saved).
7. **Keyboard in a chat:** see KEYBOARD.md. Panel height about 40% of the screen; top row of tabs (Recents and Favorites as icons, top-level collections, then Unsorted while it has stickers); a chip row with the selected collection's subcollections (All first, hidden when there are none); a 5-column grid on phones; bottom row with an ABC key (back to the letters; hold for the keyboard list), an All/Static/GIF switch and Backspace (settled with the user in M4; the board's globe moved to the letters in M4.2). The typing keyboard (M4.2, no board) uses the same panel height and tokens: keys on Raised, special keys on Line, Enter on Lime.
8. **Settings, backup and sharing:** theme, animation toggle, export all, export selected collection, import pack, keyboard setup shortcut, about.

## Navigation

Single-activity, Navigation Compose. Top-level destinations: Library, Collections, Settings. Collection detail and Save are pushed or shown as a sheet. The Save screen is also the entry for `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intents with `image/*`.

## Interaction and accessibility

- Min touch target 48dp. Content descriptions on all icon buttons and stickers (use "Sticker" plus collection name when no better label exists).
- Support font scaling to 200% without clipping in rows and buttons.
- Respect the system "remove animations" setting: static first frame.
- No emoji in UI text. Icons from Material Icons (outlined).
- Empty states: Library empty shows a short explanation and an "Add stickers" action; empty collection shows "Nothing here yet".

## Performance rules (UI)

- Lazy grids with stable keys (sticker id) and fixed cell sizes.
- Load thumbnails in lists; load the original only for animated stickers while visible.
- Never decode full images on the main thread.

## Checking your work

After each UI task: build, install, take a screenshot with `adb exec-out screencap`, save it in `artifacts/`, compare against the matching board in the artifact, and list differences in your task notes. Test both light and dark (`adb shell cmd uimode night yes|no`).
