package testhelpers.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * If you have a test that uses a coroutine that never stops, you may use this.
 */

@ExperimentalCoroutinesApi // Since 1.2.1, tentatively till 1.3.0
fun TestScope.runBlockingTest2(
    ignoreActive: Boolean = false,
    block: suspend TestScope.() -> Unit
): Unit = runBlockingTest2(
    ignoreActive = ignoreActive,
    context = coroutineContext,
    testBody = block
)

fun runBlockingTest2(
    ignoreActive: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    testBody: suspend TestScope.() -> Unit
) {
    try {
        runBlocking {
            try {
                runTest(
                    context = context,
                    testBody = testBody
                )
            } catch (e: Error) {
                if (!ignoreActive) throw e
                else Timber.v("Ignoring active job.")
            }
        }
    } catch (e: Exception) {
        if (!ignoreActive || (e.message != "This job has not completed yet")) {
            throw e
        }
    }
}
