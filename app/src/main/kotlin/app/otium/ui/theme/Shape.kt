package app.otium.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Small, quiet radii: covers and controls read as paper and board, not as pills.
 * - extraSmall (2 dp): covers.
 * - small (4 dp): buttons, chips, cards, inputs, menus and toasts.
 * - medium (8 dp): dialogs.
 * - [SheetShape] (14 dp, top corners only): bottom sheets.
 */
internal val OtiumShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Bottom sheets: rounded at the top edge only. */
val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
