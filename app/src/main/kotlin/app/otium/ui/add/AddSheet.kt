package app.otium.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.ui.components.OtiumSheet
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.otiumColors

private const val PRESSED_TINT = 0.04f

/** Roman numerals for the three rows, in the order of [AddMethod.entries]. */
private val NUMERALS = listOf("I", "II", "III")

/** Asks how to add a book. The sheet only collects the choice; the caller navigates. */
@Composable
fun AddSheet(onChoose: (AddMethod) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    OtiumSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Text(
            text = stringResource(R.string.add_kicker).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.em),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.add_book_title),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
            modifier = Modifier
                .padding(top = 4.dp, bottom = 14.dp)
                .semantics { heading() },
        )
        AddMethod.entries.forEachIndexed { index, method ->
            MethodRow(numeral = NUMERALS[index], method = method, onClick = { onChoose(method) })
        }
    }
}

/** A numbered way in: numeral, label over hint, chevron. One button for screen readers. */
@Composable
private fun MethodRow(numeral: String, method: AddMethod, onClick: () -> Unit) {
    val colors = MaterialTheme.otiumColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val label = stringResource(method.label)
    val hint = stringResource(method.hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .hairlineTop(colors.divider)
            .background(
                if (pressed) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = PRESSED_TINT)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = numeral,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.accent,
            modifier = Modifier.width(34.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.muted,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
