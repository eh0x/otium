package app.otium.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampPageTest {

    @Test
    fun `page within range is unchanged`() {
        assertEquals(120, ClampPage(page = 120, totalPages = 300))
        assertEquals(0, ClampPage(page = 0, totalPages = 300))
        assertEquals(300, ClampPage(page = 300, totalPages = 300))
    }

    @Test
    fun `page above total is clamped to total`() {
        assertEquals(300, ClampPage(page = 301, totalPages = 300))
        assertEquals(300, ClampPage(page = 10_000, totalPages = 300))
    }

    @Test
    fun `negative page is clamped to zero`() {
        assertEquals(0, ClampPage(page = -1, totalPages = 300))
        assertEquals(0, ClampPage(page = -1, totalPages = 0))
    }

    @Test
    fun `unknown total leaves a non negative page unchanged`() {
        assertEquals(42, ClampPage(page = 42, totalPages = 0))
        assertEquals(42, ClampPage(page = 42, totalPages = -3))
    }
}
