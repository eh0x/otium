package app.otium.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private const val COVER_ASPECT_RATIO = 2f / 3f

// The light grade that settles photographed covers into the plate: CSS sepia(.18) saturate(.85)
// contrast(1.04), as color matrices.
private const val SEPIA = 0.18f
private const val SATURATION = 0.85f
private const val CONTRAST = 1.04f
private const val CHANNEL_MAX = 255f
private const val HALF = 0.5f

/**
 * A book cover: [painter] when there is a cover image, otherwise a [GeneratedCover] from the title
 * and [authors]. 2:3, 2 dp corners and a soft shadow. Described to screen readers by the title.
 * [graded] applies the light sepia grade used on the detail plate; it affects real images only.
 */
@Composable
fun BookCover(
    title: String,
    modifier: Modifier = Modifier,
    authors: String? = null,
    painter: Painter? = null,
    graded: Boolean = false,
) {
    val shape = MaterialTheme.shapes.extraSmall
    val shadow = MaterialTheme.otiumColors.cover.shadow
    val description = stringResource(R.string.book_cover_description, title)

    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .shadow(elevation = 3.dp, shape = shape, ambientColor = shadow, spotColor = shadow)
            .clip(shape)
            // One image to screen readers: the generated title and surname are not read twice.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (graded) PlateGrade else null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            )
        } else {
            GeneratedCover(title = title, authors = authors, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A cover set in a plate: a 5 dp mat in the surface color with a hairline outline, as on the book
 * detail header. Real covers get the light sepia grade.
 */
@Composable
fun PlatedCover(
    title: String,
    modifier: Modifier = Modifier,
    authors: String? = null,
    painter: Painter? = null,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.otiumColors.divider)
            .padding(5.dp),
    ) {
        BookCover(title = title, authors = authors, painter = painter, graded = true)
    }
}

// Lazy, so it is built after the sepia tone below is initialized.
private val PlateGrade: ColorFilter by lazy {
    ColorFilter.colorMatrix(
        contrastMatrix(CONTRAST).apply {
            timesAssign(ColorMatrix().apply { setToSaturation(SATURATION) })
            timesAssign(sepiaMatrix(SEPIA))
        },
    )
}

// The full sepia tone (CSS sepia(1)), row by row: red, green and blue outputs.
private val SEPIA_TONE = listOf(
    floatArrayOf(0.393f, 0.769f, 0.189f),
    floatArrayOf(0.349f, 0.686f, 0.168f),
    floatArrayOf(0.272f, 0.534f, 0.131f),
)
private const val RGB = 3
private const val MATRIX_COLUMNS = 5
private const val ALPHA_ROW = 3

/** CSS sepia(amount): a blend of the identity and the full sepia tone. */
private fun sepiaMatrix(amount: Float): ColorMatrix {
    val values = FloatArray(ALPHA_ROW * MATRIX_COLUMNS + MATRIX_COLUMNS)
    for (row in 0 until RGB) {
        for (column in 0 until RGB) {
            val identity = if (row == column) 1f else 0f
            values[row * MATRIX_COLUMNS + column] =
                amount * SEPIA_TONE[row][column] + (1 - amount) * identity
        }
    }
    values[ALPHA_ROW * MATRIX_COLUMNS + ALPHA_ROW] = 1f
    return ColorMatrix(values)
}

/** CSS contrast(amount) as a color matrix: scale around mid-grey. */
private fun contrastMatrix(amount: Float): ColorMatrix {
    val offset = (HALF - HALF * amount) * CHANNEL_MAX
    return ColorMatrix(
        floatArrayOf(
            amount, 0f, 0f, 0f, offset,
            0f, amount, 0f, 0f, offset,
            0f, 0f, amount, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

@PreviewLightDark
@Composable
private fun BookCoverPreview() {
    OtiumTheme {
        PaperBackground {
            Row(
                modifier = Modifier.padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BookCover("Meditations", Modifier.width(96.dp), authors = "Marcus Aurelius")
                PlatedCover("Ethics", Modifier.width(122.dp), authors = "Baruch Spinoza")
            }
        }
    }
}
