package app.otium.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.usecase.SetBackupPassphrase

/** What is typed into a passphrase prompt. It lives only in the prompt. */
@Stable
private class PassphraseInput {
    var passphrase by mutableStateOf("")
    var confirmation by mutableStateOf("")
    var acknowledged by mutableStateOf(false)

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
    val ready = input.passphrase.isNotEmpty() && !busy &&
        (!creating || (input.confirmation.isNotEmpty() && input.acknowledged))
    val submit = {
        if (ready) onSubmit(input.passphrase.toCharArray(), input.confirmation.toCharArray())
    }

    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(prompt.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
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
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.restore_cancel))
                }
                TextButton(onClick = submit, enabled = ready) {
                    Text(stringResource(prompt.actionRes()))
                }
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
    Text(
        text = stringResource(R.string.passphrase_warning),
        color = MaterialTheme.colorScheme.error,
    )
    if (prompt.changing) Text(stringResource(R.string.passphrase_change_note))
    PassphraseField(
        value = input.passphrase,
        onValueChange = { input.passphrase = it },
        label = stringResource(R.string.passphrase_label),
        error = minLengthText(R.plurals.passphrase_too_short)
            .takeIf { prompt.problem == PassphraseProblem.TOO_SHORT },
        hint = minLengthText(R.plurals.passphrase_hint),
        contentType = ContentType.NewPassword,
    )
    PassphraseField(
        value = input.confirmation,
        onValueChange = { input.confirmation = it },
        label = stringResource(R.string.passphrase_confirm_label),
        error = stringResource(R.string.passphrase_mismatch)
            .takeIf { prompt.problem == PassphraseProblem.MISMATCH },
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
        Checkbox(checked = input.acknowledged, onCheckedChange = null)
        Text(
            text = stringResource(R.string.passphrase_acknowledge),
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
    Text(stringResource(R.string.passphrase_restore_message))
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
    OutlinedTextField(
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
            TextButton(
                onClick = { visible = !visible },
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            ) {
                Text(
                    stringResource(
                        if (visible) R.string.passphrase_hide else R.string.passphrase_show,
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType },
    )
}

@Composable
private fun minLengthText(id: Int): String =
    pluralStringResource(id, SetBackupPassphrase.MIN_LENGTH, SetBackupPassphrase.MIN_LENGTH)

private fun PassphrasePrompt.titleRes(): Int = when (this) {
    is PassphrasePrompt.Set ->
        if (changing) R.string.passphrase_change_title else R.string.passphrase_set_title

    is PassphrasePrompt.Unlock -> R.string.passphrase_restore_title
}

private fun PassphrasePrompt.actionRes(): Int = when (this) {
    is PassphrasePrompt.Set ->
        if (changing) R.string.passphrase_change_action else R.string.passphrase_turn_on_action

    is PassphrasePrompt.Unlock -> R.string.passphrase_restore_action
}
