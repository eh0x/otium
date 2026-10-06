package app.otium.domain.usecase

/** Keeps a page number in range: never negative, never above a known total. */
object ClampPage {
    operator fun invoke(page: Int, totalPages: Int): Int {
        val nonNegative = maxOf(page, 0)
        return if (totalPages > 0) minOf(nonNegative, totalPages) else nonNegative
    }
}
