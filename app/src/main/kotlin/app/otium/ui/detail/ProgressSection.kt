package app.otium.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.Book
import app.otium.domain.model.BookStatus
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.OtiumTextField
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors
import kotlin.math.roundToInt

private const val MAX_PAGE_DIGITS = 6
private const val PERCENT = 100
private val SLIDER_THUMB = 18.dp

/** One of the four page steppers: how far it moves and how it reads. */
private data class Step(val pages: Int, @StringRes val label: Int, @StringRes val description: Int)

private val STEPS = listOf(
    Step(-10, R.string.progress_back_10, R.string.progress_back_10_description),
    Step(-1, R.string.progress_back_1, R.string.progress_back_1_description),
    Step(1, R.string.progress_forward_1, R.string.progress_forward_1_description),
    Step(10, R.string.progress_forward_10, R.string.progress_forward_10_description),
)

/**
 * Where the reader is in the book, changed in place: a slider that saves when released, page
 * steppers and "Mark as finished" that save at once. Without a page count, it offers to set one.
 */
@Composable
internal fun ProgressSection(book: Book, actions: DetailActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.detail_progress).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier.semantics { heading() },
        )
        if (book.totalPages > 0) {
            KnownProgress(book = book, actions = actions)
        } else {
            UnknownTotal(onConfirm = actions.onTotalPagesConfirmed)
        }
    }
}

@Composable
private fun KnownProgress(book: Book, actions: DetailActions) {
    val total = book.totalPages
    // The page shown while the slider moves; reset whenever the saved page changes.
    var page by remember(book.currentPage) { mutableIntStateOf(book.currentPage) }
    val save = { target: Int ->
        page = target.coerceIn(0, total)
        actions.onProgressConfirmed(page)
    }

    PageNumerals(page = page, total = total)
    OtiumProgressBar(
        progress = page.toFloat() / total,
        thickness = 3.dp,
        ribbon = true,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    PageSlider(
        page = page,
        total = total,
        onChange = { page = it },
        onFinished = { actions.onProgressConfirmed(page) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        STEPS.forEach { step ->
            OtiumButton(
                text = stringResource(step.label),
                onClick = { save(page + step.pages) },
                style = OtiumButtonStyle.Secondary,
                contentDescription = stringResource(step.description),
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (book.status != BookStatus.READ) {
        OtiumButton(
            text = stringResource(R.string.progress_mark_finished),
            onClick = actions.onMarkFinished,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

/** The page slider: moves the page live and saves through [onFinished] once released. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(page: Int, total: Int, onChange: (Int) -> Unit, onFinished: () -> Unit) {
    val sliderLabel = stringResource(R.string.progress_current_page)
    val sliderState = stringResource(R.string.progress_page_of, page, total)
    val colors = MaterialTheme.otiumColors
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.divider,
    )
    Slider(
        value = page.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        onValueChangeFinished = onFinished,
        valueRange = 0f..total.toFloat(),
        colors = sliderColors,
        // A quiet slider: a thin rule and a round accent knob, in keeping with the progress rule.
        thumb = {
            Box(
                modifier = Modifier
                    .size(SLIDER_THUMB)
                    .background(colors.accent, CircleShape),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = sliderColors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                modifier = Modifier.height(2.dp),
            )
        },
        modifier = Modifier.semantics {
            contentDescription = sliderLabel
            stateDescription = sliderState
        },
    )
}

/** The page in large figures, "of 304 pages", and the percentage at the right. */
@Composable
private fun PageNumerals(page: Int, total: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = page.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = pluralStringResource(R.plurals.progress_of_pages, total, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier
                .padding(start = 8.dp)
                .alignByBaseline(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.progress_percent, page * PERCENT / total),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun UnknownTotal(onConfirm: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.progress_total_unknown),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    OtiumButton(
        text = stringResource(R.string.progress_set_total),
        onClick = { editing = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    if (editing) {
        TotalPagesDialog(
            onConfirm = {
                editing = false
                onConfirm(it)
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun TotalPagesDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val total = text.toIntOrNull()?.takeIf { it > 0 }
    OtiumDialog(
        title = stringResource(R.string.progress_total_dialog_title),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.action_save),
                onClick = { total?.let(onConfirm) },
                enabled = total != null,
            )
        },
    ) {
        OtiumTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
            label = stringResource(R.string.progress_total_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
