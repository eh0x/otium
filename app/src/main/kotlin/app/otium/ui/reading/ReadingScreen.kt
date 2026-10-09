package app.otium.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/**
 * The Reading tab: the books on the desk. For now it shows only its heading and the empty state;
 * the list of books in progress arrives with the Reading screen proper.
 */
@Composable
fun ReadingScreen(modifier: Modifier = Modifier) {
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reading_kicker).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.reading_title),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.reading_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.otiumColors.muted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadingScreenPreview() {
    OtiumTheme { ReadingScreen() }
}
