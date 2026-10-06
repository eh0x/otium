package app.otium.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.otiumColors

private const val COVER_ASPECT_RATIO = 2f / 3f
private const val MAX_INITIALS = 2
private val LEADING_ARTICLES = setOf("the", "a", "an")

/**
 * A book cover resting softly on the shelf. Shows [painter] when there is one, otherwise a quiet
 * placeholder with the title's initials. Described to screen readers by the title.
 */
@Composable
fun BookCover(title: String, modifier: Modifier = Modifier, painter: Painter? = null) {
    val shape = MaterialTheme.shapes.small
    val shadow = MaterialTheme.otiumColors.shelfShadow
    val description = stringResource(R.string.book_cover_description, title)

    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .shadow(elevation = 4.dp, shape = shape, ambientColor = shadow, spotColor = shadow)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = coverInitials(title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Up to two capital letters from the title, skipping a leading "The", "A" or "An". */
internal fun coverInitials(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val significant = words.dropWhile { it.lowercase() in LEADING_ARTICLES }.ifEmpty { words }
    return significant.take(MAX_INITIALS).map { it.first().uppercaseChar() }.joinToString("")
}
