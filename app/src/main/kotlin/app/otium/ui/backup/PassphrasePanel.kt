package app.otium.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.usecase.SetBackupPassphrase
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.otiumUnderlinedFieldColors
import app.otium.ui.theme.otiumColors

private val PANEL_MAX_WIDTH = 400.dp

/** What is typed into a passphrase prompt. It lives only in the prompt. */
@Stable
private class PassphraseInput {
    var passphrase by mutableStateOf("")
    var confirmation by mutableStateOf("")
    var acknowledged by mutableStateOf(false)

    /**
     * A new passphrase is checked as it is typed: long enough, typed the same twice, and the
     * warning acknowledged. SetBackupPassphrase checks the same again on submit.
     */
    val isValidNew: Boolean
        get() = passphrase.length >= SetBackupPassphrase.MIN_LENGTH &&
            confirmation == passphrase &&
            acknowledged

    fun clearTyped() {
        passphrase = ""
        confirmation = ""
    }
}

/**
 * Asks for a passphrase: a new one, typed twice and acknowledged as unrecoverable, or the one an
 * encrypted backup was made with. The typed text is handed over as character arrays, which the
 * receiver clears, and is cleared here whenever the prompt changes (for example after a wrong
 * try). Kept apart from the dialog window so it can be tested on its own.
 */
@Composable
internal fun PassphrasePanel(
    prompt: PassphrasePrompt,
    busy: Boolean,
    onSubmit: (passphrase: CharArray, confirmation: CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val input = remember { PassphraseInput() }
    LaunchedEffect(prompt) { input.clearTyped() }
    val creating = prompt is PassphrasePrompt.Set
    val ready = input.passphrase.isNotEmpty() && !busy && (!creating || input.isValidNew)
    val submit = {
        if (ready) onSubmit(input.passphrase.toCharArray(), input.confirmation.toCharArray())
    }

    val colors = MaterialTheme.otiumColors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .widthIn(max = PANEL_MAX_WIDTH)
            .background(colors.sheet, shape)
            .border(1.dp, colors.divider, shape)
            .padding(22.dp),
    ) {
        Column {
            Text(
                text = stringResource(prompt.titleRes()),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                ),
                modifier = Modifier.semantics { heading() },
            )
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .weight(weight = 1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (prompt) {
                    is PassphrasePrompt.Set -> NewPassphraseFields(prompt, input, submit)
                    is PassphrasePrompt.Unlock -> UnlockField(prompt, input, submit)
                }
                if (busy) OtiumIndeterminateRule()
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OtiumButton(
                    text = stringResource(prompt.dismissRes()),
                    onClick = onDismiss,
                    style = OtiumButtonStyle.Secondary,
                    enabled = !busy,
                )
                OtiumButton(
                    text = stringResource(prompt.actionRes()),
                    onClick = submit,
                    enabled = ready,
                )
            }
        }
    }
}

@Composable
private fun NewPassphraseFields(
    prompt: PassphrasePrompt.Set,
    input: PassphraseInput,
    onDone: () -> Unit,
) {
    if (prompt.suggested) {
        Text(
            stringResource(R.string.passphrase_suggest_note),
            style = bodyStyle(),
        )
    }
    Text(
        text = stringResource(R.string.passphrase_warning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    if (prompt.changing) Text(stringResource(R.string.passphrase_change_note), style = bodyStyle())
    PassphraseField(
        value = input.passphrase,
        onValueChange = { input.passphrase = it },
        label = stringResource(R.string.passphrase_label),
        error = minLengthText(R.plurals.passphrase_too_short)
            .takeIf { prompt.problem == PassphraseProblem.TOO_SHORT },
        hint = if (input.passphrase.length >= SetBackupPassphrase.MIN_LENGTH) {
            stringResource(R.string.passphrase_good)
        } else {
            minLengthText(R.plurals.passphrase_hint)
        },
        contentType = ContentType.NewPassword,
    )
    PassphraseField(
        value = input.confirmation,
        onValueChange = { input.confirmation = it },
        label = stringResource(R.string.passphrase_confirm_label),
        error = stringResource(R.string.passphrase_mismatch).takeIf {
            prompt.problem == PassphraseProblem.MISMATCH ||
                (input.confirmation.isNotEmpty() && input.confirmation != input.passphrase)
        },
        contentType = ContentType.NewPassword,
        onDone = onDone,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = input.acknowledged,
                role = Role.Checkbox,
                onValueChange = { input.acknowledged = it },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OtiumCheckbox(checked = input.acknowledged)
        Text(
            text = stringResource(R.string.passphrase_acknowledge),
            style = bodyStyle(),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun UnlockField(
    prompt: PassphrasePrompt.Unlock,
    input: PassphraseInput,
    onDone: () -> Unit,
) {
    Text(stringResource(R.string.passphrase_restore_message), style = bodyStyle())
    PassphraseField(
        value = input.passphrase,
        onValueChange = { input.passphrase = it },
        label = stringResource(R.string.passphrase_label),
        error = stringResource(R.string.error_wrong_passphrase).takeIf { prompt.wrong },
        contentType = ContentType.Password,
        onDone = onDone,
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    contentType: ContentType,
    hint: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val toggleDescription = stringResource(
        if (visible) R.string.passphrase_hide_description else R.string.passphrase_show_description,
    )
    // Underlined, like every Otium field: no box, a hairline that turns accent with focus.
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            OtiumButton(
                text = stringResource(
                    if (visible) R.string.passphrase_hide else R.string.passphrase_show,
                ),
                onClick = { visible = !visible },
                style = OtiumButtonStyle.Ghost,
                compact = true,
                contentDescription = toggleDescription,
            )
        },
        colors = otiumUnderlinedFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType },
    )
}

@Composable
private fun bodyStyle() = MaterialTheme.typography.bodyMedium

@Composable
private fun minLengthText(id: Int): String =
    pluralStringResource(id, SetBackupPassphrase.MIN_LENGTH, SetBackupPassphrase.MIN_LENGTH)

private fun PassphrasePrompt.titleRes(): Int = when (this) {
    is PassphrasePrompt.Set -> when {
        changing -> R.string.passphrase_change_title
        suggested -> R.string.passphrase_suggest_title
        else -> R.string.passphrase_set_title
    }

    is PassphrasePrompt.Unlock -> R.string.passphrase_restore_title
}

/** "Not now" for a passphrase the app suggested by itself, "Cancel" otherwise. */
private fun PassphrasePrompt.dismissRes(): Int =
    if ((this as? PassphrasePrompt.Set)?.suggested == true) {
        R.string.passphrase_not_now
    } else {
        R.string.restore_cancel
    }

private fun PassphrasePrompt.actionRes(): Int = when (this) {
    is PassphrasePrompt.Set ->
        if (changing) R.string.passphrase_change_action else R.string.passphrase_turn_on_action

    is PassphrasePrompt.Unlock -> R.string.passphrase_restore_action
}
