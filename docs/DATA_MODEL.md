# DATA_MODEL.md

Room (KMP) database shared by the app and the keyboard. Collections organise stickers; there are no tags in v1.

## Entities

### StickerCollection
| Column | Type | Notes |
|---|---|---|
| id | Long PK autogenerate | |
| name | String | not blank, trimmed |
| parentId | Long? | self FK, ON DELETE CASCADE handled in repository (see delete rules) |
| sortOrder | Int | position among siblings |
| createdAt | Long | epoch ms |

Index on `parentId`.

### Sticker
| Column | Type | Notes |
|---|---|---|
| id | Long PK autogenerate | |
| filePath | String | relative to `files/stickers/` |
| thumbPath | String | relative to `files/thumbs/` |
| mimeType | String | type of the **stored** file: usually image/webp; also image/png (API 26-28 or kept original), image/jpeg (small JPEG kept), image/gif. See IMPORT_EXPORT, Stored format |
| isAnimated | Boolean | |
| hasAlpha | Boolean | true when the image has transparency (decides cut-line style) |
| hash | String | SHA-256 of the bytes **as received**, before conversion, **unique index** (dedupe) |
| collectionId | Long? | FK to StickerCollection, ON DELETE SET NULL; null = Unsorted |
| isFavorite | Boolean | default false |
| useCount | Int | default 0 |
| lastUsedAt | Long? | |
| addedAt | Long | |

Indexes: unique `hash`; `collectionId`; `isFavorite`; `lastUsedAt`.

`hasAlpha` drives the UI: transparent stickers get the white cut line and shadow; opaque images and GIFs get a plain rounded tile. Compute once at import, store it, never recompute while scrolling.

## DAOs

### CollectionDao
- `observeChildren(parentId: Long?)`: Flow of collections with direct sticker count, ordered by `sortOrder`.
- `observeById(id)`
- `insert`, `rename`, `move(id, newParentId, newSortOrder)`, `reorder`
- `descendantIds(id)`: recursive CTE (below)
- `totalStickerCount(id)`: counts stickers in the collection and all descendants
- `delete(id)`

### StickerDao
- `observeAll()`, `observeInCollection(id)`, `observeUnsorted()`, `observeFavorites()`, `observeRecents(limit)`
- `findByHash(hash)`
- `insert` with `OnConflictStrategy.IGNORE` (returns -1 on duplicate)
- `moveTo(ids, collectionId?)`
- `setFavorite(ids, value)`
- `markUsed(id, now)`: increments `useCount`, sets `lastUsedAt`
- `delete(ids)`
- `allOnce()`: one-shot list for export

## Nesting

- Depth cap: **3** levels (root = depth 1). Enforced in `CollectionRepository`; reject moves and creates that would exceed it, and reject moves that would create a cycle (moving a collection into its own descendant).
- Recursive CTE for descendants:

```sql
WITH RECURSIVE tree(id) AS (
  SELECT id FROM StickerCollection WHERE id = :rootId
  UNION ALL
  SELECT c.id FROM StickerCollection c JOIN tree t ON c.parentId = t.id
)
SELECT id FROM tree
```

- "All stickers in a collection and below" = stickers whose `collectionId` is in that set.

## Delete rules

Deleting a collection asks the user to pick one:
1. **Collection only:** re-parent child collections to the deleted collection's parent, and move its stickers to the parent (or Unsorted if the parent is null). No stickers are lost.
2. **Delete everything:** delete the collection, all descendants, and all stickers inside them, including their files and thumbnails.

Deleting stickers always removes the files from disk in the same operation. If a file delete fails, log it and keep going; a startup sweep removes orphan files not referenced by any row.

## Rules to enforce in repositories

- Collection names are trimmed and non-blank. Sibling names don't need to be unique.
- Importing a sticker whose hash exists is a no-op that returns the existing sticker (optionally moving it into the chosen collection).
- `markUsed` is called after a successful insert into a chat app, not on tap.

## Migrations

- Schema export on (`exportSchema = true`), JSON files committed under `shared/db/schemas/`.
- Version 1 is the first release shape above. If `parentId` or `hasAlpha` are added after an early build, write a real migration; never use destructive fallback.
- Each migration has a test using `MigrationTestHelper` with old data.

## Settings (not in Room)

DataStore keys: `theme` (system, dark, light), `animateStickers` (bool), `keyboardHintShown` (bool). Exported inside the backup only if the user ticks "include settings".
