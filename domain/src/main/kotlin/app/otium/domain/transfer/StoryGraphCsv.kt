package app.otium.domain.transfer

/**
 * The library export StoryGraph sends by email (Manage Account, Manage Your Data, Export
 * StoryGraph Library). It has no page count or publisher, so books with an ISBN are looked up.
 * Moods, ratings and reviews have no place in Otium and are not read.
 */
object StoryGraphCsv {
    private const val TITLE = "Title"
    private const val AUTHORS = "Authors"
    private const val ISBN = "ISBN/UID"
    private const val READ_STATUS = "Read Status"
    private const val DATE_ADDED = "Date Added"
    private const val TAGS = "Tags"

    private const val STATUS_READ = "read"
    private const val STATUS_READING = "currently-reading"
    private const val STATUS_TO_READ = "to-read"

    fun matches(table: CsvTable): Boolean = table.hasColumns(TITLE, AUTHORS, READ_STATUS)

    fun read(table: CsvTable): List<ImportRow> = table.records.map { record ->
        val status = record[READ_STATUS].lowercase()
        ImportRow(
            title = record[TITLE],
            authors = FileValues.names(record[AUTHORS]),
            // StoryGraph puts its own id here for books without an ISBN; the import checks it.
            isbn = FileValues.isbnOrNull(record[ISBN]),
            shelf = when (status) {
                STATUS_READ -> Shelf.READ
                STATUS_READING -> Shelf.READING
                else -> Shelf.TO_READ
            },
            // "did-not-finish" and "paused" have no shelf in Otium, so they are kept as tags.
            tags = (
                FileValues.names(record[TAGS]) +
                    listOfNotNull(status.takeIf { it.isNotEmpty() && it !in SHELVES })
                ).distinct(),
            addedAt = FileValues.parseDate(record[DATE_ADDED]),
        )
    }

    private val SHELVES = setOf(STATUS_READ, STATUS_READING, STATUS_TO_READ)
}
