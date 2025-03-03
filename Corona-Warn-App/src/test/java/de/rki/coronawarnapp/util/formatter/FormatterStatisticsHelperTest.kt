package de.rki.coronawarnapp.util.formatter

import android.content.Context
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.contactdiary.util.getLocale
import de.rki.coronawarnapp.statistics.IncidenceAndHospitalizationStats
import de.rki.coronawarnapp.statistics.InfectionStats
import de.rki.coronawarnapp.statistics.KeySubmissionsStats
import de.rki.coronawarnapp.statistics.SevenDayRValue
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.slot
import org.joda.time.Duration
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.Locale

class FormatterStatisticsHelperTest : BaseTest() {

    @MockK
    private lateinit var context: Context

    private val today = Instant()
    private val yesterday = today.minus(Duration.standardDays(1))

    @BeforeEach
    fun setUp() {
        val slot = slot<String>()
        MockKAnnotations.init(this)
        mockkStatic("de.rki.coronawarnapp.contactdiary.util.ContactDiaryExtensionsKt")
        with(context) {
            every { context.getLocale() } returns Locale.GERMANY
            every { getString(R.string.statistics_primary_value_current) } returns CURRENT
            every { getString(R.string.statistics_primary_value_today) } returns TODAY
            every { getString(R.string.statistics_primary_value_yesterday) } returns YESTERDAY
            every { getString(R.string.statistics_primary_value_until_today) } returns UNTIL_TODAY
            every { getString(R.string.statistics_primary_value_until_yesterday) } returns UNTIL_YESTERDAY
            every {
                getString(
                    R.string.statistics_primary_value_until,
                    capture(slot)
                )
            } answers { "Until ${slot.captured}" }
        }
    }

    @Test
    fun `InfectionStats primary label test`() {
        InfectionStats(today, listOf()).getPrimaryLabel(context) shouldBe TODAY
        InfectionStats(yesterday, listOf()).getPrimaryLabel(context) shouldBe YESTERDAY
        InfectionStats(
            Instant.parse("2021-01-13T00:00:00Z"),
            listOf()
        ).getPrimaryLabel(context) shouldBe "13.01.2021"
    }

    @Test
    fun `KeySubmissionsStats primary label test`() {
        KeySubmissionsStats(today, listOf()).getPrimaryLabel(context) shouldBe TODAY
        KeySubmissionsStats(yesterday, listOf()).getPrimaryLabel(context) shouldBe YESTERDAY
        KeySubmissionsStats(
            Instant.parse("2021-01-13T00:00:00Z"),
            listOf()
        ).getPrimaryLabel(context) shouldBe "13.01.2021"
    }

    @Test
    fun `IncidenceAndHospitalizationStats primary and secondary label test`() {
        IncidenceAndHospitalizationStats(today, listOf()).getPrimaryLabel(context) shouldBe UNTIL_TODAY
        getSecondaryLabel(context, today) shouldBe UNTIL_TODAY

        IncidenceAndHospitalizationStats(yesterday, listOf()).getPrimaryLabel(context) shouldBe UNTIL_YESTERDAY
        getSecondaryLabel(context, yesterday) shouldBe UNTIL_YESTERDAY

        IncidenceAndHospitalizationStats(
            Instant.parse("2021-01-13T00:00:00Z"),
            listOf()
        ).getPrimaryLabel(context) shouldBe "Until 13.01.2021"
        getSecondaryLabel(context, Instant.parse("2021-01-13T00:00:00Z")) shouldBe "Until 13.01.2021"
    }

    @Test
    fun `SevenDayRValue primary label test`() {
        SevenDayRValue(today, listOf()).getPrimaryLabel(context) shouldBe CURRENT
        SevenDayRValue(yesterday, listOf()).getPrimaryLabel(context) shouldBe YESTERDAY
        SevenDayRValue(
            Instant.parse("2021-01-13T00:00:00Z"),
            listOf()
        ).getPrimaryLabel(context) shouldBe "Until 13.01.2021"
    }

    companion object {
        private const val TODAY = "Today"
        private const val CURRENT = "Current"
        private const val YESTERDAY = "Yesterday"
        private const val UNTIL_TODAY = "Until today"
        private const val UNTIL_YESTERDAY = "Until yesterday"
    }
}
