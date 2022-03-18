package de.rki.coronawarnapp.coronatest.qrcode.handler

import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.reyclebin.coronatest.request.RestoreRecycledTestRequest

sealed interface CoronaTestQRCodeHandlerEvent {
    data class InRecycleBin(val recycledCoronaTest: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class DuplicateTest(val coronaTestQRCode: CoronaTestQRCode) : CoronaTestQRCodeHandlerEvent
    data class NeedsConsent(val coronaTestQRCode: CoronaTestQRCode) : CoronaTestQRCodeHandlerEvent
    data class TestPositive(val test: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class TestNegative(val test: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class TestInvalid(val test: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class TestPending(val test: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class WarnOthers(val test: CoronaTest) : CoronaTestQRCodeHandlerEvent
    data class RestoreDuplicateTest(val restoreRecycledTestRequest: RestoreRecycledTestRequest) :
        CoronaTestQRCodeHandlerEvent
}
