package de.rki.coronawarnapp.coronatest.qrcode

import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.reyclebin.coronatest.RecycledCoronaTestsProvider
import de.rki.coronawarnapp.submission.SubmissionRepository
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.util.HashExtensions.toSHA256
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class CoronaTestQRCodeCoordinator @Inject constructor(
    private val recycledCoronaTestsProvider: RecycledCoronaTestsProvider,
    private val submissionRepository: SubmissionRepository
) {

    suspend fun handle(coronaTestQRCode: CoronaTestQRCode): Result {
        Timber.tag(TAG).d("handle(coronaTestQRCode=%s)", coronaTestQRCode::class.java.simpleName)
        val recycledCoronaTest = recycledCoronaTestsProvider.findCoronaTest(coronaTestQRCode.rawQrCode.toSHA256())
        val existingTest by lazy { submissionRepository.testForType(coronaTestQRCode.type) }
        return when {
            recycledCoronaTest != null -> InRecycleBin(recycledCoronaTest = recycledCoronaTest)
            existingTest.first() != null -> DuplicateTest
            else -> NeedsConsent
        }.also { Timber.tag(TAG).d("Returning %s", it::class.java.simpleName) }
    }

    sealed interface Result
    data class InRecycleBin(val recycledCoronaTest: CoronaTest) : Result
    object DuplicateTest : Result
    object NeedsConsent : Result

    companion object {
        private val TAG = tag<CoronaTestQRCodeCoordinator>()
    }
}
