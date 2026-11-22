package app.kisscut.keys.shared.core

/**
 * MIME type match with wildcards, the same rules as Android's `ClipDescription.compareMimeTypes`:
 * `*` + `/` + `*` matches anything, `image/` + `*` matches any image, otherwise the whole type must match
 * (case-insensitive).
 */
fun mimeMatches(concrete: String, pattern: String): Boolean {
    val c = concrete.lowercase().split('/', limit = 2)
    val p = pattern.lowercase().split('/', limit = 2)
    if (c.size != 2 || p.size != 2) return false
    if (p[0] == "*") return p[1] == "*"
    if (p[0] != c[0]) return false
    return p[1] == "*" || p[1] == c[1]
}

/** How the keyboard sends one sticker (docs/KEYBOARD.md, Inserting a sticker). */
enum class InsertRoute {
    /** The field accepts the stored type: commit the stored file. */
    COMMIT,

    /** A still sticker whose stored type the field rejects, but it takes PNG: commit a PNG copy. */
    COMMIT_AS_PNG,

    /** The field takes no images (or none of ours): copy to the clipboard, hint "paste to send". */
    CLIPBOARD,

    /** An animated sticker the field rejects: copy to the clipboard and say the app does not take it. */
    CLIPBOARD_TYPE_REJECTED,

    /** Password field: nothing is sent or copied. */
    BLOCKED,
}

/**
 * Picks the route for a sticker of [mimeType] into a field that accepts [accepted] MIME types
 * (possibly with wildcards; empty = no images).
 */
fun planInsert(mimeType: String, isAnimated: Boolean, accepted: List<String>, isPassword: Boolean): InsertRoute {
    if (isPassword) return InsertRoute.BLOCKED
    if (accepted.any { mimeMatches(mimeType, it) }) return InsertRoute.COMMIT
    if (!isAnimated && accepted.any { mimeMatches(PNG, it) }) return InsertRoute.COMMIT_AS_PNG
    if (isAnimated && accepted.isNotEmpty()) return InsertRoute.CLIPBOARD_TYPE_REJECTED
    return InsertRoute.CLIPBOARD
}

private const val PNG = "image/png"
