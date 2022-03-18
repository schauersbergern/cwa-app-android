package de.rki.coronawarnapp.covidcertificate.expiration

import de.rki.coronawarnapp.covidcertificate.common.certificate.CwaCovidCertificate.State
import de.rki.coronawarnapp.covidcertificate.common.repository.RecoveryCertificateContainerId
import de.rki.coronawarnapp.covidcertificate.common.repository.TestCertificateContainerId
import de.rki.coronawarnapp.covidcertificate.common.repository.VaccinationCertificateContainerId
import de.rki.coronawarnapp.covidcertificate.recovery.core.RecoveryCertificate
import de.rki.coronawarnapp.covidcertificate.recovery.core.RecoveryCertificateRepository
import de.rki.coronawarnapp.covidcertificate.recovery.core.RecoveryCertificateWrapper
import de.rki.coronawarnapp.covidcertificate.test.core.TestCertificate
import de.rki.coronawarnapp.covidcertificate.test.core.TestCertificateRepository
import de.rki.coronawarnapp.covidcertificate.test.core.TestCertificateWrapper
import de.rki.coronawarnapp.covidcertificate.vaccination.core.CovidCertificateSettings
import de.rki.coronawarnapp.covidcertificate.vaccination.core.VaccinationCertificate
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.VaccinationCertificateRepository
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.VaccinationCertificateWrapper
import de.rki.coronawarnapp.util.TimeStamper
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.joda.time.Duration
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.preferences.mockFlowPreference

class DccExpirationNotificationServiceTest : BaseTest() {
    @MockK lateinit var expirationNotification: DccExpirationNotification
    @MockK lateinit var vaccinationCertificateRepository: VaccinationCertificateRepository
    @MockK lateinit var recoveryRepository: RecoveryCertificateRepository
    @MockK lateinit var testCertificateRepository: TestCertificateRepository
    @MockK lateinit var covidCertificateSettings: CovidCertificateSettings
    @MockK lateinit var timeStamper: TimeStamper

    @MockK lateinit var vaccinationCertificateWrapper: VaccinationCertificateWrapper
    @MockK lateinit var vaccinationCertificate: VaccinationCertificate
    private val vaccinationContainerId = VaccinationCertificateContainerId("vac")

    @MockK lateinit var recoveryCertificateWrapper: RecoveryCertificateWrapper
    @MockK lateinit var recoveryCertificate: RecoveryCertificate
    private val recoverContainerId = RecoveryCertificateContainerId("rec")

    @MockK lateinit var testCertificateWrapper: TestCertificateWrapper
    @MockK lateinit var testCertificate: TestCertificate
    private val testContainerId = TestCertificateContainerId("test")

    private val lastDccStateBackgroundCheck = mockFlowPreference(Instant.EPOCH)
    private val nowUtc = Instant.EPOCH.plus(Duration.standardDays(7))

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { timeStamper.nowUTC } returns nowUtc

        every { covidCertificateSettings.lastDccStateBackgroundCheck } returns lastDccStateBackgroundCheck

        expirationNotification.apply {
            coEvery { showNotification(any()) } returns true
        }

        vaccinationCertificateRepository.apply {
            every { freshCertificates } returns flowOf(setOf(vaccinationCertificateWrapper))
            coEvery { setNotifiedState(any(), any(), any()) } just Runs
        }

        every { vaccinationCertificateWrapper.vaccinationCertificate } returns vaccinationCertificate

        vaccinationCertificate.apply {
            every { getState() } returns State.Valid(expiresAt = Instant.EPOCH)
            every { containerId } returns vaccinationContainerId
            every { notifiedExpiresSoonAt } returns null
            every { notifiedExpiredAt } returns null
            every { notifiedInvalidAt } returns null
            every { notifiedBlockedAt } returns null
        }

