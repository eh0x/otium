package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.otium.ui.theme.OtiumFonts
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

// Proportions of the cover width, from the design handoff.
private const val SPINE_SHADE_END = 0.06f
private const val SPINE_HIGHLIGHT = 0.075f
private const val SPINE_END = 0.10f
private const val FRAME_INSET = 0.07f
private const val FRAME_INSET_SPINE = 0.12f
private const val FRAME_PAD_TOP = 0.09f
private const val FRAME_PAD_SIDE = 0.04f
private const val FRAME_PAD_BOTTOM = 0.08f
private const val FRAME_ALPHA = 0.34f
private const val ORNAMENT_SIZE = 0.03f
private const val ORNAMENT_ALPHA = 0.9f
private const val ORNAMENT_ANGLE = 45f
private const val TITLE_SIZE = 0.125f
private const val TITLE_LINE = 1.04f
private const val TITLE_MAX_LINES = 5
private const val SURNAME_SIZE = 0.054f
private const val SURNAME_ALPHA = 0.85f
private const val COVER_ASPECT_RATIO = 2f / 3f

/**
 * The typographic cover drawn when a book has no cover image: a deep board chosen by the title,
 * spine shading, an inset frame, a small gold ornament, the title and the author's surname. Every
 * size scales with the cover's width, so it reads the same on a shelf and on the detail plate.
 */
@Composable
fun GeneratedCover(title: String, modifier: Modifier = Modifier, authors: String? = null) {
    val cover = MaterialTheme.otiumColors.cover
    val board = cover.boards[coverBoardIndex(title, cover.boards.size)]

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .background(board)
            .coverShading(cover.spine, cover.sheen),
    ) {
        val width = maxWidth
        val density = LocalDensity.current
        fun share(fraction: Float): Dp = width * fraction
        // Text sizes follow the cover, not the font scale: the cover is a picture of a book.
        // Dp.toSp divides out the font scale, so the text keeps its share of the width.

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = share(FRAME_INSET_SPINE),
                    top = share(FRAME_INSET),
                    end = share(FRAME_INSET),
                    bottom = share(FRAME_INSET),
                )
                .border(1.dp, cover.text.copy(alpha = FRAME_ALPHA))
                .padding(
                    start = share(FRAME_PAD_SIDE),
                    top = share(FRAME_PAD_TOP),
                    end = share(FRAME_PAD_SIDE),
                    bottom = share(FRAME_PAD_BOTTOM),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(share(ORNAMENT_SIZE).coerceAtLeast(3.dp))
                    .rotate(ORNAMENT_ANGLE)
                    .border(1.dp, cover.ornament.copy(alpha = ORNAMENT_ALPHA)),
            )
            CoverTitle(title, size = with(density) { share(TITLE_SIZE).toSp() })
            CoverSurname(authors, size = with(density) { share(SURNAME_SIZE).toSp() })
        }
    }
}

/** Spine shading down the left edge, then a faint sheen from top to bottom. */
private fun Modifier.coverShading(spine: List<Color>, sheen: List<Color>): Modifier = drawBehind {
    val (shade, clear, highlight) = spine
    drawRect(
        Brush.horizontalGradient(
            0f to shade,
            SPINE_SHADE_END to clear,
            SPINE_HIGHLIGHT to highlight,
            SPINE_END to spine.last(),
        ),
    )
    drawRect(Brush.verticalGradient(sheen))
}

@Composable
private fun CoverTitle(title: String, size: TextUnit) {
    Text(
        text = title,
        color = MaterialTheme.otiumColors.cover.text,
        textAlign = TextAlign.Center,
        maxLines = TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = OtiumFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = size,
            lineHeight = size * TITLE_LINE,
            letterSpacing = (-0.01).em,
            lineBreak = LineBreak.Heading,
        ),
    )
}

@Composable
private fun CoverSurname(authors: String?, size: TextUnit) {
    Text(
        text = coverSurname(authors).uppercase(),
        color = MaterialTheme.otiumColors.cover.text.copy(alpha = SURNAME_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = OtiumFonts.Text, fontSize = size, letterSpacing = 0.16.em),
    )
}

/** A stable board for [title]: the same book always gets the same color. */
internal fun coverBoardIndex(title: String, boardCount: Int): Int =
    Math.floorMod(title.trim().lowercase().hashCode(), boardCount)

/** The first author's surname (the last word of the name), or empty when unknown. */
internal fun coverSurname(authors: String?): String = authors.orEmpty()
    .substringBefore(',')
    .trim()
    .split(Regex("\\s+"))
    .lastOrNull()
    .orEmpty()

@PreviewLightDark
@Composable
private fun GeneratedCoverPreview() {
    OtiumTheme {
        PaperBackground {
            Row(
                modifier = Modifier.padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GeneratedCover("Meditations", Modifier.width(96.dp), authors = "Marcus Aurelius")
                GeneratedCover(
                    "The Name of the Rose",
                    Modifier.width(96.dp),
                    authors = "Umberto Eco",
                )
                GeneratedCover("Leaves of Grass", Modifier.width(122.dp), authors = "Walt Whitman")
            }
        }
    }
}
