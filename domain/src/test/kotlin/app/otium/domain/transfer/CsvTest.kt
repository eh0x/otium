package app.otium.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun `plain fields are split on commas, empty ones included`() {
        assertEquals(listOf(listOf("a", "", "c")), Csv.read("a,,c"))
    }

    @Test
    fun `quoted fields keep commas, doubled quotes and line breaks`() {
        val rows = Csv.read("\"one, two\",\"say \"\"hi\"\"\",\"line\nbreak\"\nnext,row\n")

        assertEquals(
            listOf(listOf("one, two", "say \"hi\"", "line\nbreak"), listOf("next", "row")),
            rows,
        )
    }

    @Test
    fun `crlf endings, a byte order mark and blank lines are tolerated`() {
        val rows = Csv.read("${Char(0xFEFF)}a,b\r\n\r\nc,d\r\n")

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test
    fun `an unquoted field holding a quoted run reads as one field`() {
        // Goodreads writes ISBNs as ="0306406152" so that spreadsheets keep the leading zero.
        assertEquals(listOf(listOf("=0306406152", "x")), Csv.read("=\"0306406152\",x"))
    }

    @Test
    fun `what is written reads back the same`() {
        val rows = listOf(listOf("plain", "with, comma", "with \"quote\"", "two\nlines", ""))

        assertEquals(rows, Csv.read(Csv.write(rows)))
    }

    @Test
    fun `fields that need no quotes are written bare`() {
        assertEquals("a,b c\n", Csv.write(listOf(listOf("a", "b c"))))
    }

    @Test
    fun `a table reads values by column name and tolerates short rows`() {
        val table = CsvTable.of(" Title ,Pages\nDune,412\nShort\n")!!

        assertTrue(table.hasColumns("Title", "Pages"))
        assertFalse(table.hasColumns("ISBN"))
        assertEquals(listOf("Dune", "Short"), table.records.map { it["Title"] })
        assertEquals(listOf("412", ""), table.records.map { it["Pages"] })
        assertEquals("", table.records.first()["ISBN"])
    }

    @Test
    fun `an empty file has no table`() {
        assertNull(CsvTable.of(""))
    }
}
