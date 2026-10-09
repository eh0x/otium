package app.otium.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.ui.theme.OtiumFonts
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private const val WORDMARK_TRACKING = 0.42f
private val WORDMARK_SIZE = 17.sp

/**
 * "OTIUM" in Cinzel, a Roman inscription face, widely spaced in gold. Tracking adds space after
 * the last letter too, so the same space goes before the first to keep the word optically
 * centred. The final mark is a design asset and can replace this composable without touching
 * its callers.
 */
@Composable
fun OtiumWordmark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        modifier = modifier.padding(
            start = with(LocalDensity.current) {
                (WORDMARK_SIZE * WORDMARK_TRACKING).toDp()
            },
        ),
        color = MaterialTheme.colorScheme.primary,
        style = TextStyle(
            fontFamily = OtiumFonts.Wordmark,
            fontWeight = FontWeight.SemiBold,
            fontSize = WORDMARK_SIZE,
            letterSpacing = WORDMARK_TRACKING.em,
        ),
    )
}

/** The library masthead: the wordmark over its subtitle, centred. */
@Composable
fun OtiumMasthead(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OtiumWordmark()
        Text(
            text = stringResource(R.string.library_subtitle).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.em),
            color = MaterialTheme.otiumColors.muted,
        )
    }
}

@PreviewLightDark
@Composable
private fun OtiumMastheadPreview() {
    OtiumTheme { PaperBackground { OtiumMasthead() } }
}
