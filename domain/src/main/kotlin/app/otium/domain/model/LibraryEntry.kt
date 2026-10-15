package app.otium.domain.model

/** One book of the library with its notes, as an export holds it. */
data class LibraryEntry(val book: Book, val notes: List<Note> = emptyList())
