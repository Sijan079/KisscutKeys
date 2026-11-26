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

/** Swipe trail thickness choices (Settings, Keyboard, under Swipe typing). [dp] is the trail's width at its head. */
enum class TrailThickness(val key: String, val dp: Float) {
    THIN("thin", 8f),
    MEDIUM("medium", 12f),
    THICK("thick", 18f),
    ;

    companion object {
        /** Unknown or missing values mean [MEDIUM]. */
        fun of(key: String?): TrailThickness = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** How the keyboard feels and looks (docs/KEYBOARD.md, Keyboard settings). Not part of backups. */
data class KeyboardSettings(
    val keySound: Boolean = false,
    val vibration: Boolean = true,
    val numberRow: Boolean = false,
    val height: KeyboardHeight = KeyboardHeight.NORMAL,

    /** A row above the letters with Stickers, Emoji, Settings and Switch keyboard. */
    val toolbar: Boolean = false,

    /** Fix the word on space or punctuation. Off until a Tagalog list lands, so Taglish isn't "fixed". */
    val autocorrect: Boolean = false,

    /** Words to tap above the letters; always on while [autocorrect] is (see [showsSuggestions]). */
    val suggestions: Boolean = true,

    /** Learn words the user types that the word list lacks (docs/KEYBOARD.md, Learned words). Next field. */
    val learnWords: Boolean = true,

    /** Trace across the letters to type a word. Read when a swipe starts, so a change applies at once. */
    val swipe: Boolean = true,

    /** How thick the swipe trail is; read when a swipe starts. */
    val trail: TrailThickness = TrailThickness.MEDIUM,
) {
    /** Autocorrect on forces suggestions on, whatever their own switch says (user's call, 2026-11-25). */
    val showsSuggestions: Boolean get() = autocorrect || suggestions
}

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
            toolbar = it[TOOLBAR] ?: defaults.toolbar,
            autocorrect = it[AUTOCORRECT] ?: defaults.autocorrect,
            suggestions = it[SUGGESTIONS] ?: defaults.suggestions,
            learnWords = it[LEARN_WORDS] ?: defaults.learnWords,
            swipe = it[SWIPE] ?: defaults.swipe,
            trail = TrailThickness.of(it[TRAIL]),
        )
    }

    override suspend fun set(settings: KeyboardSettings) {
        store.edit {
            it[KEY_SOUND] = settings.keySound
            it[VIBRATION] = settings.vibration
            it[NUMBER_ROW] = settings.numberRow
            it[HEIGHT] = settings.height.key
            it[TOOLBAR] = settings.toolbar
            it[AUTOCORRECT] = settings.autocorrect
            it[SUGGESTIONS] = settings.suggestions
            it[LEARN_WORDS] = settings.learnWords
            it[SWIPE] = settings.swipe
            it[TRAIL] = settings.trail.key
        }
    }

    private companion object {
        val KEY_SOUND = booleanPreferencesKey("kb_key_sound")
        val VIBRATION = booleanPreferencesKey("kb_vibration")
        val NUMBER_ROW = booleanPreferencesKey("kb_number_row")
        val HEIGHT = stringPreferencesKey("kb_height")
        val TOOLBAR = booleanPreferencesKey("kb_toolbar")
        val AUTOCORRECT = booleanPreferencesKey("kb_autocorrect")
        val SUGGESTIONS = booleanPreferencesKey("kb_suggestions")
        val LEARN_WORDS = booleanPreferencesKey("kb_learn_words")
        val SWIPE = booleanPreferencesKey("kb_swipe")
        val TRAIL = stringPreferencesKey("kb_swipe_trail")
    }
}
