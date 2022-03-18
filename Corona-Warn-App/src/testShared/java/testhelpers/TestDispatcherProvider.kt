package testhelpers

import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext

class TestDispatcherProvider(private val context: CoroutineContext? = null) : DispatcherProvider {
    override val Default: CoroutineContext
        get() = context ?: Dispatchers.Unconfined
    override val Main: CoroutineContext
        get() = context ?: Dispatchers.Unconfined
    override val MainImmediate: CoroutineContext
        get() = context ?: Dispatchers.Unconfined
    override val Unconfined: CoroutineContext
        get() = context ?: Dispatchers.Unconfined
    override val IO: CoroutineContext
        get() = context ?: Dispatchers.Unconfined
}

class NewTestDispatcherProvider(private val testScope: TestScope) : DispatcherProvider {
    override val IO: CoroutineContext
        get() = testScope.testScheduler
}

fun CoroutineScope.asDispatcherProvider() = this.coroutineContext.asDispatcherProvider()

fun CoroutineContext.asDispatcherProvider() = TestDispatcherProvider(context = this)
