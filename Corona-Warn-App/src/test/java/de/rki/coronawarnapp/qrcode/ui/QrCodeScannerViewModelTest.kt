package de.rki.coronawarnapp.qrcode.ui

import android.net.Uri
import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.qrcode.handler.CoronaTestQRCodeHandler
import de.rki.coronawarnapp.coronatest.qrcode.handler.CoronaTestQRCodeHandlerEvent
import de.rki.coronawarnapp.coronatest.server.CoronaTestResult
import de.rki.coronawarnapp.coronatest.type.pcr.PCRCoronaTest
import de.rki.coronawarnapp.coronatest.type.rapidantigen.RACoronaTest
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccMaxPersonChecker
import de.rki.coronawarnapp.covidcertificate.common.repository.TestCertificateContainerId
import de.rki.coronawarnapp.covidcertificate.vaccination.core.CovidCertificateSettings
import de.rki.coronawarnapp.dccticketing.core.allowlist.internal.DccTicketingAllowListException
import de.rki.coronawarnapp.dccticketing.core.common.DccTicketingErrorCode
import de.rki.coronawarnapp.dccticketing.core.common.DccTicketingException
import de.rki.coronawarnapp.dccticketing.core.qrcode.DccTicketingInvalidQrCodeException
import de.rki.coronawarnapp.dccticketing.core.qrcode.DccTicketingQrCode
import de.rki.coronawarnapp.dccticketing.core.qrcode.DccTicketingQrCodeHandler
import de.rki.coronawarnapp.presencetracing.TraceLocationSettings
import de.rki.coronawarnapp.qrcode.QrCodeFileParser
import de.rki.coronawarnapp.qrcode.handler.CheckInQrCodeHandler
import de.rki.coronawarnapp.qrcode.handler.DccQrCodeHandler
import de.rki.coronawarnapp.qrcode.parser.QrCodeBoofCVParser
import de.rki.coronawarnapp.qrcode.scanner.ImportDocumentException
import de.rki.coronawarnapp.qrcode.scanner.QrCodeValidator
import de.rki.coronawarnapp.qrcode.scanner.UnsupportedQrCodeException
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.RestoreDuplicateTest
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.TestInvalid
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.TestNegative
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.TestPending
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.TestPositive
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.WarnOthers
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.InRecycleBin
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.ConsentTest
import de.rki.coronawarnapp.qrcode.ui.CoronaTestResult.DuplicateTest
import de.rki.coronawarnapp.reyclebin.coronatest.RecycledCoronaTestsProvider
import de.rki.coronawarnapp.reyclebin.coronatest.request.toRestoreRecycledTestRequest
import de.rki.coronawarnapp.reyclebin.covidcertificate.RecycledCertificatesProvider
import de.rki.coronawarnapp.submission.SubmissionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.TestDispatcherProvider
import testhelpers.extensions.InstantExecutorExtension
import testhelpers.extensions.getOrAwaitValue
import java.util.concurrent.TimeoutException

@ExtendWith(InstantExecutorExtension::class)
class QrCodeScannerViewModelTest : BaseTest() {
    @MockK lateinit var qrCodeValidator: QrCodeValidator
    @MockK lateinit var qrCodeFileParser: QrCodeFileParser
    @MockK lateinit var dccHandler: DccQrCodeHandler
    @MockK lateinit var dccTicketingQrCodeHandler: DccTicketingQrCodeHandler
    @MockK lateinit var checkInHandler: CheckInQrCodeHandler
    @MockK lateinit var submissionRepository: SubmissionRepository
    @MockK lateinit var dccSettings: CovidCertificateSettings
    @MockK lateinit var traceLocationSettings: TraceLocationSettings
    @MockK lateinit var recycledCertificatesProvider: RecycledCertificatesProvider
    @MockK lateinit var recycledCoronaTestsProvider: RecycledCoronaTestsProvider
    @MockK lateinit var dccMaxPersonChecker: DccMaxPersonChecker
    @RelaxedMockK lateinit var coronaTestQRCodeHandler: CoronaTestQRCodeHandler

    private lateinit var mutableCoronaTestQRCodeHandlerEvent: MutableSharedFlow<CoronaTestQRCodeHandlerEvent>

    private val recycledRAT = RACoronaTest(
        identifier = "rat-identifier",
        lastUpdatedAt = Instant.EPOCH,
        registeredAt = Instant.EPOCH,
        registrationToken = "token",
        testResult = CoronaTestResult.RAT_REDEEMED,
        testedAt = Instant.EPOCH,
        isDccConsentGiven = false,
        isDccSupportedByPoc = false,
    )

