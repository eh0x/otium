package app.otium.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TryTest {

    private val ok: Try<Int> = Try.Success(2)
    private val failed: Try<Int> = Try.Failure(AppError.Offline)

    @Test
    fun `map transforms success and keeps failure`() {
        assertEquals(Try.Success(4), ok.map { it * 2 })
        assertEquals(failed, failed.map { it * 2 })
    }

    @Test
    fun `flatMap chains success and short circuits on failure`() {
        assertEquals(Try.Success("2"), ok.flatMap { Try.Success(it.toString()) })
        assertEquals(Try.Failure(AppError.NotFound), ok.flatMap { Try.Failure(AppError.NotFound) })
        assertEquals(failed, failed.flatMap { Try.Success(it.toString()) })
    }

    @Test
    fun `getOrNull and errorOrNull expose each side`() {
        assertEquals(2, ok.getOrNull())
        assertNull(failed.getOrNull())
        assertNull(ok.errorOrNull())
        assertEquals(AppError.Offline, failed.errorOrNull())
    }

    @Test
    fun `fold collapses both cases`() {
        assertEquals("ok 2", ok.fold(onSuccess = { "ok $it" }, onFailure = { "err" }))
        assertEquals("err", failed.fold(onSuccess = { "ok $it" }, onFailure = { "err" }))
    }
}
