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
    }
}
