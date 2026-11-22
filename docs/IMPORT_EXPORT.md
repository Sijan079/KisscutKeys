# IMPORT_EXPORT.md

## Goals

- Back up and restore a library, and share sticker packs with a friend, using a plain file. No accounts, no network.
- Safe by default: a pack file from a stranger must not be able to harm the device or the app.

## File format: `.stickerpack`

A zip file with this layout:

```
manifest.json
stickers/<hash>.<ext>
settings.json        (optional)
```

### manifest.json

```json
{
  "format": "stickerpack",
  "version": 1,
  "createdAt": 1767225600000,
  "appVersion": "1.0.0",
  "collections": [
    { "id": 1, "name": "Cats", "parentId": null, "sortOrder": 0 },
    { "id": 2, "name": "Grumpy", "parentId": 1, "sortOrder": 0 }
  ],
  "stickers": [
    {
      "file": "stickers/ab12....png",
      "hash": "ab12...",
      "mimeType": "image/png",
      "collectionId": 2,
      "isFavorite": false
    }
  ]
}
```

- IDs in the manifest are local to the pack. Map them to new database IDs on import.
- `useCount`, `lastUsedAt` and `addedAt` are not exported (personal history). Set fresh values on import.
- `version`: reader rejects versions higher than it knows with a clear message; it must read all lower versions. Use `ignoreUnknownKeys = true`.

### settings.json (optional, user opts in)

`{ "theme": "system", "animateStickers": true }`. Unknown keys ignored.

## Export

1. User picks scope: everything, or one collection (with its descendants).
2. Write to a temp file in `cacheDir`, stream sticker files into the zip (do not load all into memory).
3. Offer through the system file picker (`ACTION_CREATE_DOCUMENT`, SAF) or the share sheet (`ACTION_SEND` with a `FileProvider` URI). Delete the temp file afterwards.
4. Progress as `Flow<Progress>`; cancellable.

## Stored format (every import: share sheet, picker, pack)

Still images: built in ROADMAP M4.5 (`StickerImporter` with `AndroidStillConverter`). Large GIFs: not built yet, stored as received; the reader is done (`shared:core` package `gif`: `GifReader`, `GifCanvas`), the shrinking and the writer are not.

Goal: a library of thousands of stickers stays in the hundreds of MB, with no visible quality loss on a phone.

Accepted input, sniffed from magic bytes: PNG, JPEG, WebP (still or animated), GIF. Anything else is rejected.

Still images (PNG, JPEG, still WebP):
1. Decode (at most about 4x the target, via `inSampleSize`, so huge images stay within memory). Apply the EXIF orientation, since Android's decoder ignores it. If the longest side is over **512px**, scale down to 512 (keep aspect ratio; halve with filtering, then one final filtered step). Never scale up.
2. Encode as **lossless WebP** (`WEBP_LOSSLESS` on API 30+, `WEBP` at quality 100 on API 29, which Android encodes lossless). On API 26 to 28 Android has no lossless WebP encoder, so encode **PNG** instead.
3. **Never store something bigger than the input:** if the input was not scaled down or rotated and its original bytes are not bigger than the encoded result, keep the original bytes and type (for example a small JPEG or a lossy WebP; re-encoding those losslessly only makes them bigger, never better).
4. Thumbnail, `hasAlpha` and the cut line come from the stored image, as before.
5. A still GIF (one frame) is never turned into WebP; GIFs follow the animated rules below.

Animated images (GIF, animated WebP): stored **unchanged** (still under the 10 MB cap) until ROADMAP M4.5 shrinks GIFs over 512px with our own Kotlin GIF encoder (output stays GIF; near-lossless, as GIF allows 256 colors per frame). Animated WebP stays unchanged (shrinking it would need libwebp; undecided).

Duplicates: `hash` is the SHA-256 of the **bytes as received**, before conversion, so saving the same file twice is still a no-op. Known gap: a sticker sent from this app and saved back arrives as the converted bytes, so it is not recognised as the same; accepted for v1.

Existing stickers saved before this rule keep their original files; nothing is rewritten. A later Settings action may shrink them (needs its own tests over old data).

## Import

Entry points: Settings "Import pack" (SAF `ACTION_OPEN_DOCUMENT`) and opening a `.stickerpack` from another app (intent filter for the MIME type and the extension).

Steps:
1. Open the stream, **never extract the zip to disk first**. Read `manifest.json`, validate, then process entries.
2. For each sticker entry:
   - Verify the path is exactly under `stickers/` and contains no `..`, absolute path or drive letter (**zip-slip protection**). Reject the whole pack on violation.
   - Read bytes with a size cap, sniff the real type from magic bytes (PNG, JPEG, WebP, GIF), ignore the declared `mimeType` if it disagrees, skip anything else.
   - Compute SHA-256 of the entry bytes. If that hash or the manifest `hash` already exists, skip the copy and reuse the existing sticker (still apply collection mapping if the user chose to). A pack exported from this app carries the original hash, so restoring a backup into the same library adds no duplicates.
   - Convert per "Stored format" (already-converted stickers pass through unchanged), write the file, generate the thumbnail, compute `hasAlpha` and `isAnimated`, insert the row with the manifest `hash` when present and valid hex, else the computed one.
3. Create collections first (parents before children), respecting the depth cap of 3. If an imported tree is deeper, flatten the extra levels into the deepest allowed collection and report it.
4. Put the pack's top-level collections under a user-chosen parent, or the root.
5. Show a summary: added, skipped as duplicates, rejected with reasons.

### Limits (constants in one place)

| Limit | Value |
|---|---|
| Max pack file size | 200 MB |
| Max single sticker (input) | 10 MB |
| Max stored side (still images) | 512 px |
| Max GIF canvas decoded | 2048 x 2048 px (4,194,304 px) |
| Max GIF frames | 5000 |
| Max GIF decode work | 1,000,000,000 px (canvas plus frame area, summed over frames) |
| Max stickers per pack | 2000 |
| Max zip entries read | 5000 |
| Max total uncompressed | 500 MB (guards against zip bombs; abort when exceeded) |
| Max manifest size | 2 MB |

### Failure behavior

- Wrap the whole import in a logical unit: if it fails midway, remove files written by this import and roll back inserted rows. A half-imported pack must not remain.
- Never crash on malformed input; show a plain error.

## Sharing (v1)

"Share a pack" is just export plus the system share sheet. The friend opens the file with the app. No direct Wi-Fi, QR or relay in v1 because they would need the INTERNET permission or a server.

## Tests

- Round trip: export then import into an empty DB yields equal collections and stickers.
- Duplicate import is idempotent.
- Zip-slip entry, oversized entry, zip bomb, wrong extension with valid PNG bytes, valid extension with garbage bytes, and future version number are each rejected or handled as specified.
- Nested depth over 3 gets flattened with a report.
