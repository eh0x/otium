package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.SeriesProgress
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors
import kotlin.math.roundToInt

private const val PERCENT = 100

/** "2 of 5 read", for the books of a series in the library. */
@Composable
internal fun seriesProgressLabel(progress: SeriesProgress): String =
    pluralStringResource(R.plurals.series_progress, progress.books, progress.read, progress.books)

/**
 * How far the owner is through a series: "2 of 5 read" over a thin rule. Read aloud as one
 * phrase with the percentage, since the rule alone says nothing to a screen reader.
 */
@Composable
internal fun SeriesProgressLine(progress: SeriesProgress, modifier: Modifier = Modifier) {
    val label = seriesProgressLabel(progress)
    val spoken = stringResource(
        R.string.series_progress_description,
        label,
        (progress.fraction * PERCENT).roundToInt(),
    )
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = MaterialTheme.otiumColors.muted,
        )
        OtiumProgressBar(progress = progress.fraction.toFloat())
    }
}

/** The chosen series above its books: its name, then its progress. */
@Composable
internal fun SeriesSummary(
    progress: SeriesProgress,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 22.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = progress.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        SeriesProgressLine(progress)
    }
}
