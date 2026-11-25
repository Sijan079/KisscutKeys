# KEYBOARD.md

The keyboard is an `InputMethodService` in the Android app module (`keyboard` package). Native Views, not Compose.

## Purpose

A basic typing keyboard plus the sticker panel (typing added in ROADMAP M4.2, user's call). Letters, numbers and symbols, no autocorrect, word suggestions, swipe typing or emoji search (autocorrect and swipe are planned in ROADMAP M7; until then they stay out). A sticker key swaps the letters for the sticker panel and an ABC key swaps back. Holding the space bar opens the system keyboard list, to switch to another keyboard (no globe key since M6.7, user's call; Gboard does the same, and most phones also show their own switch button).

Privacy: keys are sent to the field as they are pressed. Nothing typed is stored, logged, read back or sent anywhere. The only reads of the field are the cursor's caps mode (for auto-capitalization) and the editor's action (Send, Search, Done) for the Enter key. Moving the cursor uses arrow key events, which read nothing.

### Typing layout (M4.2)

- Letters: `q`-`p`, `a`-`l`, then Shift, `z`-`m`, Backspace; bottom row `?123`, sticker key, comma, space (labelled "Space"), period, Enter. The sticker key shows a sticker with a peeled corner and no face, so it is never confused with the Emoji tab's smiley.
- Shift: tap for one capital, double-tap for caps lock. Auto-capital at sentence start when the field asks for it (`getCursorCapsMode`).
- `?123` page: digits and common symbols, with a `=\<` page for the rest. `ABC` returns to letters.
- Enter performs the field's action (Send, Search, Go, Done, Next) unless the field is multi-line or sets `IME_FLAG_NO_ENTER_ACTION`; then it types a new line.
- Backspace repeats while held. Keys give a light haptic tap.
- Key popups (M6): a bubble shows the pressed key above it; the key types on release. Holding a key with alternates (accents on e a o u i n c s y z, punctuation on the period) for 350 ms opens a row with the most common choice above the finger; slide to pick, release to type. Accents follow Shift (one with no one-letter capital, like `ß`, stays as it is). TalkBack gets each alternate as a key action ("Type é").
- Cursor slide (M6): sliding a finger on the space bar past the touch slop moves the cursor one character per 12 dp, with a tick per step, and types no space. The keyboard sends arrow key events (DPAD left and right), so the field moves its own cursor and nothing is read.
- Hold Space (M6.7): holding the space bar still for 500 ms opens the system keyboard list and types nothing; sliding first cancels it. TalkBack gets "Switch keyboard" as an action on the space bar.
- Number and phone fields open on the `?123` page. Password fields open on letters and the sticker key is off.
- The keyboard opens on the view used last (letters or stickers), except in password, number and phone fields.
- Same panel height as the sticker panel, same tokens: keys on Raised, special keys on Line, Enter on Lime.

### Keyboard settings (M6)

In the app's Settings screen, Keyboard group:

- Sound on key press (default off): the system key-click sound, a little under media volume. Plays even when the phone's Touch sounds are off (this switch is the setting).
- Vibrate on key press (default on): the light haptic tap.
- Number row (default off): digits 1 to 0 above the letters. The panel grows by one key row, so keys keep their size.
- Toolbar (default off, M6.7): a 48 dp row above the letters, on every page. Left: Stickers and Emoji (the sticker panel on its Emoji tab); both dimmed in password fields. Right: Keyboard settings (a gear; opens the app on Settings through `MainActivity.EXTRA_OPEN_SETTINGS`) and Switch keyboard (the system keyboard list). The sticker key leaves the bottom row; the panel grows by the toolbar's height, so keys keep their size. Design: "Space key" page, option B.
- Keyboard height: Small (0.85x), Normal, Large (1.15x). The letters and the sticker panel share the height.

Sound and vibration apply on the next tap (`KeyFeedback` reads them each time). Number row, toolbar and height rebuild the views when the next field opens, like the theme. Not part of backups or shared packs.

### Emoji tab (M6)

