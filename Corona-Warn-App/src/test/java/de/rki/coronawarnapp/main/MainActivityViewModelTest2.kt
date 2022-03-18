package de.rki.coronawarnapp.main

import de.rki.coronawarnapp.contactdiary.ui.ContactDiarySettings
import de.rki.coronawarnapp.contactdiary.util.getLocale
import de.rki.coronawarnapp.coronatest.CoronaTestRepository
import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.qrcode.handler.CoronaTestQRCodeHandler
import de.rki.coronawarnapp.coronatest.qrcode.handler.CoronaTestQRCodeHandlerEvent
import de.rki.coronawarnapp.coronatest.qrcode.rapid.RapidAntigenQrCodeExtractor
import de.rki.coronawarnapp.coronatest.qrcode.rapid.RapidPcrQrCodeExtractor
import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesProvider
import de.rki.coronawarnapp.covidcertificate.vaccination.core.CovidCertificateSettings
import de.rki.coronawarnapp.covidcertificate.valueset.ValueSetsRepository
import de.rki.coronawarnapp.environment.EnvironmentSetup
import de.rki.coronawarnapp.playbook.BackgroundNoise
import de.rki.coronawarnapp.presencetracing.TraceLocationSettings
import de.rki.coronawarnapp.presencetracing.checkins.CheckInRepository
import de.rki.coronawarnapp.storage.OnboardingSettings
import de.rki.coronawarnapp.storage.TracingSettings
import de.rki.coronawarnapp.ui.main.MainActivityViewModel
import de.rki.coronawarnapp.util.CWADebug
import de.rki.coronawarnapp.util.device.BackgroundModeStatus
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import testhelpers.preferences.mockFlowPreference
import java.util.Locale

@ExtendWith(InstantExecutorExtension::class)
class MainActivityViewModelTest2 : BaseTest() {

    @MockK lateinit var environmentSetup: EnvironmentSetup
    @MockK lateinit var backgroundModeStatus: BackgroundModeStatus
    @MockK lateinit var diarySettings: ContactDiarySettings
    @MockK lateinit var backgroundNoise: BackgroundNoise
    @MockK lateinit var onboardingSettings: OnboardingSettings
    @MockK lateinit var traceLocationSettings: TraceLocationSettings
    @MockK lateinit var checkInRepository: CheckInRepository
    @MockK lateinit var covidCertificateSettings: CovidCertificateSettings
    @MockK lateinit var personCertificatesProvider: PersonCertificatesProvider
    @MockK lateinit var coronTestRepository: CoronaTestRepository
    @MockK lateinit var valueSetsRepository: ValueSetsRepository
    @MockK lateinit var tracingSettings: TracingSettings
    @MockK lateinit var coronaTestQRCodeHandler: CoronaTestQRCodeHandler

    private val raExtractor = spyk(RapidAntigenQrCodeExtractor())
    private val rPcrExtractor = spyk(RapidPcrQrCodeExtractor())

    private lateinit var mutableCoronaTestQRCodeHandlerEvent: MutableSharedFlow<CoronaTestQRCodeHandlerEvent>

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic("de.rki.coronawarnapp.contactdiary.util.ContactDiaryExtensionsKt")

        mockkObject(CWADebug)

        every { onboardingSettings.isOnboarded } returns true
        every { onboardingSettings.fabScannerOnboardingDone } returns mockFlowPreference(true)
        every { environmentSetup.currentEnvironment } returns EnvironmentSetup.Type.WRU
        every { traceLocationSettings.onboardingStatus } returns mockFlowPreference(
            TraceLocationSettings.OnboardingStatus.NOT_ONBOARDED
        )
        every { onboardingSettings.isBackgroundCheckDone } returns true
        every { checkInRepository.checkInsWithinRetention } returns MutableStateFlow(listOf())
        every { coronTestRepository.coronaTests } returns flowOf()
        every { valueSetsRepository.context } returns mockk()
        every { valueSetsRepository.context.getLocale() } returns Locale.GERMAN
        every { valueSetsRepository.triggerUpdateValueSet(any()) } just Runs

        personCertificatesProvider.apply {
            every { personCertificates } returns emptyFlow()
            every { personsBadgeCount } returns flowOf(0)
        }

        every { tracingSettings.showRiskLevelBadge } returns mockFlowPreference(false)

