package app.otium.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.otium.BuildConfig
import app.otium.ui.add.AddDestination
import app.otium.ui.add.AddMethod
import app.otium.ui.add.confirm.ConfirmScreen
import app.otium.ui.add.isbn.IsbnEntryScreen
import app.otium.ui.add.scan.ScanScreen
import app.otium.ui.add.search.TitleSearchScreen
import app.otium.ui.catalog.ThemeCatalogScreen
import app.otium.ui.detail.DetailScreen
import app.otium.ui.library.LibraryScreen
import app.otium.ui.theme.otiumFade
import app.otium.ui.theme.rememberReducedMotion

/**
 * The app's navigation graph. Screens fade into each other gently, and not at all when the
 * system asks for reduced motion.
 */
@Composable
fun OtiumNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val reducedMotion = rememberReducedMotion()
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
        enterTransition = { fadeIn(otiumFade(reducedMotion)) },
        exitTransition = { fadeOut(otiumFade(reducedMotion)) },
        popEnterTransition = { fadeIn(otiumFade(reducedMotion)) },
        popExitTransition = { fadeOut(otiumFade(reducedMotion)) },
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenBook = { navController.navigate(DetailRoute(it)) },
                onAddBook = { navController.navigate(it.route()) },
                onOpenCatalog = { navController.navigate(CatalogRoute) },
            )
        }
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
