package app.otium.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * The flat paper ground every screen sits on. The Classical design has no texture: hairlines
 * carry the structure.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // Like Surface, provide the matching content color. Without it, text falls back to black,
    // which is unreadable on the dark background.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            content = content,
        )
    }
}