        mutableCoronaTestQRCodeHandlerEvent = MutableSharedFlow()
        every { coronaTestQRCodeHandler.event } returns mutableCoronaTestQRCodeHandlerEvent
    }

    private fun createInstance(): MainActivityViewModel = MainActivityViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        environmentSetup = environmentSetup,
        backgroundModeStatus = backgroundModeStatus,
        contactDiarySettings = diarySettings,
        backgroundNoise = backgroundNoise,
        onboardingSettings = onboardingSettings,
        checkInRepository = checkInRepository,
        traceLocationSettings = traceLocationSettings,
        covidCertificateSettings = covidCertificateSettings,
        personCertificatesProvider = personCertificatesProvider,
        raExtractor = raExtractor,
        rPcrExtractor = rPcrExtractor,
        coronaTestRepository = coronTestRepository,
        valueSetRepository = valueSetsRepository,
        tracingSettings = tracingSettings,
        coronaTestQRCodeHandler = coronaTestQRCodeHandler
    )

    @Test
    fun `Home screen badge count shows tests badges only`() {
        val coronaTest = mockk<CoronaTest>().apply { every { didShowBadge } returns false }
        every { tracingSettings.showRiskLevelBadge } returns mockFlowPreference(false)
        every { coronTestRepository.coronaTests } returns flowOf(setOf(coronaTest))

        createInstance().mainBadgeCount.getOrAwaitValue() shouldBe 1
    }

    @Test
    fun `Home screen badge count shows risk badges only`() {
        every { tracingSettings.showRiskLevelBadge } returns mockFlowPreference(true)
        every { coronTestRepository.coronaTests } returns flowOf(emptySet())

        createInstance().mainBadgeCount.getOrAwaitValue() shouldBe 1
    }

    @Test
    fun `Home screen badge count shows risk + tests badges only`() {
        val coronaTest = mockk<CoronaTest>().apply { every { didShowBadge } returns false }
        every { tracingSettings.showRiskLevelBadge } returns mockFlowPreference(true)
        every { coronTestRepository.coronaTests } returns flowOf(setOf(coronaTest))

        createInstance().mainBadgeCount.getOrAwaitValue() shouldBe 2
    }

    @Test
    fun `Home screen badge count shows risk + tests badges is ZERO`() {
        val coronaTest = mockk<CoronaTest>().apply { every { didShowBadge } returns true }
        every { tracingSettings.showRiskLevelBadge } returns mockFlowPreference(false)
        every { coronTestRepository.coronaTests } returns flowOf(setOf(coronaTest))

        createInstance().mainBadgeCount.getOrAwaitValue() shouldBe 0
    }

    @Test
    fun `onNavigationUri - R-PCR test uri string`() {
        val coronaTestQrCode = CoronaTestQRCode.RapidPCR(
            rawQrCode = "rawQrCode",
            hash = "hash",
            createdAt = Instant.EPOCH
        )
        val uriString = "R-PCR uri string"

        coEvery { rPcrExtractor.canHandle(uriString) } returns true
        coEvery { rPcrExtractor.extract(uriString) } returns coronaTestQrCode

        createInstance().onNavigationUri(uriString)

        coVerify {
            coronaTestQRCodeHandler.handle(coronaTestQrCode)
        }
    }

    @Test
    fun `onNavigationUri - RAT test uri string`() {
        val coronaTestQrCode = CoronaTestQRCode.RapidAntigen(
            rawQrCode = "rawQrCode",
            hash = "hash",
            createdAt = Instant.EPOCH
        )
        val uriString = "RAT uri string"

        coEvery { raExtractor.canHandle(uriString) } returns true
        coEvery { raExtractor.extract(uriString) } returns coronaTestQrCode

        createInstance().onNavigationUri(uriString)

        coVerify {
            coronaTestQRCodeHandler.handle(coronaTestQrCode)
        }
    }

    @Test
    fun `Forwards CoronaTestQRCodeHandler events`() = runBlocking {
        with(createInstance()) {
            checkEvent(CoronaTestQRCodeHandlerEvent.InRecycleBin(recycledCoronaTest = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.DuplicateTest(coronaTestQRCode = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.NeedsConsent(coronaTestQRCode = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.TestPositive(test = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.TestNegative(test = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.TestInvalid(test = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.TestPending(test = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.WarnOthers(test = mockk()))
            checkEvent(CoronaTestQRCodeHandlerEvent.RestoreDuplicateTest(restoreRecycledTestRequest = mockk()))
        }
    }

    private suspend fun MainActivityViewModel.checkEvent(event: CoronaTestQRCodeHandlerEvent) {
        mutableCoronaTestQRCodeHandlerEvent.emit(event)
        coronaTestQrEvent.getOrAwaitValue() shouldBe event
    }
}
