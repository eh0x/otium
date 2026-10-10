package app.otium.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/**
 * An underlined input: a small capitals [label] above a serif value on a 1 dp rule. The rule
 * turns accent while the field has focus, so the field stays findable although the resting
 * hairline is faint. The label is always shown and names the field for screen readers.
 */
@Composable
fun OtiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
) {
    val colors = MaterialTheme.otiumColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .semantics { contentDescription = label },
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            interactionSource = interaction,
            decorationBox = { field ->
                Box(
                    modifier = Modifier
                        .hairlineBottom(if (focused) colors.accent else colors.divider)
                        .padding(vertical = 6.dp),
                ) { field() }
            },
        )
    }
}

/**
 * Colors that turn a Material [androidx.compose.material3.TextField] into an Otium underlined
 * field, for fields that need its supporting text, error state or trailing button.
 */
@Composable
fun otiumUnderlinedFieldColors(): TextFieldColors {
    val colors = MaterialTheme.otiumColors
    return TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = colors.accent,
        unfocusedIndicatorColor = colors.divider,
        focusedLabelColor = colors.muted,
        unfocusedLabelColor = colors.muted,
        cursorColor = colors.accent,
        focusedSupportingTextColor = colors.muted,
        unfocusedSupportingTextColor = colors.muted,
    )
}

@PreviewLightDark
@Composable
private fun OtiumTextFieldPreview() {
    var text by remember { mutableStateOf("Meditations") }
    OtiumTheme {
        PaperBackground {
            OtiumTextField(
                value = text,
                onValueChange = { text = it },
                label = "Title",
                modifier = Modifier.padding(22.dp),
            )
        }
    }
}
