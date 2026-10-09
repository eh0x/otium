package app.otium.ui.add.confirm

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.components.otiumUnderlinedFieldColors
import app.otium.ui.theme.otiumColors

private const val MAX_TAG_SUGGESTIONS = 5
private const val DESCRIPTION_MIN_LINES = 3

/** Every editable field of the book. Each change reports the whole new form. */
@Composable
internal fun ConfirmForm(
    form: BookForm,
    pagesUnknown: Boolean,
    knownTags: List<String>,
    onFormChange: (BookForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(
            value = form.title,
            onValueChange = { onFormChange(form.copy(title = it)) },
            label = R.string.confirm_field_title,
            supporting = R.string.confirm_field_title_required.takeIf { form.title.isBlank() },
            isError = form.title.isBlank(),
        )
        FormField(
            value = form.subtitle,
            onValueChange = { onFormChange(form.copy(subtitle = it)) },
            label = R.string.confirm_field_subtitle,
        )
        FormField(
            value = form.authors,
            onValueChange = { onFormChange(form.copy(authors = it)) },
            label = R.string.confirm_field_authors,
            supporting = R.string.confirm_field_list_hint,
            capitalization = KeyboardCapitalization.Words,
        )
        FormField(
            value = form.totalPages,
            onValueChange = { onFormChange(form.copy(totalPages = it.filter(Char::isDigit))) },
            label = R.string.confirm_field_pages,
            supporting = R.string.confirm_field_pages_prompt.takeIf { pagesUnknown },
            keyboardType = KeyboardType.Number,
        )
        FormField(
            value = form.isbn,
            onValueChange = { onFormChange(form.copy(isbn = it)) },
            label = R.string.confirm_field_isbn,
            keyboardType = KeyboardType.Number,
        )
        PublicationFields(form, onFormChange)
        TagEditor(
            tags = form.tags,
            knownTags = knownTags,
            onTagsChange = { onFormChange(form.copy(tags = it)) },
        )
        FormField(
            value = form.description,
            onValueChange = { onFormChange(form.copy(description = it)) },
            label = R.string.confirm_field_description,
            singleLine = false,
        )
    }
}

/** Where and when the book was published, and how it is classified. */
@Composable
private fun PublicationFields(form: BookForm, onFormChange: (BookForm) -> Unit) {
    FormField(
        value = form.publisher,
        onValueChange = { onFormChange(form.copy(publisher = it)) },
        label = R.string.confirm_field_publisher,
    )
    FormField(
        value = form.publishedYear,
        onValueChange = { onFormChange(form.copy(publishedYear = it.filter(Char::isDigit))) },
        label = R.string.confirm_field_year,
        keyboardType = KeyboardType.Number,
    )
    FormField(
        value = form.language,
        onValueChange = { onFormChange(form.copy(language = it)) },
        label = R.string.confirm_field_language,
    )
    FormField(
        value = form.categories,
        onValueChange = { onFormChange(form.copy(categories = it)) },
        label = R.string.confirm_field_categories,
        supporting = R.string.confirm_field_list_hint,
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes supporting: Int? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(stringResource(label).uppercase(), style = MaterialTheme.typography.labelSmall)
        },
        textStyle = if (singleLine) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.bodyLarge
        },
        colors = otiumUnderlinedFieldColors(),
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        isError = isError,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else DESCRIPTION_MIN_LINES,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

/** Tags as removable chips, a field to add one, and matching tags from the library to pick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    tags: List<String>,
    knownTags: List<String>,
    onTagsChange: (List<String>) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val add = { tag: String ->
        val cleaned = tag.trim()
        if (cleaned.isNotEmpty() && tags.none { it.equals(cleaned, ignoreCase = true) }) {
            onTagsChange(tags + cleaned)
        }
        input = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.confirm_tags).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val remove = stringResource(R.string.confirm_tag_remove, tag)
                InputChip(
                    selected = false,
                    onClick = { onTagsChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = remove) },
                )
            }
        }
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(R.string.confirm_tag_add).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = otiumUnderlinedFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(input) }),
        )
        val suggestions = knownTags
            .filter { input.isNotBlank() && it.startsWith(input.trim(), ignoreCase = true) }
            .filter { known -> tags.none { it.equals(known, ignoreCase = true) } }
            .take(MAX_TAG_SUGGESTIONS)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { tag ->
                SuggestionChip(onClick = { add(tag) }, label = { Text(tag) })
            }
        }
    }
}
