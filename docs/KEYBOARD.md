# KEYBOARD.md

The keyboard is an `InputMethodService` in the Android app module (`keyboard` package). Native Views, not Compose.

## Purpose

A basic typing keyboard plus the sticker panel (typing added in ROADMAP M4.2, user's call). Letters, numbers and symbols, no autocorrect, word suggestions, swipe typing or emoji search. A sticker key swaps the letters for the sticker panel and an ABC key swaps back. A globe key switches to another keyboard (Android requires every keyboard to offer a way to switch).

Privacy: keys are sent to the field as they are pressed. Nothing typed is stored, logged, read back or sent anywhere. The only reads of the field are the cursor's caps mode (for auto-capitalization) and the editor's action (Send, Search, Done) for the Enter key.

### Typing layout (M4.2)

- Letters: `q`-`p`, `a`-`l`, then Shift, `z`-`m`, Backspace; bottom row `?123`, sticker key, globe, comma, space, period, Enter.
- Shift: tap for one capital, double-tap for caps lock. Auto-capital at sentence start when the field asks for it (`getCursorCapsMode`).
- `?123` page: digits and common symbols, with a `=\<` page for the rest. `ABC` returns to letters.
- Enter performs the field's action (Send, Search, Go, Done, Next) unless the field is multi-line or sets `IME_FLAG_NO_ENTER_ACTION`; then it types a new line.
- Backspace repeats while held. Keys give a light haptic tap.
- Number and phone fields open on the `?123` page. Password fields open on letters and the sticker key is off.
- The keyboard opens on the view used last (letters or stickers), except in password, number and phone fields.
- Same panel height as the sticker panel, same tokens: keys on Raised, special keys on Line, Enter on Lime.

## Components

All in `ui/android/src/main/java/com/example/stickers/keyboard/`:

- `StickerImeService : InputMethodService`: builds both views in `onCreateInputView()` inside a frame sized to the sticker panel, picks the view and page for each field in `onStartInputView()`, sends keys and stickers. All coroutines live in a scope cancelled on rebuild and in `onDestroy`.
- `TypingView` + `TypingLayout.kt`: the letters (see Typing layout). `TypingLayout.kt` holds the pure rules (rows, Shift, Enter action, which view a field opens on), unit-tested on the JVM.
- `StickerPanelView`: tab row (Recents, Favorites, top-level collections, Unsorted while non-empty), subcollection chip row, `RecyclerView` with `GridLayoutManager`, bottom bar (ABC, All/Static/GIF, Backspace). ABC returns to the letters; long-press opens the system keyboard list.
- `KeyboardModel`: the sticker panel's state (tab, chip, kind, stickers), unit-tested with the fake library.
- `StickerAdapter`: binds thumbnails with Coil (ImageViews). Animated stickers show their first frame; playback while visible is ROADMAP M6.
- `StickerInserter`: sends a sticker (see Inserting a sticker). `InputFields.kt`: password detection. `KeyboardPalette`: Theme.kt tokens as ARGB ints.
- Shared rules in `shared/core`: `InsertPlan.kt` (MIME matching and the insert route) and `KeyboardTabs.kt` (tabs, chips, start tab).
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
- Dark and light: follow the app theme; the panel reads the same tokens (see UI_SPEC).
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
