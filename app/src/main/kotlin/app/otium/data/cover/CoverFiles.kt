package app.otium.data.cover

import java.io.File

/**
 * Where the covers the database names live. The database keeps only a cover's file name, so the
 * covers directory can move (a restore, a device transfer, a new install path) without touching
 * a row. The domain sees full paths, built here when a book is read.
 *
 * Rows written before names were stored hold a full path; only its file name counts, so they
 * resolve into this directory as well.
 */
class CoverFiles(val directory: File) {

    /** The name the database keeps for the cover at [path]. */
    fun nameOf(path: String?): String? = path?.let { File(it).name }?.takeIf { it.isNotEmpty() }

    /** The full path of the cover the database calls [stored]. */
    fun pathOf(stored: String?): String? = nameOf(stored)?.let { File(directory, it).path }
}