    private val anotherRAT = RACoronaTest(
        identifier = "rat-identifier-another",
        lastUpdatedAt = Instant.EPOCH,
        registeredAt = Instant.EPOCH,
        registrationToken = "token-another",
        testResult = CoronaTestResult.RAT_REDEEMED,
        testedAt = Instant.EPOCH,
        isDccConsentGiven = false,
        isDccSupportedByPoc = false
    )

    private val recycledPCR = PCRCoronaTest(
        identifier = "pcr-identifier",
        lastUpdatedAt = Instant.EPOCH,
        registeredAt = Instant.EPOCH,
        registrationToken = "token",
        testResult = CoronaTestResult.PCR_NEGATIVE,
        isDccConsentGiven = true
    )

    private val anotherPCR = PCRCoronaTest(
        identifier = "pcr-identifier-another",
        lastUpdatedAt = Instant.EPOCH,
        registeredAt = Instant.EPOCH,
        registrationToken = "token-another",
        testResult = CoronaTestResult.PCR_NEGATIVE,
        isDccConsentGiven = true
    )

    private val rawResult = "rawResult"
    private val parsedResult = QrCodeBoofCVParser.ParseResult.Success(rawResults = setOf(rawResult))

    val coronaTestQRCode = CoronaTestQRCode.PCR(qrCodeGUID = "qrCodeGUID", rawQrCode = "rawQrCode")

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Uri::class)

        every { Uri.parse(any()) } returns mockk()
        coEvery { qrCodeFileParser.decodeQrCodeFile(any()) } returns QrCodeFileParser.ParseResult.Success("qrcode")
        every { recycledCoronaTestsProvider.tests } returns flowOf(emptySet())
        coEvery { recycledCoronaTestsProvider.restoreCoronaTest(any()) } just Runs

        mutableCoronaTestQRCodeHandlerEvent = MutableSharedFlow(extraBufferCapacity = 1)
        every { coronaTestQRCodeHandler.event } returns mutableCoronaTestQRCodeHandlerEvent.asSharedFlow()
    }

    @Test
    fun `onImportFile triggers file parsing`() {
        viewModel().onImportFile(mockk())
        coVerify { qrCodeFileParser.decodeQrCodeFile(any()) }
    }

    @Test
    fun `onImportFile report error in case of failure`() {
        coEvery { qrCodeFileParser.decodeQrCodeFile(any()) } returns QrCodeFileParser.ParseResult.Failure(Exception())

        viewModel().apply {
            onImportFile(mockk())
            result.value.shouldBeInstanceOf<Error>()
        }
    }

    @Test
    fun `onImportFile report error in case of crash`() {
        coEvery { qrCodeFileParser.decodeQrCodeFile(any()) } throws Exception()

        viewModel().apply {
            onImportFile(mockk())
            val result = result.value
            result.shouldBeInstanceOf<Error>()
            result.error.shouldBeInstanceOf<ImportDocumentException>().errorCode shouldBe
                ImportDocumentException.ErrorCode.CANT_READ_FILE
        }
    }

    @Test
    fun `unsupported qr code leads to UnsupportedQrCodeException`() {
        val error = UnsupportedQrCodeException()
        coEvery { qrCodeValidator.validate(rawString = any()) } throws error

        viewModel().apply {
            onParseResult(parseResult = parsedResult)
            result.getOrAwaitValue().also {
                it as Error
                it.error shouldBe error
            }
        }
    }

    @Test
    fun `restoreCertificate asks provider to restore DGC`() {
        coEvery { recycledCertificatesProvider.restoreCertificate(any()) } just Runs
        val containerId = TestCertificateContainerId("ceruuid")
        viewModel().apply {
            restoreCertificate(containerId)
            result.getOrAwaitValue().shouldBeInstanceOf<DccResult.Details>()
        }
        coVerify { recycledCertificatesProvider.restoreCertificate(any()) }
    }



    @Test
    fun `restoreCoronaTest forwards test to CoronaTestQRCodeHandler`() {
        viewModel().restoreCoronaTest(recycledRAT)

        coVerify {
            coronaTestQRCodeHandler.restoreCoronaTest(recycledCoronaTest = recycledRAT)
        }
    }

    @Test
    fun `onScanResult reports DccTicketingInvalidQrCodeException`() {
        val error = DccTicketingInvalidQrCodeException(
            errorCode = DccTicketingInvalidQrCodeException.ErrorCode.INIT_DATA_PROTOCOL_INVALID
        )
        coEvery { qrCodeValidator.validate(any()) } throws error

        with(viewModel()) {
            onParseResult(parseResult = parsedResult)
            result.getOrAwaitValue().also {
                it as Error
                it.error shouldBe error
            }
        }
    }

    @Test
    fun `onScanResult reports DccTicketingAllowListException`() {
        coEvery { qrCodeValidator.validate(any()) } returns mockk<DccTicketingQrCode>()

        val error = DccTicketingAllowListException(DccTicketingAllowListException.ErrorCode.ALLOWLIST_NO_MATCH)
        coEvery { dccTicketingQrCodeHandler.handleQrCode(any()) } throws error

        with(viewModel()) {
            onParseResult(parseResult = parsedResult)
            result.getOrAwaitValue().also {
                it as Error
                it.error shouldBe error
            }
        }
    }

    @Test
    fun `onScanResult reports DccTicketingException`() {
        coEvery { qrCodeValidator.validate(any()) } returns mockk<DccTicketingQrCode> {
            every { data } returns mockk {
                every { serviceProvider } returns "serviceProvider"
            }
        }

        val error = DccTicketingException(errorCode = DccTicketingErrorCode.VD_ID_PARSE_ERR)
        coEvery { dccTicketingQrCodeHandler.handleQrCode(any()) } throws error

        with(viewModel()) {
            onParseResult(parseResult = parsedResult)
            result.getOrAwaitValue().also {
                it should beInstanceOf<DccTicketingError>()
            }
        }
    }

    @Test
    fun `onParseResult does nothing on empty parse result`() {
        val emptyParseResult = QrCodeBoofCVParser.ParseResult.Success(rawResults = emptySet())
        with(viewModel()) {
            onParseResult(parseResult = emptyParseResult)

            shouldThrow<TimeoutException> { result.getOrAwaitValue() }
            coVerify {
                qrCodeValidator wasNot called
            }
        }
    }

    @Test
    fun `onParseResult reports error on parse result failure`() {
        val error = Exception("Test error")
        val failure = QrCodeBoofCVParser.ParseResult.Failure(exception = error)

        with(viewModel()) {
            onParseResult(parseResult = failure)

            result.getOrAwaitValue() should beInstanceOf<Error>()
        }
    }

    @Test
    fun `calls CoronaTestQRCodeHandler with CoronaTestQRCode`() {
        val rawString = "CoronaTestQrCode"
        coEvery { qrCodeValidator.validate(rawString = rawString) } returns coronaTestQRCode

        val result = QrCodeBoofCVParser.ParseResult.Success(rawResults = setOf(rawString))

        with(viewModel()) {
            onParseResult(parseResult = result)

            coVerify {
                coronaTestQRCodeHandler.handle(coronaTestQRCode = coronaTestQRCode)
            }
        }
    }

    @Test
    fun `maps CoronaTestQRCodeHandlerEvent to corresponding result`() = runBlocking {
        with(viewModel()) {
            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.DuplicateTest(coronaTestQRCode),
                expectedResult = DuplicateTest(coronaTestQRCode)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.InRecycleBin(recycledPCR),
                expectedResult = InRecycleBin(recycledPCR)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.NeedsConsent(coronaTestQRCode),
                expectedResult = ConsentTest(coronaTestQRCode)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.TestNegative(anotherPCR),
                expectedResult = TestNegative(anotherPCR)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.TestPending(recycledRAT),
                expectedResult = TestPending(recycledRAT)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.TestPositive(anotherRAT),
                expectedResult = TestPositive(anotherRAT)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.TestInvalid(anotherRAT),
                expectedResult = TestInvalid(anotherRAT)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.WarnOthers(anotherPCR),
                expectedResult = WarnOthers(anotherPCR)
            )

            checkEvent(
                event = CoronaTestQRCodeHandlerEvent.RestoreDuplicateTest(recycledPCR.toRestoreRecycledTestRequest()),
                expectedResult = RestoreDuplicateTest(recycledPCR.toRestoreRecycledTestRequest())
            )
        }
    }

    private suspend fun QrCodeScannerViewModel.checkEvent(
        event: CoronaTestQRCodeHandlerEvent,
        expectedResult: ScannerResult
    ) {
        mutableCoronaTestQRCodeHandlerEvent.emit(event)
        result.getOrAwaitValue() shouldBe expectedResult
    }

    fun viewModel() = QrCodeScannerViewModel(
        qrCodeFileParser = qrCodeFileParser,
        dccHandler = dccHandler,
        checkInHandler = checkInHandler,
        dccSettings = dccSettings,
        traceLocationSettings = traceLocationSettings,
        dispatcherProvider = TestDispatcherProvider(),
        qrCodeValidator = qrCodeValidator,
        recycledCertificatesProvider = recycledCertificatesProvider,
        dccTicketingQrCodeHandler = dccTicketingQrCodeHandler,
        dccMaxPersonChecker = dccMaxPersonChecker,
        coronaTestQRCodeHandler = coronaTestQRCodeHandler
    )
}
