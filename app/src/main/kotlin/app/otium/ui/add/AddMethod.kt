package app.otium.ui.add

import androidx.annotation.StringRes
import app.otium.R

/** The three ways into the add flow, in the order the add sheet offers them. */
enum class AddMethod(@param:StringRes val label: Int, @param:StringRes val hint: Int) {
    SCAN(R.string.add_method_scan, R.string.add_method_scan_hint),
    ISBN(R.string.add_method_isbn, R.string.add_method_isbn_hint),
    TITLE_SEARCH(R.string.add_method_search, R.string.add_method_search_hint),
}
