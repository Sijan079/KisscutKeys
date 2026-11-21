# KEYBOARD.md

The keyboard is an `InputMethodService` in the Android app module (`keyboard` package). Native Views, not Compose.

## Purpose

A sticker-only panel. It is **not** a full typing keyboard in v1. It provides a globe key to switch back to the user's normal keyboard (Android requires every keyboard to offer a way to switch).

## Components

- `StickerImeService : InputMethodService`: creates the panel view in `onCreateInputView()`, reads `currentInputEditorInfo` in `onStartInputView()`.
- `StickerPanelView`: tab row (Recents, Favorites, collections), `RecyclerView` with `GridLayoutManager`, bottom bar (globe, back).
- `StickerAdapter`: binds thumbnails with Coil; for animated stickers loads the animation only when the item is attached and visible.
- `StickerInserter`: commits the sticker to the target app.
- Manifest: service with `android.permission.BIND_INPUT_METHOD`, intent filter `android.view.InputMethod`, `meta-data` pointing at `res/xml/method.xml`.
- `FileProvider` with authority `<APP_ID>.fileprovider` exposing `files/stickers/`.

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

Animated stickers (GIF, animated WebP) are sent in their stored format. If the target rejects the type, tell the user in the same hint.

## Performance

- Thumbnails everywhere; originals only for animation.
- Panel appears in under 150ms on a warm start. Build the adapter and tab data lazily.
- Animate only visible items; pause when the panel hides (`onFinishInputView`).
- Keep memory bounded: Coil memory cache limited; do not hold bitmaps for off-screen tabs.

## States to handle

- No stickers yet: panel shows a message and a button that opens the app.
- Tab with zero items: empty text.
- Password and no-image fields (`InputType` password): disable insertion, show hint.
- Dark and light: follow the app theme; the panel reads the same tokens (see UI_SPEC).
- Landscape and split screen: panel adapts column count.

## Testing

See TESTING.md. Include a debug-only `ImeTestActivity` with an `EditText` that declares supported MIME types (`androidx.core.view.ViewCompat.setOnReceiveContentListener` or `setContentMimeTypes`) so insertion can be verified without a third-party chat app.

Enable and select the IME via adb: `adb shell ime enable <APP_ID>/<service class>` then `adb shell ime set <APP_ID>/<service class>`.

## Known unknowns

- Behavior differs per target app (WhatsApp, Telegram, Messenger, Gmail). Real-app behavior must be checked on a device the user owns later; the emulator can't cover it.
- Some apps accept only PNG or only static images. Record findings in a table under this section as they are discovered.
