package app.otium.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.usecase.RetryPendingLookups
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LookupRetryWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val retry = RetryPendingLookups(repo, metadata, FakeCoverStore(), FixedClock())

    private fun run(attempt: Int = 0): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<LookupRetryWorker>(context)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = LookupRetryWorker(appContext, workerParameters, retry)
                },
            )
            .build()
            .doWork()
    }

    private fun pending() =
        repo.seed(book(isbn13 = "9780441013593", title = "9780441013593").copy(needsLookup = true))

    @Test
    fun `nothing waiting is a success`() {
        assertEquals(ListenableWorker.Result.success(), run())
    }

    @Test
    fun `a book that gets its details is a success`() {
        pending()
        metadata.isbnResult = Try.Success(BookCandidate(title = "Dune", source = BookSource.GOOGLE))

        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals("Dune", repo.all.single().title)
        assertFalse(repo.all.single().needsLookup)
    }

    @Test
    fun `a book the services could not answer for is tried again later`() {
        pending()
        metadata.isbnResult = Try.Failure(AppError.Offline)

        assertEquals(ListenableWorker.Result.retry(), run())
        assertTrue(repo.all.single().needsLookup)
    }

    @Test
    fun `after many tries the worker stops and the book keeps waiting for the next save`() {
        pending()
        metadata.isbnResult = Try.Failure(AppError.RateLimited)

        assertEquals(
            ListenableWorker.Result.success(),
            run(attempt = LookupRetryWorker.MAX_ATTEMPTS),
        )
        assertTrue(repo.all.single().needsLookup)
    }
}
