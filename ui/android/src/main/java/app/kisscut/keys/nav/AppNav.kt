package app.kisscut.keys.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kisscut.keys.collections.CollectionDetailScreen
import app.kisscut.keys.collections.CollectionDetailViewModel
import app.kisscut.keys.collections.CollectionTarget
import app.kisscut.keys.collections.CollectionsScreen
import app.kisscut.keys.collections.CollectionsViewModel
import app.kisscut.keys.common.LibraryBackend
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.library.LibraryScreen
import app.kisscut.keys.library.LibraryViewModel
import app.kisscut.keys.settings.SettingsActions
import app.kisscut.keys.settings.SettingsScreen
import app.kisscut.keys.settings.SettingsUiState

object Routes {
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val UNSORTED = "unsorted"
    const val COLLECTION = "collection/{id}"
    const val SETUP = "setup"

    fun of(top: TopLevel) = when (top) {
        TopLevel.LIBRARY -> LIBRARY
        TopLevel.COLLECTIONS -> COLLECTIONS
        TopLevel.SETTINGS -> SETTINGS
    }

    fun of(target: CollectionTarget) = when (target) {
        is CollectionTarget.Collection -> "collection/${target.id}"
        CollectionTarget.Favorites -> FAVORITES
        CollectionTarget.Unsorted -> UNSORTED
    }
}

/**
 * Single-activity navigation (UI_SPEC): Library, Collections, Settings, Inside a collection, and Keyboard
 * setup. [setupScreen] draws the setup screen and calls its argument to leave; [startAtSetup] opens it first.
 */
@Composable
fun AppNav(
    backend: LibraryBackend,
    onAdd: () -> Unit,
    nav: NavHostController = rememberNavController(),
    startAtSetup: Boolean = false,
    setupScreen: @Composable (onLeave: () -> Unit) -> Unit = {},
    /** The real Settings screen with its view models; tests may leave it out for a plain one. */
    settingsScreen: (@Composable (onNavigate: (TopLevel) -> Unit, onKeyboardSetup: () -> Unit) -> Unit)? = null,
) {
    fun go(top: TopLevel) = nav.navigate(Routes.of(top)) {
        popUpTo(Routes.LIBRARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun open(target: CollectionTarget) = nav.navigate(Routes.of(target))

    fun toCollectionsRoot() {
        if (!nav.popBackStack(Routes.COLLECTIONS, inclusive = false)) {
            // Opened from elsewhere (no list below): replace the stack with a fresh list.
            nav.navigate(Routes.COLLECTIONS) {
                popUpTo(Routes.LIBRARY)
                launchSingleTop = true
            }
        }
    }

    @Composable
    fun Detail(target: CollectionTarget) {
        val vm = viewModel { CollectionDetailViewModel(backend, target) }
        CollectionDetailScreen(
            vm,
            onBack = { nav.popBackStack() },
            onOpen = ::open,
            onCollectionsRoot = ::toCollectionsRoot,
            // Collections is the active tab here: tapping it returns to the list, not this saved screen.
            onNavigate = { if (it == TopLevel.COLLECTIONS) toCollectionsRoot() else go(it) },
            onAdd = onAdd,
        )
    }

    NavHost(nav, startDestination = if (startAtSetup) Routes.SETUP else Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(viewModel { LibraryViewModel(backend) }, onNavigate = ::go, onAdd = onAdd)
        }
        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                viewModel { CollectionsViewModel(backend) },
                onOpen = ::open,
                onNavigate = ::go,
                onAdd = onAdd,
            )
        }
        composable(Routes.SETTINGS) {
            val openSetup = { nav.navigate(Routes.SETUP) }
            if (settingsScreen != null) {
                settingsScreen(::go, openSetup)
            } else {
                SettingsScreen(SettingsUiState(), SettingsActions(onKeyboardSetup = openSetup), ::go, onAdd)
            }
        }
        composable(Routes.SETUP) {
            setupScreen {
                // Leaving goes to the Library, whether setup was the start screen or opened from Settings.
                if (nav.previousBackStackEntry != null) {
                    nav.popBackStack()
                } else {
                    nav.navigate(Routes.LIBRARY) { popUpTo(Routes.SETUP) { inclusive = true } }
                }
            }
        }
        composable(Routes.FAVORITES) { Detail(CollectionTarget.Favorites) }
        composable(Routes.UNSORTED) { Detail(CollectionTarget.Unsorted) }
        composable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            Detail(CollectionTarget.Collection(entry.arguments?.getLong("id") ?: 0L))
        }
    }
}
