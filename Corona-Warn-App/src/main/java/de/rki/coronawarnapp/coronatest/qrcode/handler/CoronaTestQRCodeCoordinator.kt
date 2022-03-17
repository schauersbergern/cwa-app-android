package de.rki.coronawarnapp.coronatest.qrcode.handler

import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.reyclebin.coronatest.RecycledCoronaTestsProvider
import de.rki.coronawarnapp.reyclebin.coronatest.request.toRestoreRecycledTestRequest
import de.rki.coronawarnapp.submission.SubmissionRepository
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.util.HashExtensions.toSHA256
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class CoronaTestQRCodeCoordinator @Inject constructor(
    private val recycledCoronaTestsProvider: RecycledCoronaTestsProvider,
    private val submissionRepository: SubmissionRepository
) {

    private val mutableEvent = MutableSharedFlow<CoronaTestQRCodeCoordinatorEvent>(extraBufferCapacity = 1)
    val event = mutableEvent.asSharedFlow()

    suspend fun handle(coronaTestQRCode: CoronaTestQRCode) {
        Timber.tag(TAG).d("handle(coronaTestQRCode=%s)", coronaTestQRCode::class.java.simpleName)
        val recycledCoronaTest = recycledCoronaTestsProvider.findCoronaTest(coronaTestQRCode.rawQrCode.toSHA256())
        val existingTest by lazy { submissionRepository.testForType(coronaTestQRCode.type) }

        when {
            recycledCoronaTest != null -> CoronaTestQRCodeCoordinatorEvent.InRecycleBin(recycledCoronaTest)
            existingTest.first() != null -> CoronaTestQRCodeCoordinatorEvent.DuplicateTest(coronaTestQRCode)
            else -> CoronaTestQRCodeCoordinatorEvent.NeedsConsent(coronaTestQRCode)
        }.also { emit(event = it) }
    }

    suspend fun restoreCoronaTest(recycledCoronaTest: CoronaTest) {
        Timber.tag(TAG).d("restoreCoronaTest(recycledCoronaTest%s)", recycledCoronaTest::class.java.simpleName)
        val currentCoronaTest = submissionRepository.testForType(recycledCoronaTest.type).first()
        when {
            currentCoronaTest != null -> CoronaTestQRCodeCoordinatorEvent.RestoreDuplicateTest(
                recycledCoronaTest.toRestoreRecycledTestRequest()
            )

            else -> {
                recycledCoronaTestsProvider.restoreCoronaTest(recycledCoronaTest.identifier)
                recycledCoronaTest.toCoronaTestQRCodeCoordinatorEvent()
            }
        }.also { emit(it) }
    }

    private fun CoronaTest.toCoronaTestQRCodeCoordinatorEvent(): CoronaTestQRCodeCoordinatorEvent = when {
        isPending -> CoronaTestQRCodeCoordinatorEvent.TestPending(test = this)
        isNegative -> CoronaTestQRCodeCoordinatorEvent.TestNegative(test = this)
        isPositive -> when (isAdvancedConsentGiven) {
            true -> CoronaTestQRCodeCoordinatorEvent.TestPositive(test = this)
            false -> CoronaTestQRCodeCoordinatorEvent.WarnOthers(test = this)
        }
        else -> CoronaTestQRCodeCoordinatorEvent.TestInvalid(test = this)
    }

    private suspend fun emit(event: CoronaTestQRCodeCoordinatorEvent) {
        Timber.tag(TAG).d("emit(event=%s)", event::class.java.simpleName)
        mutableEvent.emit(event)
    }

    companion object {
        private val TAG = tag<CoronaTestQRCodeCoordinator>()
    }
}
