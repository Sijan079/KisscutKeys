package app.kisscut.keys.shared.db

import android.content.Context
import androidx.room.Room

/**
 * The one on-disk database for the app and the keyboard. Build it once (AppContainer) and share it;
 * never open a second database file.
 */
fun createStickerDatabase(context: Context): StickerDatabase {
    val appContext = context.applicationContext
    val path = appContext.getDatabasePath(DatabaseInfo.FILE_NAME).absolutePath
    return Room.databaseBuilder<StickerDatabase>(context = appContext, name = path).buildStickerDatabase()
}
