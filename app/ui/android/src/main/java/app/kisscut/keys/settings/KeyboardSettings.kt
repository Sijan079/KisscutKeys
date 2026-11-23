package app.kisscut.keys.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kisscut.keys.setup.settingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Keyboard height choices (Settings, Keyboard). [scale] multiplies the panel's normal height. */
enum class KeyboardHeight(val key: String, val scale: Float) {
    SMALL("small", 0.85f),
    NORMAL("normal", 1f),
    LARGE("large", 1.15f),
    ;

    companion object {
        /** Unknown or missing values mean [NORMAL]. */
        fun of(key: String?): KeyboardHeight = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** How the keyboard feels and looks (docs/KEYBOARD.md, Keyboard settings). Not part of backups. */
data class KeyboardSettings(
    val keySound: Boolean = false,
    val vibration: Boolean = true,
    val numberRow: Boolean = false,
    val height: KeyboardHeight = KeyboardHeight.NORMAL,
)

interface KeyboardSettingsStore {
    val settings: Flow<KeyboardSettings>
    suspend fun set(settings: KeyboardSettings)
}

/** The keyboard settings in the shared DataStore file (docs/DATA_MODEL.md, Settings). */
class DataStoreKeyboardSettingsStore(context: Context) : KeyboardSettingsStore {
    private val store = context.applicationContext.settingsStore
    private val defaults = KeyboardSettings()

    override val settings: Flow<KeyboardSettings> = store.data.map {
        KeyboardSettings(
            keySound = it[KEY_SOUND] ?: defaults.keySound,
            vibration = it[VIBRATION] ?: defaults.vibration,
            numberRow = it[NUMBER_ROW] ?: defaults.numberRow,
            height = KeyboardHeight.of(it[HEIGHT]),
        )
    }

    override suspend fun set(settings: KeyboardSettings) {
        store.edit {
            it[KEY_SOUND] = settings.keySound
            it[VIBRATION] = settings.vibration
            it[NUMBER_ROW] = settings.numberRow
            it[HEIGHT] = settings.height.key
        }
    }

    private companion object {
        val KEY_SOUND = booleanPreferencesKey("kb_key_sound")
        val VIBRATION = booleanPreferencesKey("kb_vibration")
        val NUMBER_ROW = booleanPreferencesKey("kb_number_row")
        val HEIGHT = stringPreferencesKey("kb_height")
    }
}
