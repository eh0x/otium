package app.eh.otium.data

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultDispatcherProviderTest {

    private val provider = DefaultDispatcherProvider()

    @Test
    fun exposesTheStandardCoroutineDispatchers() {
        assertSame(Dispatchers.Main, provider.main)
        assertSame(Dispatchers.IO, provider.io)
        assertSame(Dispatchers.Default, provider.default)
    }
}
