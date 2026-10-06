package app.otium.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.Book
import app.otium.domain.model.BookStatus
import app.otium.ui.components.OtiumProgressBar

private const val MAX_PAGE_DIGITS = 6

/** Where the reader is in the book, with the two ways to change it. */
@Composable
internal fun ProgressCard(book: Book, actions: DetailActions) {
    var editingPage by rememberSaveable { mutableStateOf(false) }
    var editingTotal by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_progress),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (book.totalPages > 0) {
                KnownProgress(
                    book = book,
                    onUpdate = { editingPage = true },
                    onMarkFinished = actions.onMarkFinished,
                )
            } else {
                Text(
                    text = stringResource(R.string.progress_total_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { editingTotal = true }) {
                    Text(stringResource(R.string.progress_set_total))
                }
            }
        }
    }
    if (editingPage) {
        ProgressDialog(
            current = book.currentPage,
            total = book.totalPages,
            onConfirm = {
                editingPage = false
                actions.onProgressConfirmed(it)
            },
            onDismiss = { editingPage = false },
        )
    }
    if (editingTotal) {
        TotalPagesDialog(
            onConfirm = {
                editingTotal = false
                actions.onTotalPagesConfirmed(it)
            },
            onDismiss = { editingTotal = false },
        )
    }
}

@Composable
private fun KnownProgress(book: Book, onUpdate: () -> Unit, onMarkFinished: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.progress_page_of, book.currentPage, book.totalPages),
            style = MaterialTheme.typography.bodyLarge,
        )
        OtiumProgressBar(progress = book.currentPage.toFloat() / book.totalPages)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUpdate) { Text(stringResource(R.string.progress_update)) }
            if (book.status != BookStatus.READ) {
                Button(onClick = onMarkFinished) {
                    Text(stringResource(R.string.progress_mark_finished))
                }
            }
        }
    }
}

/** A typed page with a slider beside it, for both exact and rough adjustments. */
@Composable
private fun ProgressDialog(
    current: Int,
    total: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val page = text.toIntOrNull()
    val sliderState = stringResource(R.string.progress_page_of, page ?: 0, total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.progress_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.digitsOnly() },
                    label = { Text(stringResource(R.string.progress_page_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Slider(
                    value = (page ?: 0).coerceIn(0, total).toFloat(),
                    onValueChange = { text = it.toInt().toString() },
                    valueRange = 0f..total.toFloat(),
                    modifier = Modifier.semantics { stateDescription = sliderState },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onConfirm) }, enabled = page != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TotalPagesDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val total = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.progress_total_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.digitsOnly() },
                label = { Text(stringResource(R.string.progress_total_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { total?.let(onConfirm) }, enabled = total != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun String.digitsOnly(): String = filter { it.isDigit() }.take(MAX_PAGE_DIGITS)
