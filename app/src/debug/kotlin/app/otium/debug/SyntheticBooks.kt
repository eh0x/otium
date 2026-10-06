package app.otium.debug

import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import kotlin.random.Random

/** Deterministic fake library for performance checks. Debug builds and tests only. */
object SyntheticBooks {

    private val adjectives = words(
        """
        Silent Golden Hidden Broken Last First Winter Crimson Distant Forgotten
        Burning Quiet Endless Hollow Bright Pale Ancient Restless Gentle Wandering
        """,
    )
    private val nouns = words(
        """
        River Garden Empire Letter Harbour Mountain Library Orchard Lantern Voyage
        Kingdom Machine Island Mirror Season Bridge Forest Archive Shadow Compass
        """,
    )
    private val firstNames = words(
        """
        Anna Marco Elena Tomas Ingrid Yusuf Mei Oskar Lucia Henrik
        Amara Dmitri Sofia Kenji Nadia Pavel Rosa Felix Leila Viktor
        """,
    )
    private val lastNames = words(
        """
        Alder Brandt Castell Dvorak Eriksen Fontaine Garcia Haas Ivanov Jansen
        Kovacs Lindqvist Moreau Novak Okafor Petrov Quist Rossi Sato Tanaka
        """,
    )
    private val categories = words(
        "Fiction History Poetry Science Philosophy Biography Fantasy Mystery Travel Art Essays",
    )
    private val tags = words("favourite lent reread gift signed to-reread reference paperback")

    fun generate(count: Int, seed: Long = 42L): List<Book> {
        val random = Random(seed)
        return List(count) { index ->
            val totalPages = random.nextInt(80, 900)
            val currentPage = when (random.nextInt(10)) {
                in 0..3 -> 0
                in 4..6 -> random.nextInt(1, totalPages)
                else -> totalPages
            }
            Book(
                isbn13 = isbn13For(index),
                isbn10 = null,
                title = "${adjectives.random(random)} ${nouns.random(random)} ${index + 1}",
                subtitle = if (random.nextInt(4) == 0) "A ${nouns.random(random)} story" else null,
                authors = List(random.nextInt(1, 3)) { fullName(random) }.distinct(),
                publisher = "Synthetic Press",
                publishedYear = random.nextInt(1950, 2026),
                language = "en",
                description = null,
                totalPages = totalPages,
                currentPage = currentPage,
                categories = List(random.nextInt(1, 3)) { categories.random(random) }.distinct(),
                tags = List(random.nextInt(0, 3)) { tags.random(random) }.distinct(),
                coverPath = null,
                coverSourceUrl = null,
                source = BookSource.MANUAL,
                addedAt = 1_000L * index,
                updatedAt = 1_000L * index + random.nextLong(0, 500_000),
            )
        }
    }

    private fun words(text: String): List<String> = text.trim().split(Regex("\\s+"))

    private fun fullName(random: Random) =
        "${firstNames.random(random)} ${lastNames.random(random)}"

    /** A valid, unique ISBN-13 per index (978 prefix plus a nine digit body and check digit). */
    private fun isbn13For(index: Int): String {
        val first12 = "978" + (100_000_000 + index).toString().padStart(9, '0')
        val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return first12 + ((10 - sum % 10) % 10)
    }
}
