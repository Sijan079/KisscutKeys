package app.kisscut.keys.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kisscut.keys.setup.settingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Settings, Theme (docs/UI_SPEC.md): the app and the keyboard follow it. [key] is also the pack value. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        LIGHT -> false
    }

    companion object {
        /** Unknown or missing values mean [SYSTEM]. */
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

interface ThemeStore {
    val theme: Flow<ThemeMode>
    suspend fun setTheme(mode: ThemeMode)
}

/** The theme in the shared DataStore file, key `theme` (docs/DATA_MODEL.md, Settings). */
class DataStoreThemeStore(context: Context) : ThemeStore {
    private val store = context.applicationContext.settingsStore

    override val theme: Flow<ThemeMode> = store.data.map { ThemeMode.of(it[THEME]) }

    override suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.key }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
