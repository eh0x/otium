package app.otium.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnFromBarcodeTest {

    @Test
    fun `a bookland EAN-13 with a valid check digit is an ISBN`() {
        assertEquals("9780306406157", IsbnFromBarcode("9780306406157"))
        assertEquals("9791090636071", IsbnFromBarcode("9791090636071"))
    }

    @Test
    fun `a wrong check digit is rejected`() {
        assertNull(IsbnFromBarcode("9780306406158"))
    }

    @Test
    fun `an EAN-13 outside bookland is not a book`() {
        assertNull(IsbnFromBarcode("4006381333931"))
    }

    @Test
    fun `an ISBN-10 is not something a barcode carries`() {
        assertNull(IsbnFromBarcode("0306406152"))
    }

    @Test
    fun `anything but thirteen digits is rejected`() {
        assertNull(IsbnFromBarcode("978-0-306-40615-7"))
        assertNull(IsbnFromBarcode("97803064061570"))
        assertNull(IsbnFromBarcode(""))
    }
}
