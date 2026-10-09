package app.otium.data.cover

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric because addresses are read with Android's `Uri`. */
@RunWith(RobolectricTestRunner::class)
class CameraPhotoFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val directory by lazy { temp.newFolder("camera") }

    // Unconfined: the background work runs before the call returns.
    private val photos by lazy {
        CameraPhotoFiles(directory, AUTHORITY, { NOW }, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun photo(name: String, modifiedAt: Long = NOW): File = File(directory, name).apply {
        writeText("picture")
        setLastModified(modifiedAt)
    }

    @Test
    fun `a photo the camera took for the app is deleted`() {
        val taken = photo("cover-123.jpg")

        photos.discard(listOf("content://$AUTHORITY/camera/cover-123.jpg"))

        assertFalse(taken.exists())
    }

    @Test
    fun `addresses that are not the camera's photos are left alone`() {
        val taken = photo("cover-123.jpg")
        val other = photo("notes.txt")

        photos.discard(
            listOf(
                // A gallery pick.
                "content://media/external/images/media/7",
                // Another app's provider, same path.
                "content://other.app.covers/camera/cover-123.jpg",
                // Not a photo name, or trying to climb out.
                "content://$AUTHORITY/camera/notes.txt",
                "content://$AUTHORITY/camera/..%2Fcover-123.jpg",
                "content://$AUTHORITY/camera/sub/cover-123.jpg",
                "file://${taken.path}",
            ),
        )

        assertTrue(taken.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `at start only photos older than a day are deleted`() {
        val old = photo("cover-1.jpg", modifiedAt = NOW - DAY - 1)
        val recent = photo("cover-2.jpg", modifiedAt = NOW - DAY / 2)

        photos.purgeStale()

        assertFalse(old.exists())
        assertTrue("may belong to a screen being restored", recent.exists())
        assertEquals(listOf("cover-2.jpg"), directory.list().orEmpty().toList())
    }

    @Test
    fun `a missing camera folder is not a problem`() {
        directory.delete()

        photos.purgeStale()
        photos.discard(listOf("content://$AUTHORITY/camera/cover-1.jpg"))
    }

    private companion object {
        const val AUTHORITY = "app.otium.covers"
        const val NOW = 1_700_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
