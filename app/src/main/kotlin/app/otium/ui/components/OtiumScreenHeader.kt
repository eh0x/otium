package app.otium.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground

/**
 * The 48 dp row at the top of a pushed screen: a back arrow and the screen's title in the serif
 * title style. Pushed screens have no top app bar.
 */
@Composable
fun OtiumScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_arrow_left),
                contentDescription = stringResource(R.string.detail_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@PreviewLightDark
@Composable
private fun OtiumScreenHeaderPreview() {
    OtiumTheme { PaperBackground { OtiumScreenHeader(title = "Enter an ISBN", onBack = {}) } }
}
