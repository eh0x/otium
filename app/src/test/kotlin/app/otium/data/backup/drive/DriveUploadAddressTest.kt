package app.otium.data.backup.drive

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveUploadAddressTest {

    private fun trusted(url: String) = isGoogleUploadAddress(url.toHttpUrl())

    @Test
    fun `Drive's own upload addresses are trusted`() {
        assertTrue(trusted("https://www.googleapis.com/upload/drive/v3/files?upload_id=abc"))
        assertTrue(trusted("https://content.googleapis.com/upload/drive/v3/files"))
        assertTrue(trusted("https://googleapis.com/upload"))
    }

    @Test
    fun `plain http is refused even on Google's host`() {
        assertFalse(trusted("http://www.googleapis.com/upload/drive/v3/files"))
    }

    @Test
    fun `other hosts are refused`() {
        assertFalse(trusted("https://evil.example/upload"))
        assertFalse(trusted("https://googleapis.com.evil.example/upload"))
        assertFalse(trusted("https://notgoogleapis.com/upload"))
    }

    @Test
    fun `a Google name in the user info does not count as the host`() {
        assertFalse(trusted("https://www.googleapis.com@evil.example/upload"))
    }
}
