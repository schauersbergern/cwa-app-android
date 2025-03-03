package de.rki.coronawarnapp.dccreissuance.ui.consent

import de.rki.coronawarnapp.ccl.dccwalletinfo.model.dccWalletInfoWithReissuance
import de.rki.coronawarnapp.ccl.ui.text.CclTextFormatter
import de.rki.coronawarnapp.covidcertificate.common.certificate.CertificatePersonIdentifier
import de.rki.coronawarnapp.covidcertificate.common.certificate.CwaCovidCertificate
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccData
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccQrCodeExtractor
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccV1
import de.rki.coronawarnapp.covidcertificate.common.qrcode.DccQrCode
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificates
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesProvider
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesSettings
import de.rki.coronawarnapp.covidcertificate.vaccination.core.VaccinationCertificate
import de.rki.coronawarnapp.dccreissuance.core.reissuer.DccReissuer
import de.rki.coronawarnapp.util.TimeAndDateExtensions.toLocalDateUtc
import de.rki.coronawarnapp.util.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import testhelpers.BaseTest
import testhelpers.TestDispatcherProvider
import testhelpers.extensions.InstantExecutorExtension
import testhelpers.extensions.getOrAwaitValue

@ExtendWith(InstantExecutorExtension::class)
internal class DccReissuanceConsentViewModelTest : BaseTest() {

    @MockK lateinit var personCertificatesProvider: PersonCertificatesProvider
    @MockK lateinit var dccReissuer: DccReissuer
    @MockK lateinit var dccQrCodeExtractor: DccQrCodeExtractor
    @MockK lateinit var personCertificatesSettings: PersonCertificatesSettings
    @MockK lateinit var dccQrCode: DccQrCode
    @MockK lateinit var metadata: DccV1.MetaData
    @MockK lateinit var cwaCertificates: CwaCovidCertificate

    private val identifier = CertificatePersonIdentifier(
        dateOfBirthFormatted = "01.10.1982",
        firstNameStandardized = "fN",
        lastNameStandardized = "lN"
    )

    private val vaccinationCertA = mockk<VaccinationCertificate>().apply {
        every { personIdentifier } returns identifier
        every { vaccinatedOn } returns Instant.EPOCH.toLocalDateUtc()
        every { hasNotificationBadge } returns false
        every { headerIssuedAt } returns Instant.EPOCH
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { cwaCertificates.personIdentifier } returns identifier
        every { personCertificatesProvider.findPersonByIdentifierCode(any()) } returns flowOf(
            PersonCertificates(
                certificates = listOf(vaccinationCertA),
                dccWalletInfo = dccWalletInfoWithReissuance
            )
        )

        coEvery { dccReissuer.startReissuance(any()) } just Runs
        coEvery { dccQrCodeExtractor.extract(any(), any()) } returns dccQrCode.apply {
            every { data } returns mockk<DccData<out DccV1.MetaData>>().apply {
                every { certificate } returns metadata
            }
        }
        coEvery { personCertificatesSettings.dismissReissuanceBadge(any()) } just Runs
    }

    @Test
    fun `getState works`() {
        viewModel().stateLiveData.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.State(
            certificate = metadata,
            divisionVisible = true,
            title = "Zertifikat ersetzen",
            subtitle = "Text",
            content = "Langer Text",
            url = "https://www.coronawarn.app/en/faq/#dcc_admission_state"
        )
    }

    @Test
    fun `getState no person dismisses the screen`() {
        every { personCertificatesProvider.findPersonByIdentifierCode(any()) } returns flowOf(null)

        viewModel().apply {
            stateLiveData.observeForever {} // Trigger flow
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.Back
        }
    }

    @Test
    fun `getState no reissuance dismisses the screen`() {
        every { personCertificatesProvider.findPersonByIdentifierCode(any()) } returns flowOf(
            PersonCertificates(
                certificates = listOf(vaccinationCertA),
                dccWalletInfo = null
            )
        )
        viewModel().apply {
            stateLiveData.observeForever {} // Trigger flow
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.Back
        }
    }

    @Test
    fun `startReissuance calls dccReissuer`() {
        viewModel().apply {
            startReissuance()
            coVerify { dccReissuer.startReissuance(any()) }
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.ReissuanceSuccess
        }
    }

    @Test
    fun `startReissuance posts an error`() {
        val exception = Exception("Hello World!")
        coEvery { dccReissuer.startReissuance(any()) } throws exception
        viewModel().apply {
            startReissuance()
            coVerify { dccReissuer.startReissuance(any()) }
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.ReissuanceError(exception)
        }
    }

    @Test
    fun navigateBack() {
        viewModel().apply {
            navigateBack()
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.Back
        }
    }

    @Test
    fun openPrivacyScreen() {
        viewModel().apply {
            openPrivacyScreen()
            event.getOrAwaitValue() shouldBe DccReissuanceConsentViewModel.OpenPrivacyScreen
        }
    }

    private fun viewModel() = DccReissuanceConsentViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        personCertificatesProvider = personCertificatesProvider,
        personIdentifierCode = "code",
        dccReissuer = dccReissuer,
        format = CclTextFormatter(cclJsonFunctions = mockk(), SerializationModule.jacksonBaseMapper),
        dccQrCodeExtractor = dccQrCodeExtractor,
        personCertificatesSettings = personCertificatesSettings
    )
}
