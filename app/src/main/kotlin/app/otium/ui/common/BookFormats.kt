package app.otium.ui.common

import androidx.annotation.StringRes
import app.otium.R
import app.otium.domain.model.BookFormat

/** The words for a format on screen. */
@StringRes
fun BookFormat.labelRes(): Int = when (this) {
    BookFormat.PRINT -> R.string.format_print
    BookFormat.EBOOK -> R.string.format_ebook
    BookFormat.AUDIOBOOK -> R.string.format_audiobook
}
