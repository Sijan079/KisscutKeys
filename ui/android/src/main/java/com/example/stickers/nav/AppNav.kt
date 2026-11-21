package com.example.stickers.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.stickers.collections.CollectionDetailScreen
import com.example.stickers.collections.CollectionDetailViewModel
import com.example.stickers.collections.CollectionTarget
import com.example.stickers.collections.CollectionsScreen
import com.example.stickers.collections.CollectionsViewModel
import com.example.stickers.common.LibraryBackend
import com.example.stickers.common.TopLevel
import com.example.stickers.library.LibraryScreen
import com.example.stickers.library.LibraryViewModel
import com.example.stickers.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val UNSORTED = "unsorted"
    const val COLLECTION = "collection/{id}"

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

/** Single-activity navigation (UI_SPEC): Library, Collections, Settings, plus Inside a collection. */
@Composable
fun AppNav(backend: LibraryBackend, onAdd: () -> Unit, nav: NavHostController = rememberNavController()) {
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

    NavHost(nav, startDestination = Routes.LIBRARY) {
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
        composable(Routes.SETTINGS) { SettingsScreen(onNavigate = ::go, onAdd = onAdd) }
        composable(Routes.FAVORITES) { Detail(CollectionTarget.Favorites) }
        composable(Routes.UNSORTED) { Detail(CollectionTarget.Unsorted) }
        composable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            Detail(CollectionTarget.Collection(entry.arguments?.getLong("id") ?: 0L))
        }
    }
}
