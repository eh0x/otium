package app.otium.data.cover

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.testBook
import app.otium.data.repo.RoomBookRepository
import app.otium.domain.usecase.BackupLock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrphanedCoversTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: OtiumDatabase
    private val directory by lazy { temp.newFolder("covers") }
    private val books by lazy {
        RoomBookRepository(db.bookDao(), CoverFiles(directory), Dispatchers.Unconfined)
    }
    private val lock = BackupLock()
    private val orphans by lazy {
        OrphanedCovers(
            db.bookDao(),
            CoverFiles(directory),
            lock,
            { NOW },
            CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun cover(name: String, modifiedAt: Long = NOW - DAY - 1): File =
        File(directory, name).apply {
            writeText("picture")
            setLastModified(modifiedAt)
        }

    @Test
    fun `an old cover no book names is deleted`() = runTest {
        val orphan = cover("orphan.jpg")

        assertEquals(1, orphans.purgeNow())

        assertFalse(orphan.exists())
    }

    @Test
    fun `a cover a book names is kept`() = runTest {
        val named = cover("named.jpg")
        books.add(testBook().copy(coverPath = named.path))

        assertEquals(0, orphans.purgeNow())

        assertTrue(named.exists())
    }

    @Test
    fun `a cover an older row names by its full path is kept`() = runTest {
        val named = cover("named.jpg")
        books.add(testBook().copy(coverPath = "/data/old/install/files/covers/named.jpg"))

        orphans.purgeNow()

        assertTrue(named.exists())
    }

    @Test
    fun `a recent cover is kept, as its book may not be saved yet`() = runTest {
        val fresh = cover("fresh.jpg", modifiedAt = NOW - DAY / 2)

        assertEquals(0, orphans.purgeNow())

        assertTrue(fresh.exists())
    }

    @Test
    fun `a cover still being written is kept`() = runTest {
        val partial = cover("cover123.part")

        orphans.purgeNow()

        assertTrue(partial.exists())
    }

    @Test
    fun `purge runs in the background and a missing covers folder is not a problem`() {
        directory.delete()

        orphans.purge()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
