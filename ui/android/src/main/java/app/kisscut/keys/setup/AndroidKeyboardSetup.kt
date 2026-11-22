package app.kisscut.keys.setup

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.kisscut.keys.keyboard.StickerImeService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Reads the keyboard's state from the system. Watches the enabled and default keyboard settings, because
 * picking a keyboard from the system picker is a dialog: the activity is never paused, so a resume check
 * alone would miss it.
 */
class AndroidKeyboardStatus(context: Context) : KeyboardStatusSource {
    private val context = context.applicationContext
    private val component = ComponentName(this.context, StickerImeService::class.java)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun read(): KeyboardStatus {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.component == component }
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current?.let(ComponentName::unflattenFromString) == component
        return KeyboardStatus(enabled, selected)
    }

    override fun observe(): Flow<KeyboardStatus> {
        val changes = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            val resolver = context.contentResolver
            for (key in listOf(Settings.Secure.ENABLED_INPUT_METHODS, Settings.Secure.DEFAULT_INPUT_METHOD)) {
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
        return merge(changes, refreshes).onStart { emit(Unit) }.map { read() }.distinctUntilChanged()
    }

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** Opens the system list of keyboards (step 1). */
    fun openSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Shows the system keyboard picker (step 2). */
    fun showPicker() = context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Settings in DataStore (docs/ARCHITECTURE.md). For now only the setup flag; Settings (M5) adds more. */
class DataStoreSetupStore(context: Context) : SetupStore {
    private val store = context.applicationContext.settingsStore

    override val dismissed: Flow<Boolean> = store.data.map { it[DISMISSED] ?: false }

    override suspend fun dismiss() {
        store.edit { it[DISMISSED] = true }
    }

    private companion object {
        val DISMISSED = booleanPreferencesKey("keyboard_setup_dismissed")
    }
}
