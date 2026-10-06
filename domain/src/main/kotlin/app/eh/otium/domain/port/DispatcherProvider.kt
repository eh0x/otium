package app.eh.otium.domain.port

import kotlinx.coroutines.CoroutineDispatcher

/** Source of coroutine dispatchers, injected so tests can replace them. */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
