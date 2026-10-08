package app.otium.data.backup.drive

import app.otium.data.remote.RemoteClients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferClientTest {

    private val lookups = RemoteClients.httpClient("test")
    private val transfers = RemoteClients.transferClient(lookups)

    @Test
    fun `a transfer is not cut off however long it takes`() {
        assertEquals(0, transfers.callTimeoutMillis)
    }

    @Test
    fun `a transfer that goes quiet is still given up on`() {
        assertTrue(transfers.readTimeoutMillis > lookups.readTimeoutMillis)
        assertTrue(transfers.writeTimeoutMillis > 0)
    }

    @Test
    fun `book lookups keep their short limits`() {
        assertTrue(lookups.callTimeoutMillis > 0)
    }
}
