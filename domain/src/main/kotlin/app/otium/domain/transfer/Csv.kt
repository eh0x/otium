package app.otium.domain.transfer

/**
 * Comma-separated values as RFC 4180 describes them: fields may be quoted, a quote inside a quoted
 * field is doubled, and quoted fields may hold commas and line breaks. Reading is lenient, as
 * exports from other apps need: a byte order mark is skipped, CRLF and LF both end a line, blank
 * lines are dropped, and a quote in the middle of an unquoted field starts a quoted run.
 */
object Csv {
    /** U+FEFF, written as a code so that no formatter strips the invisible character. */
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()
    private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')

    fun read(text: String): List<List<String>> = Reader(text.removePrefix(BYTE_ORDER_MARK))
        .rows()
        .filterNot { it.size == 1 && it.single().isBlank() }

    /** Writes [rows] one per line, quoting only the fields that need it. */
    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            row.joinTo(this, separator = ",", transform = ::quoted)
            append('\n')
        }
    }

    private fun quoted(field: String): String = if (field.any { it in NEEDS_QUOTES }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }

    private class Reader(private val text: String) {
        private val rows = mutableListOf<List<String>>()
        private var row = mutableListOf<String>()
        private val field = StringBuilder()
        private var inQuotes = false
        private var index = 0

        fun rows(): List<List<String>> {
            while (index < text.length) {
                if (inQuotes) quotedChar(text[index]) else plainChar(text[index])
                index++
            }
            if (field.isNotEmpty() || row.isNotEmpty()) endRow()
            return rows
        }

        private fun quotedChar(char: Char) {
            when {
                char != '"' -> field.append(char)

                text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                else -> inQuotes = false
            }
        }

        private fun plainChar(char: Char) {
            when (char) {
                '"' -> inQuotes = true
                ',' -> endField()
                '\n' -> endRow()
                '\r' -> if (text.getOrNull(index + 1) != '\n') endRow()
                else -> field.append(char)
            }
        }

        private fun endField() {
            row += field.toString()
            field.clear()
        }

        private fun endRow() {
            endField()
            rows += row
            row = mutableListOf()
        }
    }
}

/** The rows of a CSV file below its header, read by column name. */
class CsvTable(header: List<String>, private val cells: List<List<String>>) {
    private val columns = header.withIndex().associate { (index, name) -> name.trim() to index }

    val records: List<Record> get() = cells.map(::Record)

    fun hasColumns(vararg names: String): Boolean = names.all { it in columns }

    inner class Record(private val values: List<String>) {
        /** The trimmed value in [column], or "" when the column or the value is missing. */
        operator fun get(column: String): String =
            columns[column]?.let(values::getOrNull)?.trim().orEmpty()
    }

    companion object {
        /** The table in [text], or null when it has no header row. */
        fun of(text: String): CsvTable? {
            val rows = Csv.read(text)
            return rows.firstOrNull()?.let { CsvTable(it, rows.drop(1)) }
        }
    }
}
