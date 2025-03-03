package de.rki.coronawarnapp.ccl.ui.text

import androidx.test.platform.app.InstrumentationRegistry
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import de.rki.coronawarnapp.ccl.dccwalletinfo.calculation.CclJsonFunctions
import de.rki.coronawarnapp.ccl.dccwalletinfo.model.CclText
import de.rki.coronawarnapp.util.BuildVersionWrap
import de.rki.coronawarnapp.util.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import kotlinx.coroutines.test.runBlockingTest
import org.joda.time.DateTimeZone
import org.junit.Before
import org.junit.Test
import testhelpers.BaseTestInstrumentation
import java.nio.file.Paths
import java.util.Locale
import java.util.TimeZone

class CclTextFormatterTest : BaseTestInstrumentation() {

    @MockK private lateinit var cclJsonFunctions: CclJsonFunctions
    private val mapper = SerializationModule.jacksonBaseMapper

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(BuildVersionWrap)
        Locale.setDefault(Locale.GERMAN)

        val timeZone = TimeZone.getTimeZone("Europe/Berlin")
        TimeZone.setDefault(timeZone)
        DateTimeZone.setDefault(DateTimeZone.forTimeZone(timeZone))
    }

    @Test
    fun runFormat24() {
        every { BuildVersionWrap.SDK_INT } returns 24
        testCases()
    }

    @Test
    fun runFormat23() {
        every { BuildVersionWrap.SDK_INT } returns 23
        testCases()
    }

    private fun testCases() = runBlockingTest {
        val format = CclTextFormatter(cclJsonFunctions, mapper)
        val path = Paths.get("ccl", "ccl-text-descriptor-test-cases.gen.json").toString()
        val stream = InstrumentationRegistry.getInstrumentation().context.assets.open(path)
        val testCases = SerializationModule().jacksonObjectMapper().readValue<TestCases>(stream)
        testCases.testCases.forEach { testCase ->
            format(
                testCase.textDescriptor,
                testCase.assertions[0].languageCode,
                Locale.GERMAN
            ) shouldBe testCase.assertions[0].text
        }
    }
}

data class TestCase(
    @JsonProperty("description")
    val description: String,

    @JsonProperty("textDescriptor")
    val textDescriptor: CclText,

    @JsonProperty("assertions")
    val assertions: List<Assertions>
)

data class Assertions(
    @JsonProperty("languageCode")
    val languageCode: String,

    @JsonProperty("text")
    val text: String
)

data class TestCases(
    @JsonProperty("\$comment")
    val comment: String,
    @JsonProperty("testCases")
    val testCases: List<TestCase>
)
