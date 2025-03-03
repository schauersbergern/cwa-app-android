package de.rki.coronawarnapp.ui.coronatest.rat.profile.create

import de.rki.coronawarnapp.coronatest.antigen.profile.RATProfile
import de.rki.coronawarnapp.coronatest.antigen.profile.RATProfileSettingsDataStore
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.extensions.InstantExecutorExtension
import testhelpers.extensions.getOrAwaitValue

@ExtendWith(InstantExecutorExtension::class)
internal class RATProfileDataCreateFragmentViewModelTest : BaseTest() {

    @MockK lateinit var ratProfileSettings: RATProfileSettingsDataStore

    private val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    private val birthDate: LocalDate = formatter.parseDateTime("01.01.1980").toLocalDate()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { ratProfileSettings.profileFlow } returns flowOf(null)
        every { ratProfileSettings.updateProfile(any()) } returns Job()
    }

    @Test
    fun `createProfile doesn't create profile when profile is invalid`() {
        viewModel().apply {
            createProfile()
            profile.getOrAwaitValue().isValid shouldBe false
        }

        verify(exactly = 1) {
            ratProfileSettings.profileFlow
        }
    }

    @Test
    fun `Saved profile is displayed`() {
        val savedProfile = RATProfile(
            firstName = "First name",
            lastName = "Last name",
            birthDate = birthDate,
            street = "Main street",
            zipCode = "12132",
            city = "London",
            phone = "111111111",
            email = "email@example.com"
        )
        every { ratProfileSettings.profileFlow } returns flowOf(savedProfile)

        viewModel().apply {
            // Fields updated
            firstNameChanged(savedProfile.firstName)
            lastNameChanged(savedProfile.lastName)
            birthDateChanged(savedProfile.birthDate?.toString(formatter))
            streetChanged(savedProfile.street)
            zipCodeChanged(savedProfile.zipCode)
            cityChanged(savedProfile.city)
            phoneChanged(savedProfile.phone)
            emailChanged(savedProfile.email)

            val input = latestProfile.getOrAwaitValue()!!
            val output = profile.getOrAwaitValue()
            input.firstName shouldBe output.firstName
            input.lastName shouldBe output.lastName
            input.phone shouldBe output.phone
            input.email shouldBe output.email
            input.city shouldBe output.city
            input.street shouldBe output.street
            input.zipCode shouldBe output.zipCode
        }
    }

    @Test
    fun `createProfile create profile when at least one field is set`() {
        viewModel().apply {
            firstNameChanged("First name")
            createProfile()
            events.getOrAwaitValue() shouldBe CreateRATProfileNavigation.ProfileScreen
        }

        verify {
            ratProfileSettings.profileFlow
        }
    }

    @Test
    fun firstNameChanged() {
        viewModel().apply {
            firstNameChanged("First name")
            profile.getOrAwaitValue().apply {
                firstName shouldBe "First name"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun lastNameChanged() {
        viewModel().apply {
            lastNameChanged("Last name")
            profile.getOrAwaitValue().apply {
                lastName shouldBe "Last name"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun birthDateChanged() {
        viewModel().apply {
            birthDateChanged("1.1.2021")
            profile.getOrAwaitValue().apply {
                birthDate shouldBe birthDate
                isValid shouldBe true
            }
        }
    }

    @Test
    fun streetChanged() {
        viewModel().apply {
            streetChanged("Main St.")
            profile.getOrAwaitValue().apply {
                street shouldBe "Main St."
                isValid shouldBe true
            }
        }
    }

    @Test
    fun zipCodeChanged() {
        viewModel().apply {
            zipCodeChanged("11111")
            profile.getOrAwaitValue().apply {
                zipCode shouldBe "11111"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun cityChanged() {
        viewModel().apply {
            cityChanged("London")
            profile.getOrAwaitValue().apply {
                city shouldBe "London"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun phoneChanged() {
        viewModel().apply {
            phoneChanged("111111111")
            profile.getOrAwaitValue().apply {
                phone shouldBe "111111111"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun emailChanged() {
        viewModel().apply {
            emailChanged("email@example.com")
            profile.getOrAwaitValue().apply {
                email shouldBe "email@example.com"
                isValid shouldBe true
            }
        }
    }

    @Test
    fun allFieldsAreSet() {
        viewModel().apply {
            firstNameChanged("First name")
            lastNameChanged("Last name")
            birthDateChanged("01.01.1980")
            streetChanged("Main street")
            zipCodeChanged("12132")
            cityChanged("London")
            phoneChanged("111111111")
            emailChanged("email@example.com")
            profile.getOrAwaitValue().apply {
                isValid shouldBe true
                this shouldBe RATProfileData(
                    firstName = "First name",
                    lastName = "Last name",
                    birthDate = birthDate,
                    street = "Main street",
                    zipCode = "12132",
                    city = "London",
                    phone = "111111111",
                    email = "email@example.com"
                )
            }
        }
    }

    @Test
    fun navigateBack() {
        viewModel().apply {
            navigateBack()
            events.getOrAwaitValue() shouldBe CreateRATProfileNavigation.Back
        }
    }

    fun viewModel() = RATProfileCreateFragmentViewModel(ratProfileSettings, formatter)
}