- First tab of the sticker panel (user's call, 2026-11-23: a tab, not a key on the letters). Never the tab the panel opens on.
- Category chips in the chip row: Smileys, People, Animals and nature, Food and drink, Activities, Travel and places, Objects, Symbols. The chosen category is kept while the keyboard runs.
- A tap types the emoji as text, like a key. No recent emoji (user's call: nothing typed is stored), no search, no skin tones.
- The All/Static/GIF switch is hidden (kept in place, so the row does not jump). Works with an empty library.
- The list lives in code (UI strings carry no emoji); emoji the phone cannot draw are left out, so older phones show no empty boxes. Symbols that default to text style (heart, plane, arrows) carry U+FE0F so they show and send in color; a JVM test checks this.

### Word list (M7)

English words for suggestions, autocorrect and swipe (used from M7 step 2 on). Source: AOSP LatinIME `dictionaries/en_US_wordlist.combined.gz` (blob `8aed9c5e0b94d0f3e9654f66104d7af1e84c454f`), Apache License 2.0, Copyright 2008 The Android Open Source Project. `tools/wordlist/build_en.py` turns a hand-downloaded copy into two assets (it never touches the network):
- `assets/words/en_US.txt`: the 50,000 most common words (user's call), `word<TAB>frequency` with frequency the source's 0-255 score; `possibly_offensive` words carry a third column `o` and are known but never suggested or used as a correction. Entries with `f=0` or `not_a_word` are dropped. About 600 KB; the APK compresses it.
- `assets/words/en_US_fixes.txt`: the source's 99 typed-to-fix pairs (`cant` to `can't`, `alot` to `a lot`). Some typed sides are real words (`ill`, `lets`), so step 2 decides when a pair applies.

Both files say in their header that they were changed from the source. `shared:core` `words.WordList` parses them (strict: a malformed line fails with its line number). The license text ships in `assets/licenses/` and shows on Settings, Open-source licenses.

### Suggestions and autocorrect (M7)

`shared:core` `words.Speller` works over a list of word lists at once (English now, Tagalog later; no language switch). It is pure: the keyboard passes in what was typed and what the user undid.
- Matching ignores case and apostrophes ("dont" meets "don't"). Typo costs: 1 for a change, 0.6 for a neighbouring QWERTY key or two swapped letters, 0.4 for a missed or extra double letter ("helo", "helllo"). Suggestions reach 2; autocorrect 1, or 2 for words of 6 letters or more (user's call, 2026-11-25: "wlrdld" to "world"; big keyboards fix two-typo long words too, using context we don't have, so short words stay at 1). Only words whose first letter is the typed one, a neighbouring key or a swapped first pair are checked, and the letters used must be close, so a keystroke checks a few thousand words.
- Suggestions (up to 3): a known word first as typed, then a fix pair, corrections (frequency minus 100 per unit of cost) and completions from the second letter on. Offensive words are never offered. The typed case carries over ("Teh" gives "The", "TEH" gives "THE").
- Autocorrect never changes a known word, a word with digits or symbols, ALL CAPS, MixedCase, chat shorthand (`CHAT_WORDS`: lol, thx, idk and similar; "thx" otherwise became "the"), a word the user undid, or into an offensive word. A fix pair applies only when the typed word is not a real word ("bot" stays, "not" is suggested). Otherwise one typo at most (two from 6 letters on), a target with frequency 60 or more, at least 3 letters, and a lead of 25 over the runner-up; apostrophe and case-only fixes ("dont", "i") always apply.
- Speed (Redmi M2103K19G, debug build, `SpellerTimingTest`): 8 ms median and 14 ms at the 95th percentile per keystroke; loading the list takes about 2.5 s, so it loads off the main thread once per keyboard start.

### Typing pipeline (M7)

- Privacy: the keyboard reads nothing from the field except, when a word starts where it did not put the cursor itself (a box that already has text, after a tap, Enter, a sticker or a Backspace), the one character before the cursor, to see whether that word stands on its own; it keeps nothing (user's call, 2026-11-25, after Discord kept drafts and the first word never got corrected). In plain text fields it holds only the word being typed, built from its own key presses (`shared:core` `words.Composer`), and shows it underlined with `setComposingText`; the word is dropped when it ends. Nothing typed is stored, learned or sent. The setup screen says so.
- Where: plain text fields only (`allowsTypingAids`): never in password, number, phone, date, email or web address fields, fields flagged "no suggestions", or fields with no input type. Elsewhere, and when both Autocorrect and Suggestions are off, keys go straight to the field as before.
- Letters, digits and apostrophes extend the word. Space and `. , ! ? ; :` end it and may autocorrect it (only when Autocorrect is on); other symbols end it as typed. Only a word that starts at a word boundary is corrected (the field start, or right after a space or new line), so "gmail" in "john@gmail.com", "@teh" and "#teh" are left alone. After the keyboard's own space it knows; otherwise it checks the one character before the cursor once, when the word starts. Enter, the sticker or emoji panel, a space-bar cursor slide, switching keyboards and leaving the field keep it as typed.
- Backspace shortens the word; right after an autocorrection it puts back what was typed (deleting only the correction it just wrote) and that word is not corrected again in this field.
- Cursor safety (`CursorTracker`): from the lengths of its own edits the keyboard knows where the cursor should be. A selection update it doesn't expect (a tap elsewhere, the app changing the text) keeps the word as it stands and drops the undo, so Backspace never deletes away from the correction. The expected position is recorded before the edits are sent: in a field in this app's own process the update arrives before `endBatchEdit` returns (the device tests caught this). Before each edit to the word the keyboard re-marks it with `setComposingRegion` from those positions, because some apps drop the underline without any selection update; without that the next letter typed the word twice ("heyheya", caught on the phone). An update that shows a different underline length also lets go of the word.
- The word list loads once per keyboard start on a background thread; until then typing works without corrections.
- Settings: `kb_autocorrect` (default off until a Tagalog list lands, so Taglish isn't "fixed") and `kb_suggestions` (default on); Autocorrect on forces suggestions on (`KeyboardSettings.showsSuggestions`). The Autocorrect and Suggestions switches are in Settings, Keyboard (added in step 3 so autocorrect can be tried; the strip itself is step 4).

### Suggestion strip (M7)

- One row above the letters, `TOOLBAR_DP` high, shared with the toolbar. It exists whenever Toolbar or Suggestions is on (`topRow`), in every field, so the keyboard never changes height (user's call: an empty row between words rather than a jump). The sticker panel grows by the same height.
- While a word is being typed in a plain text field: three slots (`stripSlots`). Without a coming fix: second choice, best (bold, middle), third choice. When Autocorrect will change the word on Space: the typed word in quotes (left, keeps it), the fix (bold, middle, lime bar), the next choice. Between words: the toolbar icons if Toolbar is on, else a blank row.
- Tapping a slot puts its word in with a space (`Composer.pick`); the quoted word also stops that word being corrected again in the field. Slots are 1/3 of the width and 48 dp high; TalkBack reads the word, or "Keep teh".
- The lookup runs off the main thread on each keystroke and a result for a word that has changed since is dropped, so typing never waits on it.

## Components

All in `app/ui/android/src/main/java/app/kisscut/keys/keyboard/`:

- `StickerImeService : InputMethodService`: builds both views in `onCreateInputView()` inside a frame sized to the sticker panel, picks the view and page for each field in `onStartInputView()`, sends keys and stickers. All coroutines live in a scope cancelled on rebuild and in `onDestroy`.
- `TypingView` + `TypingLayout.kt`: the letters (see Typing layout). `TypingLayout.kt` holds the pure rules (rows, Shift, Enter action, which view a field opens on, alternates and popup placement), unit-tested on the JVM. `KeyPopups`: the preview and alternates bubbles (touch-through `PopupWindow`s anchored to the key, closed when the keyboard hides).
- `StickerPanelView`: tab row (Emoji, Recents, Favorites, top-level collections, Unsorted while non-empty), subcollection chip row (emoji categories on the Emoji tab), `RecyclerView` with `GridLayoutManager`, bottom bar (ABC, All/Static/GIF, Backspace). ABC returns to the letters; long-press opens the system keyboard list.
- `KeyboardModel`: the sticker panel's state (tab, chip, kind, stickers), unit-tested with the fake library.
- `EmojiCatalog.kt` (`EmojiCategory`): the built-in emoji list by category, JVM-tested. `EmojiAdapter`: the emoji grid; drops emoji the phone's font cannot draw (`Paint.hasGlyph`).
- `StickerAdapter`: binds thumbnails with Coil (ImageViews). Animated stickers show their first frame; playback while visible is ROADMAP M9.
- `KeyFeedback`: key sound and vibration as set in Settings. Settings live in `settings/KeyboardSettings.kt`.
- `StickerInserter`: sends a sticker (see Inserting a sticker). `InputFields.kt`: password detection. `KeyboardPalette`: Theme.kt tokens as ARGB ints.
- Shared rules in `app/shared/core`: `InsertPlan.kt` (MIME matching and the insert route) and `KeyboardTabs.kt` (tabs, chips, start tab).
- Manifest: service with `android.permission.BIND_INPUT_METHOD`, intent filter `android.view.InputMethod`, `meta-data` pointing at `res/xml/method.xml`.
- `FileProvider` with authority `<APP_ID>.fileprovider` exposing `files/stickers/` and `cache/send/` (PNG copies). Debug builds add `files/ime-test/` for the keyboard tests.

## Data

Reads through the same repositories as the app, via `AppContainer`. Observe `Flow`s with a coroutine scope tied to the service lifecycle; cancel in `onDestroy`. Never keep a second database.

## Inserting a sticker

1. Check the target supports images:

```kotlin
val types = EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo)
val supported = types.any { ClipDescription.compareMimeTypes(sticker.mimeType, it) }
```

2. If supported, build a content URI with `FileProvider.getUriForFile(...)` and commit:

```kotlin
val info = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(sticker.mimeType)), null)
var flags = 0
if (Build.VERSION.SDK_INT >= 25) flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
InputConnectionCompat.commitContent(currentInputConnection, currentInputEditorInfo, info, flags, null)
```

3. If not supported, copy the URI to the clipboard as a `ClipData` with the image and show a short hint ("Paste to send"). Do not claim success.
4. Only when `commitContent` returns true, call `markUsed(id)`.

Still stickers are stored mostly as lossless WebP, which some apps do not accept. Before falling back to the clipboard, if the target accepts `image/png` but not the stored type, convert that one sticker to PNG (lossless, same pixels) into `cache/send/`, commit that, and clear the folder on the next send. Never change the stored file.

The clipboard fallback copies still stickers (other than stored PNGs) as a PNG copy too, since pasting WebP fails in some apps. That copy is named `clip-<hash>.png` in `cache/send/`: the next send leaves it alone, so the clipboard does not point at a deleted file; the next clipboard copy replaces it. If the copy cannot be made (over 4096x4096 px or undecodable), the stored file goes on the clipboard.

Animated stickers (GIF, animated WebP) are sent in their stored format. If the target rejects the type, tell the user in the same hint.

## Performance

- Thumbnails everywhere; originals only for animation.
- Panel appears in under 150ms on a warm start. Build the adapter and tab data lazily.
- Animate only visible items; pause when the panel hides (`onFinishInputView`).
- Keep memory bounded: Coil memory cache limited; do not hold bitmaps for off-screen tabs.

## States to handle

- No stickers yet: panel shows a message and a button that opens the app.
- Tab with zero items: empty text.
- Password fields: open on letters, the sticker key is off (dimmed). If the sticker panel is somehow shown, it says stickers are off.
- Fields that take no images: stickers go to the clipboard with "Copied. Paste to send."
- Dark and light: follow the Settings theme (System, Dark, Light), not only the system; the panel reads the same tokens (see UI_SPEC). A theme changed while the keyboard is alive rebuilds the views at the next field.
- Landscape and split screen: panel adapts column count.

## Testing

See TESTING.md. The debug-only `ImeTestActivity` has five fields: any image, PNG only, plain text, password, and a Send-action field. Image fields wrap their input connection with `InputConnectionCompat.createWrapper` and record what they receive, so insertion is verified without a chat app.

Enable and select the IME via adb: `adb shell ime enable <APP_ID>/<service class>` then `adb shell ime set <APP_ID>/<service class>`.

## Known unknowns

- Behavior differs per target app (WhatsApp, Telegram, Messenger, Gmail). Real-app behavior must be checked on a device the user owns later; the emulator can't cover it.
- Some apps accept only PNG or only static images. Record findings in a table under this section as they are discovered.

| App | Device | Date | Build | Typing | Still sticker | GIF | Notes |
|---|---|---|---|---|---|---|---|
| Discord | Xiaomi M2103K19G (user's phone) | 2026-11-21 | M4.2 (stickers stored as received: PNG/WebP/GIF) | works | works | works | Direct insert vs clipboard paste not noted; re-check after M4.5 makes WebP the stored format |

Types each app's message box accepts from keyboards, read with the debug-only `FieldTypesLog` (`adb logcat -s StickerFieldTypes`; on the user's phone run `adb logcat -G 16M` first, the default 256 KB buffer drops lines). Same phone, 2026-11-22.

| App | Accepts | Stored still WebP goes as |
|---|---|---|
| Discord | `image/*` | WebP |
| Telegram | `image/gif`, `image/*`, `image/jpg`, `image/png`, `image/webp` | WebP |
| Viber | `image/*` | WebP |
| Facebook Lite | `image/*` | WebP |
| Google Messages | `image/*`, `video/*`, `audio/*`, documents | WebP |
| WhatsApp | `image/gif`, `video/x.looping_mp4`, `image/jpeg`, `image/jpg`, `image/png`, `image/webp.wasticker` | PNG copy (no plain `image/webp`) |
| Instagram | `image/png`, `image/jpeg`, `image/gif` | PNG copy |

Animated WebP has no accepted type in WhatsApp or Instagram, so it gets the "can't send" hint there. WhatsApp's `image/webp.wasticker` is its own sticker type (WebP, 512x512); sending as it is not built.
