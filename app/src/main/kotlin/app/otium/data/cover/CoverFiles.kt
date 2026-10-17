package app.otium.data.cover

import java.io.File
import java.io.IOException

private const val PARTIAL_EXTENSION = "part"

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

    /**
     * Deletes the cover the database calls [stored]. Only a file directly in [directory] is ever
     * touched, whatever [stored] holds: a name like `..` is refused. True when the cover is gone,
     * including when there was none; false when it could not be deleted or was refused.
     */
    @Suppress("SwallowedException") // A cover that cannot be resolved is reported as not deleted.
    fun delete(stored: String?): Boolean {
        val name = nameOf(stored) ?: return true
        val file = File(directory, name)
        return try {
            file.canonicalFile.parentFile == directory.canonicalFile &&
                (!file.exists() || (file.isFile && file.delete()))
        } catch (error: IOException) {
            false
        }
    }

    /** The cover files in [directory], without the `.part` files of covers still being written. */
    fun list(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension != PARTIAL_EXTENSION }

    /** The `.part` files in [directory] from covers being written or interrupted saves. */
    fun partials(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == PARTIAL_EXTENSION }
}
