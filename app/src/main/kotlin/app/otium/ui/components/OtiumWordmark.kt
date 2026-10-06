package app.otium.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.ui.theme.OtiumFonts

/**
 * Placeholder wordmark: "OTIUM" in Cinzel, a Roman inscription face. The final mark is a design
 * asset and can replace this composable without touching its callers.
 */
@Composable
fun OtiumWordmark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        style = TextStyle(
            fontFamily = OtiumFonts.Wordmark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            letterSpacing = 0.18.em,
        ),
    )
}
