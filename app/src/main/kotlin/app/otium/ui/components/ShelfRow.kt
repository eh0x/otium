package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.otiumColors

/**
 * Books standing on a shelf: a row of content above a hairline shelf edge.
 * Pure styling over whatever [content] is given, so it stays cheap inside a lazy grid.
 */
@Composable
fun ShelfRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
            content = content,
        )
        ShelfBoard()
    }
}

/** The shelf edge: a hairline the full width of whatever holds it. */
@Composable
fun ShelfBoard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.otiumColors.divider),
    )
}
