package app.otium.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteClientsTest {

    @Test
    fun `every timeout is 8 seconds`() {
        val client = RemoteClients.httpClient("Otium/test")

        assertEquals(8_000, client.connectTimeoutMillis)
        assertEquals(8_000, client.readTimeoutMillis)
        assertEquals(8_000, client.callTimeoutMillis)
    }

    @Test
    fun `the user agent names the app, its version and a contact`() {
        assertEquals(
            "Otium/0.1.0 (https://github.com/eh0x/otium)",
            RemoteClients.userAgent("0.1.0", "https://github.com/eh0x/otium"),
        )
    }

    @Test
    fun `a blank contact leaves it out instead of printing empty brackets`() {
        assertEquals("Otium/0.1.0", RemoteClients.userAgent("0.1.0", "  "))
    }
}
