package app.otium.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.rememberAsyncImagePainter
import java.io.File

/**
 * Loads a book cover saved on this device, or returns null so the caller shows its placeholder.
 * Covers are stored locally when a book is added, so nothing here touches the network.
 */
@Composable
fun rememberCoverPainter(coverPath: String?): Painter? =
    if (coverPath == null) null else rememberAsyncImagePainter(model = File(coverPath))
