package app.otium.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Toasts in the Otium style: ink on a small paper-colored card, inverted against the page, with
 * the page gutter at the sides. They dismiss themselves.
 */
@Composable
fun OtiumSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ) {
            Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
