package app.kisscut.keys.shared.db

/**
 * Facts about the single database shared by the app and the keyboard.
 * The Room database itself arrives in M1.
 */
object DatabaseInfo {
    /** The one database file. The keyboard and the app must never open a second one. */
    const val FILE_NAME: String = "stickers.db"
}
