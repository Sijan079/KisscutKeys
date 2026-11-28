package app.kisscut.keys.licenses

import android.content.res.AssetManager
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kisscut.keys.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One open-source part the app ships, with the license file in `assets/licenses/`. */
data class License(@StringRes val title: Int, @StringRes val about: Int, val file: String)

/** Everything bundled in the APK that asks for its license to be shown (docs/UI_SPEC.md, Open-source licenses). */
val BUNDLED_LICENSES = listOf(
    License(R.string.licenses_words, R.string.licenses_words_about, "Apache-2.0-AOSP-LatinIME.txt"),
    License(R.string.licenses_pairs, R.string.licenses_pairs_about, "CC-BY-3.0-Google-Books-Ngram.txt"),
    License(R.string.licenses_libraries, R.string.licenses_libraries_about, "Apache-2.0.txt"),
    License(R.string.licenses_bricolage, R.string.licenses_font_about, "OFL-BricolageGrotesque.txt"),
    License(R.string.licenses_figtree, R.string.licenses_font_about, "OFL-Figtree.txt"),
)

data class LicensesUiState(
    val licenses: List<License> = BUNDLED_LICENSES,
    /** The license shown in full, by file; null shows the list. */
    val open: License? = null,
    /** Text of [open]; null while it loads. */
    val text: String? = null,
)

/** The Open-source licenses screen: a list, and one license's full text. [read] loads a file from the assets. */
class LicensesViewModel(private val read: suspend (String) -> String) : ViewModel() {
    private val _state = MutableStateFlow(LicensesUiState())
    val state: StateFlow<LicensesUiState> = _state.asStateFlow()

    fun open(license: License) {
        _state.update { it.copy(open = license, text = null) }
        viewModelScope.launch {
            val text = read(license.file)
            _state.update { if (it.open == license) it.copy(text = text) else it }
        }
    }

    /** Back from a license's text to the list. Returns false when the list is showing (leave the screen). */
    fun back(): Boolean {
        if (_state.value.open == null) return false
        _state.update { it.copy(open = null, text = null) }
        return true
    }

    companion object {
        fun assetReader(assets: AssetManager): suspend (String) -> String = { file ->
            withContext(Dispatchers.IO) { assets.open("licenses/$file").bufferedReader().use { it.readText() } }
        }
    }
}
