package app.otium.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The camera and ML Kit need a device; what counts as a book read is checked here. */
class MlKitBarcodeScannerTest {

    @Test
    fun `the first book barcode in a frame is the read`() {
        assertEquals(
            "9780441013593",
            isbnIn(listOf(null, "4006381333931", "9780441013593", "9780141439587")),
        )
    }

    @Test
    fun `a frame with no book barcode reads nothing`() {
        assertNull(isbnIn(listOf("4006381333931", "9780441013594", null)))
        assertNull(isbnIn(emptyList()))
    }
}
