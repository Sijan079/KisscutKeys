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
- `useCount`, `lastUsedAt` and `addedAt` are not exported (personal history), not even as an option (user's call, 2026-11-22). Set fresh values on import.
- `hash`: accepted only as 64 lowercase hex characters (it becomes the file name); otherwise the computed hash is used. The same hash twice in one pack is imported once.
- `version`: reader rejects versions higher than it knows with a clear message; it must read all lower versions. Use `ignoreUnknownKeys = true`.

### settings.json (optional: in "Back up everything", never in a shared collection)

`{ "theme": "system" }`. Unknown keys ignored. `animateStickers` joins in M9 with playback. `keyboard_setup_dismissed` is never exported.

## Export

1. User picks scope: everything, or one collection (with its descendants).
2. Write to a temp file in `cache/export/`, `manifest.json` first, then stream sticker files into the zip (do not load all into memory).
3. Offer through the system file picker (`ACTION_CREATE_DOCUMENT`, SAF) or the share sheet (`ACTION_SEND` with a `FileProvider` URI on `cache/export/`, not `cache/send/`, which the keyboard clears). Delete the temp file afterwards.
5. An export never produces a pack the importer would reject (tested against the limits below).
4. Progress as `Flow<Progress>`; cancellable.

## Stored format (every import: share sheet, picker, pack)

Built in ROADMAP M4.5: still images in `StickerImporter` with `AndroidStillConverter`; GIFs in pure Kotlin (`shared:core` package `gif`: `GifReader`, `GifCanvas`, `GifShrinker`, `GifWriter`, called through `shrinkGif`).

Goal: a library of thousands of stickers stays in the hundreds of MB, with no visible quality loss on a phone.

Accepted input, sniffed from magic bytes: PNG, JPEG, WebP (still or animated), GIF. Anything else is rejected.

Still images (PNG, JPEG, still WebP):
1. Decode (at most about 4x the target, via `inSampleSize`, so huge images stay within memory). Apply the EXIF orientation, since Android's decoder ignores it. If the longest side is over **512px**, scale down to 512 (keep aspect ratio; halve with filtering, then one final filtered step). Never scale up.
2. Encode as **lossless WebP** (`WEBP_LOSSLESS` on API 30+, `WEBP` at quality 100 on API 29, which Android encodes lossless). On API 26 to 28 Android has no lossless WebP encoder, so encode **PNG** instead.
3. **Never store something bigger than the input:** if the input was not scaled down or rotated and its original bytes are not bigger than the encoded result, keep the original bytes and type (for example a small JPEG or a lossy WebP; re-encoding those losslessly only makes them bigger, never better).
4. Thumbnail, `hasAlpha` and the cut line come from the stored image, as before.
5. A still GIF (one frame) is never turned into WebP; GIFs follow the animated rules below.

GIFs (still or animated):
1. At most 512px on the longest side: stored unchanged.
2. Over 512px: decoded one frame at a time, each composited frame scaled to 512 (exact area average, edges without dark halos, transparency stays on/off), and written again as a **GIF** that keeps every frame, delay and the loop count. Each frame redraws only the pixels that changed and gets its own palette of up to 255 colors (exact when they fit; near-lossless otherwise, no dithering). The Save sheet shows the percentage while this runs; closing the sheet cancels it and stores nothing.
3. **Never bigger:** the received bytes are kept when the result is not smaller than the input, or when the GIF cannot be shrunk (broken, or over the GIF limits in Limits below; it is still stored as received if it passes the usual checks).

Animated WebP: stored **unchanged** (shrinking it would need libwebp; undecided).

Duplicates: `hash` is the SHA-256 of the **bytes as received**, before conversion, so saving the same file twice is still a no-op. Known gap: a sticker sent from this app and saved back arrives as the converted bytes, so it is not recognised as the same; accepted for v1.

Existing stickers saved before this rule keep their original files; nothing is rewritten. A later Settings action may shrink them (needs its own tests over old data).

## Import

Entry points: Settings "Import pack" (SAF `ACTION_OPEN_DOCUMENT`) and opening a `.stickerpack` from another app. Other apps hand over `content:` URIs typed `application/zip` or `application/octet-stream`, so the filter is broad and the manifest's `format` field decides; checked on a real phone.

Steps:
1. Open the stream, **never extract the zip to disk first**. Read `manifest.json`, validate, then process entries. A pack from elsewhere may not have the manifest first: open the source a second time (SAF URIs can be reopened) instead of buffering entries. Count the bytes actually decompressed; never trust the sizes in zip headers.
   Before starting, check the free space is at least the pack size plus 50 MB.
2. For each sticker entry:
   - Verify the path is exactly under `stickers/` and contains no `..`, absolute path or drive letter (**zip-slip protection**). Reject the whole pack on violation.
   - Read bytes with a size cap, sniff the real type from magic bytes (PNG, JPEG, WebP, GIF), ignore the declared `mimeType` if it disagrees, skip anything else.
   - Compute SHA-256 of the entry bytes. If that hash or the manifest `hash` already exists, skip it: the existing sticker stays where it is (not moved, favorite unchanged). A pack exported from this app carries the original hash, so restoring a backup into the same library adds no duplicates.
   - Convert per "Stored format" (already-converted stickers pass through unchanged), write the file, generate the thumbnail, compute `hasAlpha` and `isAnimated`, insert the row with the manifest `hash` when present and valid hex, else the computed one.
3. Create collections first (parents before children), respecting the depth cap of 3. If an imported tree is deeper, flatten the extra levels into the deepest allowed collection and report it. A collection whose name already exists under the same parent (ignoring case and outer spaces) is **merged** into it, so restoring a backup does not duplicate collections (user's call).
4. Put the pack's top-level collections at the root of the library (v1; choosing a parent is not offered).
5. Show a summary: added, skipped as duplicates, rejected with reasons, collections created, merged and flattened.

Code: `PackExporter` and `PackImporter` in `shared:core` (`pack` package) over `PackSink` and `PackSource`; `PackZipWriter` and `PackZipReader` implement them with `java.util.zip`. Pack imports use `StickerImporter.prepare` (files only) and add every row at the end.

### Limits (constants in one place)

| Limit | Value |
|---|---|
| Max pack file size | 1 GB (user's call, 2026-11-22: a full backup of 1000+ stickers must fit) |
| Max single sticker (input) | 10 MB |
| Max stored side (still images) | 512 px |
| Max GIF canvas decoded | 2048 x 2048 px (4,194,304 px) |
| Max GIF frames | 5000 |
| Max GIF decode work | 1,000,000,000 px (canvas plus frame area, summed over frames) |
| Max stickers per pack | 10,000 |
| Max zip entries read | 12,000 |
| Max total uncompressed | 2 GB, counted from bytes actually read (guards against zip bombs; abort when exceeded) |
| Free space before import | pack size + 50 MB |
| Max manifest size | 2 MB |

### Failure behavior

- Wrap the whole import in a logical unit: if it fails or is cancelled midway, remove files written by this import and roll back inserted rows. A half-imported pack must not remain.
- How: convert and write sticker files first, keeping a list; insert collections and rows in one short transaction at the end; delete the listed files in a `finally` under `NonCancellable` when the import did not finish. Never hold a database write transaction while converting: the keyboard shares the database and would stall.
- Never crash on malformed input; show a plain error.

## Sharing (v1)

"Share a pack" is just export plus the system share sheet. The friend opens the file with the app. No direct Wi-Fi, QR or relay in v1 because they would need the INTERNET permission or a server.

## Tests

- Round trip: export then import into an empty DB yields equal collections and stickers.
- Duplicate import is idempotent.
- Zip-slip entry, oversized entry, zip bomb, wrong extension with valid PNG bytes, valid extension with garbage bytes, and future version number are each rejected or handled as specified.
- Nested depth over 3 gets flattened with a report.
- Failure (including a disk that fills up), and cancel midway leave no new rows and no new files.
- Same-name collections merge; a full export of a library at the limits imports cleanly.
