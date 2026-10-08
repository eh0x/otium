package app.otium.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.otium.domain.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorMessagesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val everyError = listOf(
        AppError.Offline,
        AppError.RateLimited,
        AppError.Timeout,
        AppError.NotFound,
        AppError.Duplicate(existingBookId = 1L),
        AppError.InvalidIsbn("123"),
        AppError.InvalidInput("title"),
        AppError.AuthExpired,
        AppError.InsufficientDriveSpace,
        AppError.IncompatibleBackup(backupSchema = 3, appSchema = 2),
        AppError.CorruptBackup,
        AppError.NothingToBackUp,
        AppError.Storage("disk"),
        AppError.Unexpected("boom"),
    )

    @Test
    fun `every error has a readable message`() {
        for (error in everyError) {
            val message = context.getString(error.messageRes())
            assertTrue("$error has no message", message.isNotBlank())
        }
    }

    @Test
    fun `messages never leak technical details`() {
        val storage = context.getString(AppError.Storage("SQLITE_CONSTRAINT: secret").messageRes())
        val unexpected = context.getString(
            AppError.Unexpected("NullPointerException at X").messageRes(),
        )

        assertTrue("SQLITE" !in storage && "secret" !in storage)
        assertTrue("NullPointer" !in unexpected)
    }

    @Test
    fun `failures that call for different actions read differently`() {
        val needingDifferentActions = listOf(
            AppError.Offline,
            AppError.RateLimited,
            AppError.Timeout,
            AppError.NotFound,
            AppError.Duplicate(existingBookId = 1L),
            AppError.AuthExpired,
            AppError.InsufficientDriveSpace,
            AppError.IncompatibleBackup(backupSchema = 3, appSchema = 2),
            AppError.CorruptBackup,
            AppError.NothingToBackUp,
        )

        val messages = needingDifferentActions.map { context.getString(it.messageRes()) }

        assertEquals(messages.size, messages.toSet().size)
    }
}
