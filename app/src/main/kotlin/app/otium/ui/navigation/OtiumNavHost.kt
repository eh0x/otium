package app.otium.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.otium.BuildConfig
import app.otium.ui.add.AddDestination
import app.otium.ui.add.AddMethod
import app.otium.ui.add.AddSheet
import app.otium.ui.add.confirm.ConfirmScreen
import app.otium.ui.add.isbn.IsbnEntryScreen
import app.otium.ui.add.scan.ScanScreen
import app.otium.ui.add.search.TitleSearchScreen
import app.otium.ui.backup.BackupScreen
import app.otium.ui.catalog.ThemeCatalogScreen
import app.otium.ui.detail.DetailScreen
import app.otium.ui.library.LibraryScreen
import app.otium.ui.reading.ReadingScreen
import app.otium.ui.theme.otiumFade
import app.otium.ui.transfer.ImportExportScreen

/**
 * The app shell and its navigation graph. The three tabs (Library, Reading, Settings) share a
 * bottom bar whose Add action opens the add sheet; every other screen is pushed full screen and
 * hides the bar. Screens fade into each other gently.
 */
@Composable
fun OtiumNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.let { destination ->
        TopLevelDestination.entries.firstOrNull { tab ->
            destination.hierarchy.any { it.hasRoute(tab.route::class) }
        }
    }
    var addOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                OtiumBottomBar(
                    selected = currentTab,
                    onSelect = { navController.navigateToTab(it) },
                    onAdd = { addOpen = true },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LibraryRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(otiumFade()) },
            exitTransition = { fadeOut(otiumFade()) },
            popEnterTransition = { fadeIn(otiumFade()) },
            popExitTransition = { fadeOut(otiumFade()) },
        ) {
            tabs(navController, onOpenAddSheet = { addOpen = true })
            composable<DetailRoute> { entry ->
                val bookId = entry.toRoute<DetailRoute>().bookId
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ConfirmRoute.edit(bookId)) },
                )
            }
            addFlow(navController)
            if (BuildConfig.DEBUG) {
                composable<CatalogRoute> { ThemeCatalogScreen() }
            }
        }
    }

    if (addOpen) {
        AddSheet(
            onChoose = { method ->
                addOpen = false
                navController.navigate(method.route())
            },
            onDismiss = { addOpen = false },
        )
    }
}

/** The three top-level destinations behind the bottom bar. */
private fun NavGraphBuilder.tabs(navController: NavHostController, onOpenAddSheet: () -> Unit) {
    composable<LibraryRoute> {
        LibraryScreen(
            onOpenBook = { navController.navigate(DetailRoute(it)) },
            onOpenAddSheet = onOpenAddSheet,
            onOpenCatalog = { navController.navigate(CatalogRoute) },
        )
    }
    composable<ReadingRoute> {
        ReadingScreen(onOpenBook = { navController.navigate(DetailRoute(it)) })
    }
    composable<SettingsRoute> {
        BackupScreen(onOpenImportExport = { navController.navigate(ImportExportRoute) })
    }
    composable<ImportExportRoute> { ImportExportScreen(onBack = { navController.popBackStack() }) }
}

/** Switches tabs the standard way: one copy of each tab, each keeping its own state. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AddMethod.route(): Any = when (this) {
    AddMethod.SCAN -> ScanRoute
    AddMethod.ISBN -> IsbnEntryRoute
    AddMethod.TITLE_SEARCH -> TitleSearchRoute
}

/**
 * The add flow: the three ways in, all ending on the confirm screen. Leaving the flow for a book
 * (just saved, or already owned) clears the flow from the back stack, so back returns to the
 * library.
 */
private fun NavGraphBuilder.addFlow(navController: NavHostController) {
    val openBook = { bookId: Long ->
        navController.navigate(DetailRoute(bookId)) { popUpTo<LibraryRoute>() }
    }
    val goTo = { destination: AddDestination ->
        when (destination) {
            is AddDestination.Confirm -> navController.navigate(
                ConfirmRoute.from(destination.candidate),
            )

            is AddDestination.Book -> openBook(destination.bookId)
        }
    }
    composable<ScanRoute> {
        ScanScreen(
            onBack = { navController.popBackStack() },
            onEnterIsbn = {
                navController.navigate(IsbnEntryRoute) { popUpTo<ScanRoute> { inclusive = true } }
            },
            onDestination = goTo,
        )
    }
    composable<IsbnEntryRoute> {
        IsbnEntryScreen(onBack = { navController.popBackStack() }, onDestination = goTo)
    }
    composable<TitleSearchRoute> {
        TitleSearchScreen(onBack = { navController.popBackStack() }, onDestination = goTo)
    }
    composable<ConfirmRoute> { entry ->
        val isEdit = entry.toRoute<ConfirmRoute>().isEdit
        ConfirmScreen(
            onBack = { navController.popBackStack() },
            onSaved = { bookId -> if (isEdit) navController.popBackStack() else openBook(bookId) },
            onOpenBook = openBook,
        )
    }
}
