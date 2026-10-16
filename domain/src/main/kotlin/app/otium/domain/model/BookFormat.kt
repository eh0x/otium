package app.otium.domain.model

/**
 * How the owner has a book: on paper, as an ebook or as an audiobook. A book whose format is not
 * set (null) is not claimed to be any of them. Another format of the same title is its own book,
 * because it has its own ISBN (D15, D35).
 */
enum class BookFormat {
    PRINT,
    EBOOK,
    AUDIOBOOK,
    ;

    /** The word Otium writes in the Goodreads CSV's Binding column, which [fromBinding] reads. */
    val binding: String
        get() = when (this) {
            PRINT -> "Print"
            EBOOK -> "Ebook"
            AUDIOBOOK -> "Audiobook"
        }

    companion object {
        private val PRINT_WORDS =
            listOf("print", "paperback", "hardcover", "hardback", "board", "library")
        private val EBOOK_WORDS = listOf("ebook", "e-book", "kindle", "nook", "epub", "digital")
        private val AUDIO_WORDS = listOf("audio")

        /**
         * The format a Goodreads Binding such as "Kindle Edition", "Audible Audio" or "Paperback"
         * names, or null when it names none of them. Audio is tried first, then ebook, so
         * "Audible Audio" and "Kindle Edition" never fall through to a print word.
         */
        fun fromBinding(text: String?): BookFormat? {
            val binding = text?.trim()?.lowercase().orEmpty()
            return when {
                binding.isEmpty() -> null
                AUDIO_WORDS.any { it in binding } -> AUDIOBOOK
                EBOOK_WORDS.any { it in binding } -> EBOOK
                PRINT_WORDS.any { it in binding } -> PRINT
                else -> null
            }
        }
    }
}
