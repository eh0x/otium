package app.otium.domain.model

import java.text.Collator
import java.util.Locale

/**
 * How the library orders and groups books by title and author, the way a catalogue does: titles
 * file under their first significant word, authors under their surname.
 */
object SortKeys {

    private val LEADING_ARTICLES = setOf("the", "a", "an")
    private val WHITESPACE = Regex("\\s+")

    /** Ignores case but not accents, and never depends on the device's locale. */
    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply {
        strength = Collator.SECONDARY
    }

    /** The title without a leading "The", "A" or "An". A title that is only an article stays. */
    fun title(title: String): String {
        val words = title.trim().split(WHITESPACE)
        val startsWithArticle = words.size > 1 && words.first().lowercase() in LEADING_ARTICLES
        return if (startsWithArticle) words.drop(1).joinToString(" ") else title.trim()
    }

    /**
     * Particles that belong to a surname: "Le Guin", "van Gogh", "de la Mare". Matched without
     * regard to case, and only before the last word, so a first name is never taken for one.
     */
    private val PARTICLES = setOf(
        "le", "la", "les", "de", "del", "della", "der", "den", "des", "di", "da", "das", "do",
        "dos", "du", "van", "von", "ten", "ter", "st.", "saint", "mac", "al", "el", "bin", "ibn",
    )

    /**
     * The surname of the first author: the last word of the first name in [authors], with any
     * particles before it ("Ursula K. Le Guin" gives "Le Guin").
     */
    fun surname(authors: String): String {
        val words = authors.substringBefore(',').trim().split(WHITESPACE)
        var start = words.lastIndex
        while (start > 1 && words[start - 1].lowercase() in PARTICLES) start--
        return words.subList(start, words.size).joinToString(" ")
    }

    /** Library books ordered by title as filed (see [title]), then by id for a stable order. */
    val byTitle: Comparator<BookSummary> =
        compareBy(collator) { book: BookSummary -> title(book.title) }
            .thenBy { it.id }

    /** Library books ordered by the first author's surname, then full name, then title. */
    val byAuthor: Comparator<BookSummary> =
        compareBy(collator) { book: BookSummary -> surname(book.authors) }
            .thenBy(collator) { it.authors }
            .then(byTitle)
}
