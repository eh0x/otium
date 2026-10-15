package app.otium.domain.model

/** What a [Note] holds: the owner's own words, or a passage copied from the book. */
enum class NoteKind { NOTE, QUOTE }

/**
 * A note or quote the owner keeps on one book (Marginalia). [page] is the page it belongs to, or
 * null when it belongs to the book as a whole. Notes are private: they are never shared, and they
 * go out of the phone only in a backup or an export the owner makes.
 */
data class Note(
    val id: Long = 0L,
    val bookId: Long,
    val kind: NoteKind,
    val text: String,
    val page: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        /** A quote is rarely longer than a page; the limit keeps a pasted chapter out. */
        const val MAX_TEXT_LENGTH = 10_000

        /** [text] trimmed, or null when it is blank or longer than [MAX_TEXT_LENGTH]. */
        fun cleanText(text: String): String? =
            text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT_LENGTH }

        /** A book's notes in reading order: by page, then oldest first; notes without a page last. */
        val READING_ORDER: Comparator<Note> =
            compareBy<Note> { it.page == null }
                .thenBy { it.page }
                .thenBy { it.createdAt }
                .thenBy { it.id }

        /** Whether [page] is acceptable: none, or 1 or more. */
        fun isValidPage(page: Int?): Boolean = page == null || page >= 1
    }
}
