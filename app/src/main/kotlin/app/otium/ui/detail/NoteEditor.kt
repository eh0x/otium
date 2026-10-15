package app.otium.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumChip
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumTextField
import app.otium.ui.theme.otiumColors

private const val MAX_PAGE_DIGITS = 6

/** The counter appears when a note is close to [Note.MAX_TEXT_LENGTH]. */
private const val COUNTER_FROM = 9_000
private val EDITOR_MAX_HEIGHT = 380.dp

/** What the note editor hands back. [editing] is the note being changed, or null for a new one. */
@Immutable
data class NoteDraft(val editing: Note?, val kind: NoteKind, val text: String, val page: Int?)

/** What the owner has typed so far; the page stays text until it is saved. */
@Immutable
private data class NoteForm(val kind: NoteKind, val text: String, val pageText: String) {
    val trimmed: String get() = text.trim()
    val page: Int? get() = pageText.toIntOrNull()
    val pageOk: Boolean get() = pageText.isEmpty() || (page ?: 0) >= 1
    val textOk: Boolean get() = trimmed.isNotEmpty() && trimmed.length <= Note.MAX_TEXT_LENGTH

    companion object {
        fun of(note: Note?) = NoteForm(
            kind = note?.kind ?: NoteKind.NOTE,
            text = note?.text.orEmpty(),
            pageText = note?.page?.toString().orEmpty(),
        )

        /** Survives rotation and the process being recreated. */
        val Saver = listSaver<NoteForm, String>(
            save = { listOf(it.kind.name, it.text, it.pageText) },
            restore = { NoteForm(NoteKind.valueOf(it[0]), it[1], it[2]) },
        )
    }
}

internal fun NoteKind.labelRes(): Int = when (this) {
    NoteKind.NOTE -> R.string.notes_kind_note
    NoteKind.QUOTE -> R.string.notes_kind_quote
}

@Composable
internal fun DeleteNoteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OtiumDialog(
        title = stringResource(R.string.notes_delete_title),
        body = stringResource(R.string.notes_delete_message),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(text = stringResource(R.string.action_delete), onClick = onConfirm)
        },
    )
}

/** The editor for a new note ([initial] null) or an existing one. Save needs usable text. */
@Composable
internal fun NoteDialog(initial: Note?, onConfirm: (NoteDraft) -> Unit, onDismiss: () -> Unit) {
    var form by rememberSaveable(stateSaver = NoteForm.Saver) {
        mutableStateOf(NoteForm.of(initial))
    }
    val title = if (initial == null) R.string.notes_new_title else R.string.notes_edit_title
    OtiumDialog(
        title = stringResource(title),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(NoteDraft(initial, form.kind, form.trimmed, form.page)) },
                enabled = form.textOk && form.pageOk,
            )
        },
    ) {
        NoteFields(form = form, onChange = { form = it })
    }
}

/** The editor's fields, in a column that scrolls so a long quote never hides the buttons. */
@Composable
private fun NoteFields(form: NoteForm, onChange: (NoteForm) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .heightIn(max = EDITOR_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteKind.entries.forEach { option ->
                OtiumChip(
                    label = stringResource(option.labelRes()),
                    selected = form.kind == option,
                    onClick = { onChange(form.copy(kind = option)) },
                )
            }
        }
        // Above the field, so a long quote cannot push it out of sight.
        if (form.text.length >= COUNTER_FROM) {
            Text(
                text = pluralStringResource(
                    R.plurals.notes_counter,
                    Note.MAX_TEXT_LENGTH,
                    form.text.length,
                    Note.MAX_TEXT_LENGTH,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (form.textOk) {
                    MaterialTheme.otiumColors.muted
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        OtiumTextField(
            value = form.text,
            onValueChange = { onChange(form.copy(text = it)) },
            label = stringResource(R.string.notes_text_label),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = false,
        )
        OtiumTextField(
            value = form.pageText,
            onValueChange = {
                onChange(form.copy(pageText = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS)))
            },
            label = stringResource(R.string.notes_page_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
