# ROADMAP.md

Work top to bottom. Tick boxes as you finish. Do not start a milestone until the previous one's done criteria pass. Every milestone ends with: `gradlew test` green and notes added below it.

Open decisions block nothing unless stated; use the defaults in `docs/ARCHITECTURE.md` and ask the user when a milestone needs one.

## M0: Project skeleton
- [x] Gradle project with modules `shared:db`, `shared:core`, `ui:android`; version catalog; Gradle wrapper
- [x] Empty app launches to a blank Compose screen on the emulator
- [x] Theme.kt with dark and light tokens and bundled fonts (UI_SPEC)
- [x] `scripts/` helpers from SETUP.md section 10
- [x] `.gitignore` (build, `local.properties`, `artifacts/`, `.gradle`)

Done when: `assembleDebug` and `installDebug` succeed and a screenshot of the themed blank screen exists in both modes.

## M1: Data layer
- [x] Entities, DAOs, database with `BundledSQLiteDriver` (DATA_MODEL)
- [x] `CollectionRepository` with depth cap, cycle check, both delete modes
- [x] `StickerRepository` with dedupe by hash
- [x] `FileStore` interface and Android implementation
- [x] Schema export committed; first migration test harness
- [x] Tests for everything under "Database and repositories" in TESTING

Done when: all those tests pass.

## M2: Import a sticker (core path)
- [x] Type sniffing, thumbnail generation, `hasAlpha`, `isAnimated`
- [x] Share-sheet intent (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `image/*`) opens the Save sheet
- [x] Save sheet with nested collection picker and "New collection"
- [x] In-app "Add stickers" using the system photo picker / SAF

Done when: sharing an image from the emulator's browser or gallery saves it; duplicate share is a no-op; test coverage per TESTING.

## M3: Library and collections UI
- [x] Library screen (chips, grid, hero tile, floating nav) with true sticker look (cut line for transparent, tile for opaque, GIF badge)
- [x] Collections list (nested, counts) and Inside-a-collection screen with breadcrumb
- [x] Select mode: move, favorite, delete
- [x] Empty states and accessibility pass
- [x] Dark and light verified against the artifact with screenshots

Done when: UI tests pass and screenshots match the designs; differences listed.

## M4: Keyboard
- [x] `StickerImeService`, panel view, tabs, grid, globe key (KEYBOARD)
- [x] Insert via commit-content with clipboard fallback and hints
- [x] `markUsed` on success; Recents works
- [x] Keyboard setup onboarding screen (enable, select, state tracking)
- [x] `ImeTestActivity` and keyboard tests

Done when: on the emulator a sticker is inserted into `ImeTestActivity` via the keyboard and tests pass.

## M4.2: Typing keyboard
Added after M4 at the user's request: the keyboard must also type (KEYBOARD, Typing layout).
- [x] Letters, `?123` and `=\<` pages with Shift, caps lock, auto-capitals, Backspace repeat, Enter action
- [x] Sticker key and ABC key swap between letters and the sticker panel; last view remembered
- [x] Field rules: number and phone fields open on numbers, password fields on letters with stickers off
- [x] Setup screen privacy text updated (types, never stores what is typed)
- [x] Tests: layout and shift logic on the JVM; typing into `ImeTestActivity` fields on the device

Done when: on the emulator, typing "Hi there" plus a sticker into `ImeTestActivity` works, Enter sends in a field with a Send action, and tests pass. The user tries it in Discord on their phone.

## M4.5: Smaller storage and file types
First, check the send rule (send the stored type if the field accepts it, else a PNG copy, else the clipboard):
- [x] Debug-only log in the keyboard of the MIME types each field accepts (`src/debug`, nothing in release)
- [x] The user taps the text box in WhatsApp, Discord, Messenger, Google Messages, Telegram on their phone; record each list in KEYBOARD's app table and check the rule fits before changing the stored format

Still images:
- [x] Accept JPEG; still images stored at most 512px as lossless WebP (PNG on API 26-28), never bigger than the input (IMPORT_EXPORT, Stored format)
- [x] Hash the bytes as received so duplicates still match; existing stickers untouched
- [x] Keyboard clipboard fallback copies still stickers as a PNG copy (same pixels, `cache/send/`), like the direct send; GIFs stay GIF (KEYBOARD, Inserting a sticker)

