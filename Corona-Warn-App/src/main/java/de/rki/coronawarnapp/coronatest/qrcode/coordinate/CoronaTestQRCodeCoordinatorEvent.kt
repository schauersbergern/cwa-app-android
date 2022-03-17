package de.rki.coronawarnapp.coronatest.qrcode.coordinate

import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.reyclebin.coronatest.request.RestoreRecycledTestRequest

sealed interface CoronaTestQRCodeCoordinatorEvent {
    data class InRecycleBin(val recycledCoronaTest: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class DuplicateTest(val coronaTestQRCode: CoronaTestQRCode) : CoronaTestQRCodeCoordinatorEvent
    data class NeedsConsent(val coronaTestQRCode: CoronaTestQRCode) : CoronaTestQRCodeCoordinatorEvent
    data class TestPositive(val test: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class TestNegative(val test: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class TestInvalid(val test: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class TestPending(val test: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class WarnOthers(val test: CoronaTest) : CoronaTestQRCodeCoordinatorEvent
    data class RestoreDuplicateTest(val restoreRecycledTestRequest: RestoreRecycledTestRequest) :
        CoronaTestQRCodeCoordinatorEvent
}