        recoveryRepository.apply {
            coEvery { freshCertificates } returns flowOf(setOf(recoveryCertificateWrapper))
            coEvery { setNotifiedState(any(), any(), any()) } just Runs
        }
        every { recoveryCertificateWrapper.recoveryCertificate } returns recoveryCertificate
        recoveryCertificate.apply {
            every { getState() } returns State.Valid(expiresAt = Instant.EPOCH)
            every { containerId } returns recoverContainerId
            every { notifiedExpiresSoonAt } returns null
            every { notifiedExpiredAt } returns null
            every { notifiedInvalidAt } returns null
            every { notifiedBlockedAt } returns null
        }

        every { testCertificateWrapper.testCertificate } returns testCertificate
        testCertificate.apply {
            every { getState() } returns State.Valid(expiresAt = Instant.EPOCH)
            every { containerId } returns testContainerId
            every { notifiedExpiresSoonAt } returns null
            every { notifiedExpiredAt } returns null
            every { notifiedInvalidAt } returns null
            every { notifiedBlockedAt } returns null
        }

        testCertificateRepository.apply {
            coEvery { setNotifiedState(any(), any(), any()) } just Runs
            every { certificates } returns flowOf(setOf(testCertificateWrapper))
        }
    }

    fun createInstance() = DccExpirationNotificationService(
        dscCheckNotification = expirationNotification,
        vaccinationCertificateRepository = vaccinationCertificateRepository,
        recoveryRepository = recoveryRepository,
        covidCertificateSettings = covidCertificateSettings,
        testCertificateRepository = testCertificateRepository,
        timeStamper = timeStamper,
    )

    @Test
    fun `only once per day`() = runTest {
        lastDccStateBackgroundCheck.update { timeStamper.nowUTC }
        createInstance().apply {
            showNotificationIfStateChanged()

            verify {
                vaccinationCertificateRepository wasNot Called
                recoveryRepository wasNot Called
                expirationNotification wasNot Called
            }
        }
    }

    @Test
    fun `check can be enforced`() = runTest {
        lastDccStateBackgroundCheck.update { timeStamper.nowUTC }
        createInstance().run {
            showNotificationIfStateChanged(ignoreLastCheck = true)

            verify {
                vaccinationCertificateRepository.freshCertificates
                recoveryRepository.freshCertificates
            }
        }
    }

    @Test
    fun `no certificates at all`() = runTest {
        every { vaccinationCertificateRepository.freshCertificates } returns flowOf(emptySet())
        every { recoveryRepository.freshCertificates } returns flowOf(emptySet())

        createInstance().showNotificationIfStateChanged()

        verify {
            vaccinationCertificateRepository.freshCertificates
            recoveryRepository.freshCertificates
            expirationNotification wasNot Called
        }
    }

    @Test
    fun `certificates that are all valid`() = runTest {
        createInstance().showNotificationIfStateChanged()

        verify { expirationNotification wasNot Called }

        coVerify(exactly = 0) {
            vaccinationCertificateRepository.setNotifiedState(any(), any(), any())
            recoveryRepository.setNotifiedState(any(), any(), any())
        }
    }

    @Test
    fun `two expired certificates`() = runTest {
        every { vaccinationCertificate.getState() } returns State.Expired(expiredAt = Instant.EPOCH)
        every { recoveryCertificate.getState() } returns State.Expired(expiredAt = Instant.EPOCH)

        createInstance().showNotificationIfStateChanged()

        coVerify { expirationNotification.showNotification(any()) }
    }

    @Test
    fun `two soon expiring certificates`() = runTest {
        every { vaccinationCertificate.getState() } returns State.ExpiringSoon(expiresAt = Instant.EPOCH)
        every { recoveryCertificate.getState() } returns State.ExpiringSoon(expiresAt = Instant.EPOCH)

        createInstance().showNotificationIfStateChanged()

        coVerify { expirationNotification.showNotification(any()) }
    }

    @Test
    fun `one of each`() = runTest {
        every { vaccinationCertificate.getState() } returns State.ExpiringSoon(expiresAt = Instant.EPOCH)
        every { recoveryCertificate.getState() } returns State.Expired(expiredAt = Instant.EPOCH)
        every { testCertificate.getState() } returns State.Blocked

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 3) { expirationNotification.showNotification(any()) }

        coVerify(exactly = 1) {
            vaccinationCertificateRepository.setNotifiedState(
                containerId = vaccinationContainerId,
                state = State.ExpiringSoon(expiresAt = Instant.EPOCH),
                time = nowUtc,
            )

            recoveryRepository.setNotifiedState(
                containerId = recoverContainerId,
                state = State.Expired(expiredAt = Instant.EPOCH),
                time = nowUtc,
            )

            testCertificateRepository.setNotifiedState(
                containerId = testContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one invalid each - one notification`() = runTest {
        every { vaccinationCertificate.getState() } returns State.Invalid()
        every { recoveryCertificate.getState() } returns State.Invalid()
        every { testCertificate.getState() } returns State.Invalid()

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            vaccinationCertificateRepository.setNotifiedState(
                containerId = vaccinationContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )
        }

        coVerify(exactly = 0) {
            recoveryRepository.setNotifiedState(
                containerId = recoverContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )

            testCertificateRepository.setNotifiedState(
                containerId = testContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one invalid test certificate`() = runTest {
        every { testCertificate.getState() } returns State.Invalid()

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            testCertificateRepository.setNotifiedState(
                containerId = testContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one invalid vaccination certificate`() = runTest {
        every { vaccinationCertificate.getState() } returns State.Invalid()

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            vaccinationCertificateRepository.setNotifiedState(
                containerId = vaccinationContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one invalid recovery certificate`() = runTest {
        every { recoveryCertificate.getState() } returns State.Invalid()

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            recoveryRepository.setNotifiedState(
                containerId = recoverContainerId,
                state = State.Invalid(),
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one blocked each - one notification`() = runTest {
        every { vaccinationCertificate.getState() } returns State.Blocked
        every { recoveryCertificate.getState() } returns State.Blocked
        every { testCertificate.getState() } returns State.Blocked

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            vaccinationCertificateRepository.setNotifiedState(
                containerId = vaccinationContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }

        coVerify(exactly = 0) {
            recoveryRepository.setNotifiedState(
                containerId = recoverContainerId,
                state = State.Blocked,
                time = nowUtc,
            )

            testCertificateRepository.setNotifiedState(
                containerId = testContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }
    }

    @Test
    fun `blocked test certificate notification`() = runTest {
        every { testCertificate.getState() } returns State.Blocked

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            testCertificateRepository.setNotifiedState(
                containerId = testContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }
    }

    @Test
    fun `blocked vaccination certificate notification`() = runTest {
        every { vaccinationCertificate.getState() } returns State.Blocked

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            vaccinationCertificateRepository.setNotifiedState(
                containerId = vaccinationContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }
    }

    @Test
    fun `blocked recovery certificate notification`() = runTest {
        every { recoveryCertificate.getState() } returns State.Blocked

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 1) {
            expirationNotification.showNotification(any())
            recoveryRepository.setNotifiedState(
                containerId = recoverContainerId,
                state = State.Blocked,
                time = nowUtc,
            )
        }
    }

    @Test
    fun `one of each but already notified the user`() = runTest {
        vaccinationCertificate.apply {
            every { getState() } returns State.ExpiringSoon(expiresAt = Instant.EPOCH)
            every { notifiedExpiresSoonAt } returns Instant.EPOCH
        }
        recoveryCertificate.apply {
            every { getState() } returns State.Expired(expiredAt = Instant.EPOCH)
            every { notifiedExpiredAt } returns Instant.EPOCH
        }

        createInstance().showNotificationIfStateChanged()

        coVerify(exactly = 0) {
            expirationNotification.showNotification(any())
        }
    }
}