Large GIFs (output stays GIF so every chat app accepts it; near-lossless, as GIF allows 256 colors per frame; no new library):
- [x] Pure Kotlin GIF reader in `shared:core` commonMain (frames, disposal, transparency, interlace, local palettes), with safety limits for broken or hostile files
- [x] Shrink GIFs over 512px on the longest side: composite each frame, scale, re-pick 256 colors per frame without flicker, keep 1-bit transparency clean
- [x] GIF writer: real LZW, store only the changed area per frame, keep delays and loop count
- [x] Wire into import: only GIFs over 512px, never bigger than the input, one frame at a time in memory, progress for long GIFs
- [x] Before reusing any existing code (for example AnimatedGifEncoder / NeuQuant), check its license with the user

WhatsApp stickers (last, after the still and GIF parts; the user asked for it):
- [x] Probe first on the user's phone: does WhatsApp show our sticker as a sticker? Yes without the new format: WhatsApp asks whether to send the PNG copy as a sticker, and it then shows small and frameless (2026-11-22)
- [x] ~~Send as `image/webp.wasticker`~~: not needed (user's call). WhatsApp's own prompt already makes it a sticker; sending the sticker type would only skip that prompt. Moved to Later.

Tests per TESTING (Import / export, Stored format), plus GIF cases: odd disposal and palettes, malformed files, timing and loop preserved, "never bigger", a set of real-world GIFs. GIFs at 512px or smaller and animated WebP are stored unchanged.

Done when: a 3000px PNG saves as a 512px lossless WebP with identical pixels to a 512px scale of the source; a JPEG saves; a 1000px GIF saves at 512px as a GIF that plays with the same timing and looks the same on the emulator; small GIFs and animated WebP are unchanged; tests pass.

## M4.6: Library sections
Added at the user's request (UI_SPEC, Library sections). Speed was the worry behind it; the grid is lazy, so the full grid stays below the sections.
- [x] `librarySections` rule in `shared:core` (Most used 3, New 6, Least used 6 random per session, no repeats)
- [x] Library shows the sections above "All stickers" on the All view; hero tile kept as the first Most used
- [x] Tests: rules on the JVM, ViewModel, device test for repeated tiles
- [x] Device tests run (all passed on the emulator) and screenshots (library-sections-dark/light)

Done when: on a device the Stickers page shows the four headings in order with a 20+ sticker library, and tests pass.

## M5: Backup and sharing
Plan agreed 2026-11-22; one commit per step.
1. [x] Pack format and checks in `shared:core`: manifest model (kotlinx.serialization), limits, validation, ID mapping, depth flattening in commonMain; zip streaming in a JVM + Android source set. Malicious-file tests on the JVM
2. [x] Export engine (all or one collection), progress and cancel; round-trip test; exports never exceed the import limits
3. [x] Import engine: dedupe, same-name merge, flattening, rollback on failure and cancel, free-space check, summary
4. [x] Settings screen: theme (app and keyboard), export all, export a collection, import, keyboard card
5. [x] Open `.stickerpack` from other apps, checked on the emulator (Files app, "Open with"); the user accepted the emulator check, the phone was not tried

Decisions: pack limits 1 GB and 10,000 stickers (IMPORT_EXPORT, Limits); no usage history in packs; same-name collections merge; existing stickers are not moved; the animation toggle moves to M9 (was M6 before the 2026-11-23 renumbering).

Done when: round-trip test and all rejection tests pass.

## M6: Keyboard basics
Planned 2026-11-23 at the user's request, ahead of release prep (now M9). One commit per step.
1. [x] Key preview popup on press; long-press a letter for accents (e, a, o, u, i, n, c, s, y, z), long-press period for punctuation. Backspace repeat and the ABC long-press keep working. Accent maps in `TypingLayout.kt`, JVM-tested
2. [x] Space-bar cursor slide: drag on space moves the cursor left or right with DPAD key events (never reads the field). Step math JVM-tested
3. [x] Emoji page: system emoji by category, filtered with `Paint.hasGlyph()` so old phones show no empty boxes; list kept in code, not `strings.xml`; no new library. User's calls: an Emoji tab first in the sticker panel (no new key), categories only, no recent emoji
4. [x] Keyboard settings in the app's Settings screen: key sound, vibration, number row, keyboard height. Height and number row apply to the letters and the sticker panel alike; changes apply at the next field. Keys through the one DataStore delegate, added to DATA_MODEL. Not exported in backups (no pack change)
5. [x] Device tests written for each step (`KeyboardTest`, `SettingsUiTest`); all 18 passed on the user's phone (Redmi, 2026-11-25), not the emulator. `KeyboardTest` uses in-memory keyboard settings, so it never writes the real settings file
6. [x] Device check with screenshots, done on the user's phone (Redmi) on 2026-11-25 instead of the emulator, at the user's request (`keyboard-preview-dark`, `keyboard-emoji-dark`). Checked: previews on the top letter row (q to p) show above the keyboard; sliding right past the end of the text in a field with another field after it does not jump to that field; key sound plays with the phone's Touch sounds off; symbol emoji (heart, plane) show in color
7. [ ] No globe key (user's call, 2026-11-25): the space bar reads "Space" and holding it opens the keyboard list. A Toolbar switch in Settings (off by default) adds a row above the letters with Stickers, Emoji, Settings and Switch keyboard; with it on, the sticker key leaves the bottom row. Both designs on the "Space key" page of the UI designs board

Done when: JVM tests pass, device tests pass on the emulator, and screenshots of the new keyboard parts are in `artifacts/`.

## M7: Swipe typing and autocorrect
Changes a v1 non-goal (PRODUCT, KEYBOARD); confirm with the user before starting.
- [ ] Bundled English word list with a license that allows shipping (ask the user to approve the list and its size)
- [ ] Suggestion strip above the letters; autocorrect on space, undo with Backspace; on or off in Settings
- [ ] Swipe typing: trace across letters, best word from the list, alternatives in the strip
- [ ] All on the device; nothing typed is learned, stored or sent. Privacy text updated
- [ ] Off in password, number, phone, email and URL fields

Done when: swipe and autocorrect work on the emulator, JVM tests cover matching and correction, and tests pass.

## M8: Sticker auto-tagging and quick search
Changes two v1 non-goals (no ML, no search); confirm with the user before starting.
- [ ] Choose the on-device image labelling library with the user (rule 7); works without the INTERNET permission
- [ ] Tags table and migration (with a test over old data); labels made on save and for the existing library in the background
- [ ] Users can add, edit and remove tags
- [ ] Search in the app and in the keyboard panel by tag and collection name
- [ ] Tags in `.stickerpack` (optional field; older packs still import)

Done when: new and old stickers get tags on the emulator, search finds them in the app and the keyboard, and tests pass.

## M9: Polish and release prep
- [ ] Performance pass: scrolling with 2000 stickers, animated items only when visible, memory check
- [ ] Animated sticker playback (app and keyboard) and the Settings animation toggle (`animateStickers`), moved from M5
- [ ] Release build config: minify, shrink, signing config read from user-provided properties (never commit secrets)
- [ ] App icon, name, Play listing assets (final: see `docs/BRAND.md`)
- [ ] Privacy statement text for the listing (draft for the user to review)
- [ ] Entitlements interface and $1 unlock plumbing, behind a flag (ask the user for free-tier limits first)
- [ ] Manual test checklist for real chat apps (for the user)

Done when: signed release bundle builds and the manual checklist exists.

## Later (not planned yet)
- Voice typing (needs the microphone permission and Android's on-device recognizer, Android 12 and up)
- iOS port (SwiftUI or Compose Multiplatform; keyboard cannot insert images directly there, clipboard only)
- Direct sharing without a file (needs a network decision)
- WhatsApp: send as `image/webp.wasticker` (512x512 WebP, at most 100 KB) to skip WhatsApp's "send as sticker?" prompt. Only if the prompt annoys users.
- Smaller-looking stickers in chats (user's request, parked 2026-11-22). Chat apps show what a keyboard sends as a photo attachment, stretched to their preview box; only their own sticker formats render small and frameless. Tried on the user's phone: smaller pixels (128-256px) look the same size, just blurrier; transparent padding shrinks the sticker but Discord and Facebook Lite draw a gray box behind attachments, showing the image edges. WhatsApp already works: it offers to send the image as a sticker. Paths left for others: none known for Discord or Messenger.

## Notes log

Add one line per surprise or decision: date, milestone, what happened.

- 2026-11-21, M0: Versions: Gradle 9.7.0, AGP 9.5.2, Kotlin 2.4.21, Compose BOM 2026.11.01. Kotlin's official table only covers AGP up to 9.3.1; 9.5.2 built with no warnings.
- 2026-11-21, M0: Compose 1.13 requires compileSdk 37.1 (minor SDK), set via `compileSdk { version = release(37) { minorApiLevel = 1 } }`. Shared modules stay on 37.
- 2026-11-21, M0: minSdk 26 (variable fonts need `FontVariation`), targetSdk 36. Fonts are the Google Fonts variable TTFs; OFL texts in `ui/android/src/main/assets/licenses/`.
- 2026-11-21, M0: KMP modules have no `test` task, so each registers one that runs `allTests`; `gradlew test` now covers all three modules.
- 2026-11-21, M0: The spec's "Ink" is two colors (dark background #0F0E15, light text #14131C); Theme.kt keeps them as separate tokens.
- 2026-11-21, M0: APK path is `ui/android/build/outputs/apk/debug/android-debug.apk` (not `app/...` as SETUP.md assumes).
- 2026-11-21, M1: Added a `jvm()` target to `shared:db` and `shared:core` (reverses M0's Android-only choice). DAO, repository and migration tests run on the host with Room in-memory + BundledSQLiteDriver; it also makes "no Android imports in commonMain" compiler-enforced.
- 2026-11-21, M1: Versions: Room 2.8.5, KSP 2.3.14, sqlite-bundled 2.6.2 (matches Room's sqlite), coroutines 1.11.0, Turbine 1.2.1, room-testing, AndroidX Test runner 1.7.0 / ext-junit 1.3.0.
- 2026-11-21, M1: Ordering decided: All, Unsorted, Favorites and in-collection lists are newest first (addedAt, then id). Recents exclude never-used stickers. Re-parented children go to the end of the new parent's children.
- 2026-11-21, M1: `parentId` has a real self FK with CASCADE as a safety net; the repository re-parents children / deletes stickers before deleting rows (tests cover the order).
- 2026-11-21, M1: File delete failures are returned as `FileDeleteReport.failed`, not logged yet; callers log them. The startup orphan-file sweep (DATA_MODEL) is not built yet. DataStore settings are not in M1.
- 2026-11-21, M1: `AndroidFileStore` only accepts plain file names `[A-Za-z0-9._-]{1,128}`; import (M2) must name files by hash.
- 2026-11-21, M1: `DataLayerSmokeTest` (ui:android androidTest) checks the real Android database builder, bundled SQLite and file store on the emulator.
- 2026-11-21, M2: Versions: lifecycle-viewmodel-compose 2.11.1, Coil 3.6.3 (coil-compose only), material-icons-extended and ui-test-junit4 from the Compose BOM. The user approved the two not named in ARCHITECTURE (ViewModel, icons).
- 2026-11-21, M2: `hasAlpha` comes from the decoded thumbnail pixels, not the header: many opaque screenshots are RGBA PNGs. The header only says "may have alpha".
- 2026-11-21, M2: Thumbnails are plain 384px WebP. The die-cut outline is not baked in yet (M3); thumbnails saved before M3 will need regenerating.
- 2026-11-21, M2: Animated PNG (APNG) is stored as a still PNG (`isAnimated = false`); Android decoders only show its first frame. Max image side is 16384px, rejected as too large above that.
- 2026-11-21, M2: A duplicate import is a pure no-op (no decode, no writes, no move), even when the user picked a different collection. DATA_MODEL allows "optionally moving"; not offered in the UI.
- 2026-11-21, M2: MainActivity is the share target (single activity). Done after a share calls `finish()` and returns to the sharing app. Only `content:` URIs are read.
- 2026-11-21, M2: Save button height follows screen size (user's call): 48dp when the screen is under 700dp tall (small phones, landscape), 56dp on phones (the design), 64dp on tablets (600dp+ wide). The button is pinned below the scrolling content so it is never cut off. Rows are 48dp (touch minimum).
- 2026-11-21, M2: "New collection" creates inside the selected collection, or beside it when the selected one is at depth 3.
- 2026-11-21, M2: Checked on the emulator: shared from Google Photos ("Save to library" in the share sheet), saved into a nested collection, then shared again with "1 was already in your library" and still one file. The photo picker path works too. Chrome was not tried.
- 2026-11-21, M2: Gotcha: `image/*` inside a KDoc comment opens a nested Kotlin comment and breaks the build.
- 2026-11-21, M2: Save sheet checked against its board (dark and light, nested picker, GIF): matches. Fixed: the GIF badge used the page colors, so it was light in light mode (board: dark in both). Kept on purpose: rows are 48dp (board 44px, our 48dp rule), Unsorted has an inbox icon, "New collection in" names the selected collection (see above), preview tile uses the neutral raised fill (board: a violet tint).
- 2026-11-21, M3: Design vs UI_SPEC conflicts settled with the user: the design's Library layout (round filter rail plus All/Static/Animated chips), with Recents added to the rail; hero = most used, else newest ("NEWEST" label until M4 counts uses); long-press on a collection opens rename/move/delete (drag-to-reorder later, hint text changed); 4-item nav with Add, Settings a placeholder until M5.
- 2026-11-21, M3: Cut line is baked into the thumbnail at import (radius 12px of 384, white, anti-aliased) only for still images with transparent pixels; `hasAlpha` is read before padding. The shadow is drawn in Compose (same image tinted black, offset down) because its strength differs per theme. M2-era thumbnails had no outline: cleared emulator app data, no migration (nothing shipped).
- 2026-11-21, M3: Versions: Navigation Compose 2.10.2 (approved list). No schema change; new queries only (all collections with counts, newest 3 stickers per collection, stickers in a subtree).
- 2026-11-21, M3: LazyVerticalGrid has no row span, so the 2x2 hero and the tiles beside it are one full-width block. Columns are 3 on phones, more on wide screens (100dp minimum cell).
- 2026-11-21, M3: Bugs caught on the emulator: tapping the active Collections tab inside a collection reopened that collection (now returns to the list); at font scale 2.0 the nav pushed Settings off-screen (active label now ellipsizes). Both have tests.
- 2026-11-21, M3: UI tests use an in-memory database and temp folder (TestLibrary), so they never touch the emulator's real library. Debug-only SeedData/SeedReceiver fill the real library for screenshots (`adb shell am broadcast -n app.kisscut.keys/.debug.SeedReceiver --ez clear true`).
- 2026-11-21, M3: Differences from the designs (screenshots in artifacts/): collection previews are real stickers on tinted rounded squares (design draws round faces); subcollection cards are 150-190dp wide (design 150); Recents in the rail; rail and nav labels ellipsize at font 2.0; GIFs show their first frame only (playback while visible is M6); a sticker tile reacts only to long-press outside select mode (no sticker viewer in v1).
- 2026-11-21, M3: The emulator process exited once mid-run of connectedAndroidTest (reported as "status -1"); a restart and rerun passed 38/38.
- 2026-11-21, M3: Trying official game stickers (Pokémon GIPHY, Atelier Ryza 3 icons) found 3 layout bugs the generated seed missed: long titles under the art, hero label covering the GIF badge, and the rail button's rounded clip shaving long labels. Fixed with tests. JPEG is rejected by spec (PNG, WebP, GIF only).
- 2026-11-21, M4: Panel follows the Keyboard board, not the old UI_SPEC line (user's call): globe, All/Static/GIF and Backspace at the bottom, a subcollection chip row, 5 columns. Unsorted tab added (user's call) so unsorted stickers stay reachable; the panel opens on Recents if used, else the first non-empty tab.
- 2026-11-21, M4: Versions: RecyclerView 1.4.0, core-ktx 1.19.0, coil (views) 3.6.3, DataStore preferences 1.1.7 (all approved). DataStore holds only "setup dismissed" so far.
- 2026-11-21, M4: Still stickers whose type the field rejects go as a lossless PNG copy in `cache/send/` (cleared on the next send); stills over 4096x4096 px skip conversion and go to the clipboard. Animated ones are never converted.
- 2026-11-21, M4: Keyboard tests swap `App.container` for an in-memory library under `files/ime-test/` (debug-only FileProvider path). The emulator hands tests a redacted text clip, so the clipboard test checks what the inserter copied, not the clipboard itself.
- 2026-11-21, M4: Gotchas: setting a View background replaces its padding with the drawable's insets; `connectedAndroidTest` uninstalls the app, which wipes the emulator library and turns the keyboard off; switching keyboards per test raced with Gboard, so the IME is selected once per class.
- 2026-11-21, M4: Setup shows at launch until Skip or Done (not after a share); the steps update live from system settings (the picker is a dialog, so resume alone misses it). Also reachable from a keyboard card in the Settings placeholder.
- 2026-11-21, M4: Differences from the boards (screenshots in artifacts/): touch areas are 48dp around the 40dp tabs, 32dp chips and 38dp segments; GIFs show their first frame (playback is M6); thumbnails take 2-3s to appear on the first cold open on the emulator; in dark mode a done step and the current step are both lime (check vs number).
- 2026-11-21, M4.2: Typing added at the user's request after trying M4 (they expected a full keyboard; the spec said stickers only). Basic QWERTY, no autocorrect or suggestions. The sticker panel's globe became an ABC key (hold it for the keyboard list); the globe lives on the letters.
- 2026-11-21, M4.2: Opens on the view used last (kept in memory while the keyboard runs, not saved). Enter sends `\n` with commitText for new lines, not a KEYCODE_ENTER event, because some chat apps treat that key as Send.
- 2026-11-21, M4.2: Gotchas: in strings.xml `?123` must be written `\?123` (a leading `?` is a theme reference) and the backslash in `=\<` doubled; a MATCH_PARENT child of a wrap-content FrameLayout in an IME window takes the whole screen, so the frame is measured to the sticker panel's height. With a phone plugged in, `installDebug` and `connectedAndroidTest` hit both devices: set `ANDROID_SERIAL=emulator-5554` for tests.
- 2026-11-21, M4.2: Not built: key preview popups, long-press for accents or digits, other layouts and languages. No board exists for the letters; screenshots in artifacts/ (typing-dark, typing-light).
- 2026-11-22, M4.5: Logged the accepted types in 7 apps on the user's phone (KEYBOARD, Real-app checks). The send rule fits: Discord, Telegram, Viber, FB Lite and Google Messages take WebP; WhatsApp and Instagram don't, so they get the PNG copy. WhatsApp lists `image/webp.wasticker`, its own sticker type; not used yet. The release build has a no-op `FieldTypesLog` in `src/release`.
- 2026-11-22, M4.5: Still-image code written (JPEG sniffing, `AndroidStillConverter`, clipboard PNG copy); JVM tests pass, device tests written but not run yet at the user's request, so those boxes stay open. Additions not in the plan: EXIF orientation is applied (BitmapFactory ignores it) and a rotated image is always converted; a one-frame GIF is never turned into WebP; the clipboard copy is named `clip-<hash>.png` so the next send doesn't delete it.
- 2026-11-22, M4.5: Device tests 60/60 on the emulator. "Identical pixels" holds for opaque and fully transparent pixels; semi-transparent edge pixels can differ by about 255/alpha per channel, because Android bitmaps are premultiplied and the WebP and PNG encoders round differently. The test allows exactly that. `RepickTest` failed once in a full run: after the keyboard tests turn our keyboard on, the setup screen behind the Save sheet also shows "Done", so clicking by text found two buttons. Fixed by tagging the Save sheet's Done button (`SaveTags.DONE_BUTTON`); passes with the keyboard on.
- 2026-11-22, M4.5: GIF reader written from the GIF89a spec (no code reused), in a new `gif` subpackage of `shared:core`. It checks the limits in a block scan before any pixel decoding, then decodes one frame at a time; `GifCanvas` does the disposal. Damage rules (browser-like): missing trailer or junk keeps the frames before it; a frame with no palette or a bad LZW code size ends the GIF; truncated or broken pixel data leaves the rest of that frame undrawn; indices past the palette draw nothing; the canvas starts transparent (background color ignored). A GIF over the limits fails to decode; whether it is then stored unchanged is decided in the import-wiring box.
- 2026-11-22, M4.5: GIF shrinking (`GifScaler`, `ColorQuantizer`, `GifShrinker`). Scaling is an exact area average in premultiplied form, then alpha cut at half, so edges get no dark halo. A pixel is redrawn only when its scaled color changed since it was last drawn; the rest get the transparent "keep" index, so still areas cannot flicker. A pixel that turns transparent needs the frame before it to have disposal CLEAR over a rectangle covering it, so the shrinker (not the writer) picks each frame's rectangle and disposal, with one frame of lookahead. Colors: own weighted median cut, 255 per frame plus the keep index, exact when they fit, no dithering. An unchanged frame becomes a 1x1 frame that draws nothing, keeping its delay. The writer box only has to LZW-encode these frames.
- 2026-11-22, M4.5: GIF writer (`GifWriter`, `LzwEncoder`): streams frames into one growing buffer, local palette per frame padded to a power of two, clear code when the 4096-code table fills, NETSCAPE2.0 loop only when the source had one. The changed area comes from `GifShrinker`'s rectangles. Checked by round trips through our reader and by ImageIO decoding the output.
- 2026-11-22, M4.5: GIF shrinking wired into `StickerImporter` (`shrinkGif`, runs on Dispatchers.Default, cancellable between frames). A GIF that cannot be shrunk (broken or over the GIF limits) is stored as received, not rejected. The Save button shows "Saving n of m, 40%" during a shrink. Emulator check: a 1000x1000, 24-frame GIF (Pillow-made) saved as 512x512, 24 frames, same delays and loop, 316 kB to 103 kB, mean channel error 0.23 against a box-scaled source (artifacts/save-gif-progress-light.png). The shell cannot grant MediaProvider read access, so `adb am start SEND` with a media URI fails as unreadable; use the in-app photo picker instead. No existing code was reused for any GIF part, so the license box needed no check.
- 2026-11-22, M4.5: Stress check with a dithered GIF (Pillow, 1000x1000, 30 frames, photo-like gradients, Floyd-Steinberg): saved in 21 s on the emulator (about 0.7 s a frame), 2.6 MB to 1.2 MB, mean channel error about 1. Dithering changes nearly every pixel every frame, so each frame is a full redraw and a full quantize. No real-world GIF over 512px was on the emulator; real ones from the user's phone still need a check. Speed-ups (faster quantizer, near-match "keep") are a decision for the user, not done.
- 2026-11-22, Brand (pulled ahead of M6 at the user's request): renamed to Kisscut Keys, package `com.example.stickers` -> `app.kisscut.keys` (Room schema folder renamed with it), keycap launcher icon from BRAND.md. It installs as a new app: the library in the old `com.example.stickers` app (on the user's phone) does not carry over, and export is not built yet (M5). Play icon PNG and listing still open.
- 2026-11-22: Device tests no longer uninstall the app: `android.injected.androidTest.leaveApksInstalledAfterRun=true` in `gradle.properties`. Checked on the emulator: 32 seeded stickers before and after a full `connectedAndroidTest` (61/61 passed, also covering the package rename).
- 2026-11-22: Send-size tests in Discord and Facebook Lite (phone): image size does not change how big a sticker looks, and padding shows the app's gray attachment box. Parked under Later.
- 2026-11-22, M4.5: WhatsApp shows a "send as sticker" prompt for the PNG copy our keyboard sends; picked as sticker it looks like a native sticker. The `wasticker` send is not needed, moved to Later. M4.5 done.
- 2026-11-22, M5.1: kotlinx.serialization 1.11.0 with the Kotlin serialization plugin. The JVM + Android source set is `jvmSharedMain`, declared through `applyDefaultHierarchyTemplate` (opt-in experimental API); a manual `dependsOn` turned the default hierarchy off and warned. Collection repair is linear and the flattening loop iterative, so a hostile manifest with a 50,000-deep chain plans quickly. A manifest is found anywhere in the zip (two passes over the source).
- 2026-11-23, M5.2-3: `StickerImporter` split into `prepare` (checks, convert, write files) and the row; single imports behave as before. A pack row keeps the manifest hash (the original bytes' hash), so a restored backup of converted stickers still dedupes. Packs go to the library root (no parent picker in v1). A streaming reader does not need the zip's index, so a pack cut only there still imports fully.
- 2026-11-23, M5.4-5: Device tests caught that debug builds replace `res/xml/file_paths.xml` (`src/debug`), so the new `cache/export/` path was missing there; both files now say to stay in sync. Forced Light under a dark system left the status bar icons white; the activity now sets the bar icon colors from the app theme. Checked by hand on the emulator: Back up everything through the system save dialog (32 stickers, 567 KB), emptied the library, opened the backup from the Files app ("Open with: Kisscut Keys"), all 32 stickers, 9 collections and favorites back; Share a collection hands `Reactions.stickerpack` to the share sheet; the keyboard is light under forced Light. Device tests 68/68.
- 2026-11-23, M5: Noticed, not changed: in light mode dialog buttons (OK, Cancel) use lime text on a light dialog, hard to read; it comes from the shared light color scheme, so it needs a design call.
- 2026-11-23: The Gradle project (wrapper, settings, `gradle/`, `shared/`, `ui/`) moved into `app/`; run Gradle from there. Older notes above use the old root paths.
- 2026-11-23: Renumbered. New M6 (keyboard basics), M7 (swipe and autocorrect), M8 (auto-tagging and search); release prep moved from M6 to M9. Earlier notes that say M6 mean today's M9.
