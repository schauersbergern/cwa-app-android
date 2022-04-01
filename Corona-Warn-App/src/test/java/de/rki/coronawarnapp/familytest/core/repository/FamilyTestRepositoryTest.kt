package de.rki.coronawarnapp.familytest.core.repository

import de.rki.coronawarnapp.coronatest.qrcode.CoronaTestQRCode
import de.rki.coronawarnapp.coronatest.server.CoronaTestResult
import de.rki.coronawarnapp.coronatest.type.BaseCoronaTest
import de.rki.coronawarnapp.familytest.core.model.CoronaTest
import de.rki.coronawarnapp.familytest.core.model.FamilyCoronaTest
import de.rki.coronawarnapp.familytest.core.model.updateTestResult
import de.rki.coronawarnapp.familytest.core.notification.FamilyTestNotificationService
import de.rki.coronawarnapp.familytest.core.storage.FamilyTestStorage
import de.rki.coronawarnapp.util.TimeStamper
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FamilyTestRepositoryTest : BaseTest() {

    @MockK lateinit var processor: CoronaTestProcessor
    @MockK lateinit var storage: FamilyTestStorage
    @MockK lateinit var timeStamper: TimeStamper
    @MockK lateinit var familyTestNotificationService: FamilyTestNotificationService

    private val nowUTC = Instant.parse("2021-03-15T05:45:00.000Z")

    private val qrCode = CoronaTestQRCode.PCR(
        qrCodeGUID = "guid",
        rawQrCode = "rawQrCode"
    )

    private val identifier = "identifier"
    private val test = CoronaTest(
        type = BaseCoronaTest.Type.PCR,
        identifier = identifier,
        registeredAt = nowUTC,
        registrationToken = "regtoken",
        testResult = CoronaTestResult.PCR_OR_RAT_PENDING
    )

    private val familyTest = FamilyCoronaTest(
        personName = "Maria",
        test
    )

    private val update = CoronaTestProcessor.ServerResponse.CoronaTestResultUpdate(
        coronaTestResult = CoronaTestResult.PCR_POSITIVE
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { timeStamper.nowUTC } returns nowUTC
        coEvery { processor.register(qrCode) } returns test
        coEvery { processor.pollServer(test) } returns update
        coEvery { storage.familyTestMap } returns flowOf(mapOf(identifier to familyTest))
        coEvery { storage.familyTestRecycleBinMap } returns flowOf(mapOf())
        coEvery { storage.save(familyTest) } just Runs
        coEvery { storage.update(any(), any()) } just Runs
        coEvery { storage.delete(familyTest) } just Runs
        every { familyTestNotificationService.showTestResultNotification() } just Runs
    }

    @Test
    fun `registration works`() = runBlockingTest {
        createInstance().registerTest(qrCode, "Maria") shouldBe familyTest

        coVerifySequence {
            storage.familyTestMap
            storage.familyTestRecycleBinMap
            processor.register(qrCode)
            storage.save(familyTest)
        }
    }

    @Test
    fun `refresh works`() = runBlockingTest {
        createInstance().refresh()
        coVerifySequence {
            storage.familyTestMap
            storage.familyTestRecycleBinMap
            processor.pollServer(test)
            storage.update(identifier, any())
        }
    }

    @Test
    fun `no polling for final results`() = runBlockingTest {
        coEvery { storage.familyTestMap } returns
            flowOf(mapOf(identifier to familyTest.updateTestResult(CoronaTestResult.PCR_POSITIVE)))
        createInstance().refresh()
        coVerify(exactly = 0) {
            processor.pollServer(test)
        }

        // Notification
        coVerify {
            storage.update(identifier, any())
        }
    }

    @Test
    fun `restoreTest calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.restoreTest(identifier)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `moveTestToRecycleBin calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.moveTestToRecycleBin(identifier)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `markViewed calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.markViewed(identifier)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `updateResultNotification calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.updateResultNotification(identifier, true)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `markBadgeAsViewed calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.markBadgeAsViewed(identifier)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `markDccAsCreated calls update`() = runBlockingTest {
        val instance = createInstance()
        instance.markDccAsCreated(identifier, true)
        coVerify {
            storage.update(identifier = identifier, any())
        }
    }

    @Test
    fun `delete calls storage delete`() = runBlockingTest {
        val instance = createInstance()
        instance.deleteTest(identifier)
        coVerify {
            storage.delete(familyTest)
        }
    }

    @Test
    fun `notifyIfNeeded notify and update tests after change`() = runBlockingTest {
        val familyTest1 = FamilyCoronaTest(
            personName = "Person 1",
            coronaTest = CoronaTest(
                identifier = "id-1",
                type = BaseCoronaTest.Type.PCR,
                registeredAt = Instant.EPOCH,
                registrationToken = "registrationToken",
                uiState = CoronaTest.UiState(
                    isResultAvailableNotificationSent = false,
                    hasResultChangeBadge = true
                )
            )
        )

        val familyTest2 = FamilyCoronaTest(
            personName = "Person 1",
            coronaTest = CoronaTest(
                identifier = "id-2",
                type = BaseCoronaTest.Type.RAPID_ANTIGEN,
                registeredAt = Instant.EPOCH,
                registrationToken = "registrationToken",
                uiState = CoronaTest.UiState(
                    isResultAvailableNotificationSent = true,
                    hasResultChangeBadge = true
                )
            )
        )

        val familyTest3 = FamilyCoronaTest(
            personName = "Person 1",
            coronaTest = CoronaTest(
                identifier = "id-3",
                type = BaseCoronaTest.Type.PCR,
                registeredAt = Instant.EPOCH,
                registrationToken = "registrationToken",
                uiState = CoronaTest.UiState(
                    isResultAvailableNotificationSent = false,
                    hasResultChangeBadge = false
                )
            )
        )

        val familyTest4 = FamilyCoronaTest(
            personName = "Person 1",
            coronaTest = CoronaTest(
                identifier = "id-4",
                type = BaseCoronaTest.Type.RAPID_ANTIGEN,
                registeredAt = Instant.EPOCH,
                registrationToken = "registrationToken",
                uiState = CoronaTest.UiState(
                    isResultAvailableNotificationSent = true,
                    hasResultChangeBadge = true
                )
            )
        )

        val familyTest5 = FamilyCoronaTest(
            personName = "Person 2",
            coronaTest = CoronaTest(
                identifier = "id-5",
                type = BaseCoronaTest.Type.PCR,
                registeredAt = Instant.EPOCH,
                registrationToken = "registrationToken",
                uiState = CoronaTest.UiState(
                    isResultAvailableNotificationSent = false,
                    hasResultChangeBadge = true
                )
            )
        )

        every { storage.familyTestMap } returns flowOf(
            setOf(
                familyTest1,
                familyTest2,
                familyTest3,
                familyTest4,
                familyTest5
            ).associateBy { it.identifier }
        )
        val instance = createInstance()
        instance.notifyIfNeeded()

        coVerify {
            familyTestNotificationService.showTestResultNotification()
            storage.update("id-1", any())
            storage.update("id-5", any())
        }

        coVerify(exactly = 0) {
            storage.update("id-2", any())
            storage.update("id-3", any())
            storage.update("id-4", any())
        }
    }

    private fun createInstance() = FamilyTestRepository(
        processor = processor,
        storage = storage,
        timeStamper = timeStamper,
        familyTestNotificationService = familyTestNotificationService
    )
}