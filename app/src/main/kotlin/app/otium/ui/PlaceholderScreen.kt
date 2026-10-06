package app.otium.ui

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
import app.otium.BuildConfig
import app.otium.R
import app.otium.ui.catalog.ThemeCatalogScreen
import app.otium.ui.components.OtiumWordmark
import app.otium.ui.theme.PaperBackground

/**
 * Temporary entry screen, replaced by the library screen in Phase 3. Debug builds show the
 * theme catalog instead, so the identity can be reviewed on a device.
 */
@Composable
fun PlaceholderScreen() {
    if (BuildConfig.DEBUG) {
        ThemeCatalogScreen()
    } else {
        PaperBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OtiumWordmark()
                Text(
                    text = stringResource(R.string.placeholder_tagline),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
