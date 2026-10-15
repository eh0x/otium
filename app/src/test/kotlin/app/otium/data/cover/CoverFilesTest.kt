package app.otium.data.cover

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val directory by lazy { temp.newFolder("covers") }
    private val covers by lazy { CoverFiles(directory) }

    private fun cover(name: String): File = File(directory, name).apply { writeText("picture") }

    @Test
    fun `a stored cover is deleted by its name or its full path`() {
        val byName = cover("a.jpg")
        val byPath = cover("b.jpg")

        assertTrue(covers.delete("a.jpg"))
        assertTrue(covers.delete(byPath.path))

        assertFalse(byName.exists())
        assertFalse(byPath.exists())
    }

    @Test
    fun `deleting a cover that is already gone, or none at all, succeeds`() {
        assertTrue(covers.delete("missing.jpg"))
        assertTrue(covers.delete(null))
    }

    @Test
    fun `a file outside the covers directory is never deleted`() {
        val outside = temp.newFile("precious.txt")

        covers.delete(outside.path)
        covers.delete(File(directory, "../${outside.name}").path)

        assertTrue(outside.exists())
    }

    @Test
    fun `a name that climbs out of the covers directory is refused`() {
        assertFalse(covers.delete(".."))
        assertTrue(directory.exists())
    }

    @Test
    fun `listing leaves out covers still being written`() {
        cover("a.jpg")
        cover("cover123.part")
        File(directory, "sub").mkdir()

        assertEquals(listOf("a.jpg"), covers.list().map { it.name })
    }
}
