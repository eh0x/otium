package app.otium.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.otium.R

/** The tabs of the bottom bar. Add sits between Reading and Settings but is an action, not a tab. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    LIBRARY(LibraryRoute, R.string.library_title, R.drawable.ic_lucide_library),
    READING(ReadingRoute, R.string.reading_title, R.drawable.ic_lucide_book_open),
    SETTINGS(SettingsRoute, R.string.settings_title, R.drawable.ic_lucide_sliders_horizontal),
}
