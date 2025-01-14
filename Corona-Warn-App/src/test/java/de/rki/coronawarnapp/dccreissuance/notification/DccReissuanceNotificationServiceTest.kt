package de.rki.coronawarnapp.dccreissuance.notification

import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.ccl.dccwalletinfo.model.DccWalletInfo
import de.rki.coronawarnapp.covidcertificate.common.certificate.CertificatePersonIdentifier
import de.rki.coronawarnapp.covidcertificate.notification.PersonNotificationSender
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesSettings
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

internal class DccReissuanceNotificationServiceTest : BaseTest() {

    @MockK lateinit var personNotificationSender: PersonNotificationSender
    @MockK lateinit var personCertificatesSettings: PersonCertificatesSettings
    @MockK lateinit var dccWalletInfo: DccWalletInfo

    private val personIdentifier = CertificatePersonIdentifier(
        dateOfBirthFormatted = "01-01-2010",
        firstNameStandardized = "fN",
        lastNameStandardized = "lN",
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { personCertificatesSettings.setDccReissuanceNotifiedAt(any(), any()) } just Runs
        coEvery { personCertificatesSettings.dismissReissuanceBadge(any()) } just Runs
        every { personNotificationSender.showNotification(any(), any()) } just Runs
        every { dccWalletInfo.certificateReissuance } returns mockk()
    }

    @Test
    fun `notify person if no previous dcc reissuance`() = runBlockingTest {
        DccReissuanceNotificationService(
            personCertificatesSettings = personCertificatesSettings,
            personNotificationSender = personNotificationSender
        ).notifyIfNecessary(
            personIdentifier = personIdentifier,
            oldWalletInfo = null,
            newWalletInfo = dccWalletInfo
        )

        coVerify {
            personNotificationSender.showNotification(personIdentifier, R.string.notification_body_certificate)
            personCertificatesSettings.setDccReissuanceNotifiedAt(personIdentifier, any())
        }
    }

    @Test
    fun `don't notify person if there is previous dcc reissuance`() = runBlockingTest {
        DccReissuanceNotificationService(
            personCertificatesSettings = personCertificatesSettings,
            personNotificationSender = personNotificationSender
        ).notifyIfNecessary(
            personIdentifier = personIdentifier,
            oldWalletInfo = mockk<DccWalletInfo>().apply { every { certificateReissuance } returns mockk() },
            newWalletInfo = dccWalletInfo
        )

        coVerify(exactly = 0) {
            personNotificationSender.showNotification(personIdentifier, R.string.notification_body_certificate)
            personCertificatesSettings.setDccReissuanceNotifiedAt(personIdentifier, any())
        }
    }

    @Test
    fun `dismiss the badge if the new dcc reissuance doesn't exist`() = runBlockingTest {
        DccReissuanceNotificationService(
            personCertificatesSettings = personCertificatesSettings,
            personNotificationSender = personNotificationSender
        ).notifyIfNecessary(
            personIdentifier = personIdentifier,
            oldWalletInfo = dccWalletInfo,
            newWalletInfo = mockk<DccWalletInfo>().apply { every { certificateReissuance } returns null }
        )

        coEvery {
            personCertificatesSettings.dismissReissuanceBadge(personIdentifier)
        }

        coVerify(exactly = 0) {
            personNotificationSender.showNotification(personIdentifier, R.string.notification_body_certificate)
            personCertificatesSettings.setDccReissuanceNotifiedAt(personIdentifier, any())
        }
    }
}
