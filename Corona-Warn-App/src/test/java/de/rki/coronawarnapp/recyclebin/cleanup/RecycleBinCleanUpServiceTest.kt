package de.rki.coronawarnapp.recyclebin.cleanup

import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.covidcertificate.common.certificate.CwaCovidCertificate
import de.rki.coronawarnapp.covidcertificate.common.repository.CertificateContainerId
import de.rki.coronawarnapp.reyclebin.covidcertificate.RecycledCertificatesProvider
import de.rki.coronawarnapp.reyclebin.cleanup.RecycleBinCleanUpService
import de.rki.coronawarnapp.reyclebin.coronatest.RecycledCoronaTestsProvider
import de.rki.coronawarnapp.util.TimeStamper
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.joda.time.Days
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RecycleBinCleanUpServiceTest : BaseTest() {

    private val now = Instant.parse("2021-10-13T12:00:00.000Z")

    @MockK lateinit var timeStamper: TimeStamper
    @RelaxedMockK lateinit var recycledCertificatesProvider: RecycledCertificatesProvider
    @RelaxedMockK lateinit var recycledCoronaTestsProvider: RecycledCoronaTestsProvider

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { timeStamper.nowUTC } returns Instant.parse("2021-10-13T12:00:00.000Z")
    }

    private fun createInstance() = RecycleBinCleanUpService(
        recycledCertificatesProvider = recycledCertificatesProvider,
        recycledCoronaTestsProvider = recycledCoronaTestsProvider,
        timeStamper = timeStamper
    )

    private fun createCert(days: Int) = createCert(recycleTime = now.minus(Days.days(days).toStandardDuration()))

    private fun createCert(recycleTime: Instant): CwaCovidCertificate {
        val mockContainerId = mockk<CertificateContainerId>()
        return mockk {
            every { recycledAt } returns recycleTime
            every { containerId } returns mockContainerId
        }
    }

    private fun createTest(days: Int) = createTest(recycleTime = now.minus(Days.days(days).toStandardDuration()))

    private fun createTest(recycleTime: Instant): CoronaTest = mockk {
        every { recycledAt } returns recycleTime
        every { identifier } returns recycleTime.toString()
    }

    @Test
    fun `No recycled items, nothing to delete`() = runBlockingTest {
        every { recycledCertificatesProvider.recycledCertificates } returns flowOf(emptySet())
        every { recycledCoronaTestsProvider.tests } returns flowOf(emptySet())

        createInstance().clearRecycledItems()

        coVerify(exactly = 0) {
            recycledCertificatesProvider.deleteAllCertificate(any())
            recycledCoronaTestsProvider.deleteAllCoronaTest(any())
        }
    }

    @Test
    fun `Retention time in recycle bin too short, nothing to delete`() = runBlockingTest {
        val certWith0DaysOfRetention = createCert(0)
        val certWith30DaysOfRetention = createCert(30)
        val testWith0DaysOfRetention = createTest(0)
        val testWith30DaysOfRetention = createTest(30)

        every { recycledCertificatesProvider.recycledCertificates } returns flowOf(
            setOf(certWith0DaysOfRetention, certWith30DaysOfRetention)
        )

        every { recycledCoronaTestsProvider.tests } returns flowOf(
            setOf(testWith0DaysOfRetention, testWith30DaysOfRetention)
        )

        createInstance().clearRecycledItems()

        coVerify(exactly = 0) {
            recycledCertificatesProvider.deleteAllCertificate(any())
            recycledCoronaTestsProvider.deleteAllCoronaTest(any())
        }
    }

    @Test
    fun `Time difference between recycledAt and now is greater than 30 days with ms precision`() = runBlockingTest {
        val nowMinus30Days = now.minus(Days.days(30).toStandardDuration())
        val nowMinus30DaysAnd1Ms = nowMinus30Days.minus(1)

        val certExact30Days = createCert(nowMinus30Days)
        val cert30DaysAnd1Ms = createCert(nowMinus30DaysAnd1Ms)
        val testExact30Days = createTest(nowMinus30Days)
        val test30DaysAnd1Ms = createTest(nowMinus30DaysAnd1Ms)

        every { recycledCertificatesProvider.recycledCertificates } returns flowOf(
            setOf(certExact30Days, cert30DaysAnd1Ms)
        )

        every { recycledCoronaTestsProvider.tests } returns flowOf(
            setOf(testExact30Days, test30DaysAnd1Ms)
        )

        createInstance().clearRecycledItems()

        val containerIds = listOf(cert30DaysAnd1Ms.containerId)
        val identifiers = listOf(test30DaysAnd1Ms.identifier)
        coVerify(exactly = 1) {
            recycledCertificatesProvider.deleteAllCertificate(containerIds)
            recycledCoronaTestsProvider.deleteAllCoronaTest(identifiers)
        }
    }
}
