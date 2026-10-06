package app.otium.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.otium.BuildConfig
import app.otium.R
import app.otium.ui.catalog.ThemeCatalogScreen
import app.otium.ui.detail.DetailScreen
import app.otium.ui.library.LibraryScreen
import app.otium.ui.theme.PaperBackground
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
                onAddBook = { _ -> navController.navigate(AddRoute) },
                onOpenCatalog = { navController.navigate(CatalogRoute) },
            )
        }
        composable<DetailRoute> {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable<AddRoute> {
            AddBookStub()
        }
        if (BuildConfig.DEBUG) {
            composable<CatalogRoute> { ThemeCatalogScreen() }
        }
    }
}

/** Stands in for the add flow until it is built. */
@Composable
private fun AddBookStub() {
    PaperBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.add_book_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.add_book_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
