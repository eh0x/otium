package app.otium.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
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
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

/** From this many notes a book gets a search field; fewer are easier to read than to search. */
private const val SEARCH_FROM = 6

private const val SNIPPET_LENGTH = 40
private val QUOTE_RULE = 2.dp
private val QUOTE_INDENT = 12.dp

/**
 * The owner's notes and quotes on this book: add one, change or delete one, and from
 * [SEARCH_FROM] notes on, search them. Everything the owner writes stays on the device.
 */
@Composable
internal fun NotesSection(
    notes: List<Note>,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier) {
        NotesHeader(onAdd = { adding = true })
        NotesList(
            notes = notes,
            query = query,
            onQueryChange = { query = it },
            onEdit = { editingId = it },
            onDelete = { deletingId = it },
        )
    }

    val editing = notes.firstOrNull { it.id == editingId }
    if (adding || editing != null) {
        NoteDialog(
            initial = editing,
            onConfirm = {
                adding = false
                editingId = null
                actions.onNoteSaved(it)
            },
            onDismiss = {
                adding = false
                editingId = null
            },
        )
    }
    notes.firstOrNull { it.id == deletingId }?.let { note ->
        DeleteNoteDialog(
            onConfirm = {
                deletingId = null
                actions.onNoteDeleted(note.id)
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun NotesHeader(onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.notes_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        OtiumButton(
            text = stringResource(R.string.notes_add),
            onClick = onAdd,
            style = OtiumButtonStyle.Secondary,
            compact = true,
        )
    }
}

/** The notes, or a quiet line saying there are none (or none that match the search). */
@Composable
private fun NotesList(
    notes: List<Note>,
    query: String,
    onQueryChange: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (notes.size >= SEARCH_FROM) {
        OtiumTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.notes_search),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    val shown = filtered(notes, query)
    when {
        notes.isEmpty() -> QuietLine(stringResource(R.string.notes_empty))

        shown.isEmpty() -> QuietLine(stringResource(R.string.notes_no_match, query.trim()))

        else -> shown.forEach { note ->
            NoteItem(
                note = note,
                onEdit = { onEdit(note.id) },
                onDelete = { onDelete(note.id) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.otiumColors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The notes whose text contains [query], ignoring case; all of them for a blank query. */
private fun filtered(notes: List<Note>, query: String): List<Note> {
    val needle = query.trim()
    return if (needle.isEmpty()) {
        notes
    } else {
        notes.filter { it.text.contains(needle, ignoreCase = true) }
    }
}

@Composable
private fun NoteItem(note: Note, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(top = 12.dp),
    ) {
        NoteMeta(note)
        NoteText(note)
        NoteButtons(snippet = note.text.take(SNIPPET_LENGTH), onEdit = onEdit, onDelete = onDelete)
    }
}

/** What kind of note this is, and the page it belongs to. */
@Composable
private fun NoteMeta(note: Note) {
    val muted = MaterialTheme.otiumColors.muted
    Row {
        Text(
            text = stringResource(note.kind.labelRes()).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        note.page?.let {
            Text(
                text = stringResource(R.string.notes_page_short, it),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = muted,
            )
        }
    }
}

/** The text; a quote is set in italics beside a thin accent rule. */
@Composable
private fun NoteText(note: Note) {
    val quote = note.kind == NoteKind.QUOTE
    Row(modifier = Modifier.height(IntrinsicSize.Min).padding(top = 6.dp)) {
        if (quote) {
            Box(
                modifier = Modifier
                    .width(QUOTE_RULE)
                    .fillMaxHeight()
                    .background(MaterialTheme.otiumColors.accent),
            )
        }
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = if (quote) FontStyle.Italic else FontStyle.Normal,
            ),
            modifier = Modifier.padding(start = if (quote) QUOTE_INDENT else 0.dp),
        )
    }
}

@Composable
private fun NoteButtons(snippet: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OtiumButton(
            text = stringResource(R.string.notes_edit),
            onClick = onEdit,
            style = OtiumButtonStyle.Secondary,
            compact = true,
            contentDescription = stringResource(R.string.notes_edit_description, snippet),
        )
        OtiumButton(
            text = stringResource(R.string.notes_delete),
            onClick = onDelete,
            style = OtiumButtonStyle.Secondary,
            compact = true,
            contentDescription = stringResource(R.string.notes_delete_description, snippet),
        )
    }
}
