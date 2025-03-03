package de.rki.coronawarnapp.test.deltaonboarding.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.databinding.FragmentTestDeltaonboardingBinding
import de.rki.coronawarnapp.test.menu.ui.TestMenuItem
import de.rki.coronawarnapp.util.di.AutoInject
import de.rki.coronawarnapp.util.ui.viewBinding
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactoryProvider
import de.rki.coronawarnapp.util.viewmodel.cwaViewModels
import javax.inject.Inject

@SuppressLint("SetTextI18n")
class DeltaOnboardingFragment : Fragment(R.layout.fragment_test_deltaonboarding), AutoInject {

    @Inject lateinit var viewModelFactory: CWAViewModelFactoryProvider.Factory
    private val viewModel: DeltaOnboardingFragmentViewModel by cwaViewModels { viewModelFactory }

    private val binding: FragmentTestDeltaonboardingBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchContactJournalOnboarding.isChecked = viewModel.isContactJournalOnboardingDone()
        binding.switchDeltaOnboarding.isChecked = viewModel.isDeltaOnboardingDone()
        binding.switchAttendeeOnboarding.isChecked = viewModel.isAttendeeOnboardingDone()
        binding.switchVaccinationOnboarding.isChecked = viewModel.isVaccinationRegistrationOnboardingDone()
        binding.switchNotificationsDeltaOnboarding.isChecked = viewModel.isNotificationsOnboardingDone()
        binding.switchAnalyticsDeltaOnboarding.isChecked = viewModel.isAnalyticsOnboardingDone()
        viewModel.changelogVersion.observe(viewLifecycleOwner) {
            binding.lastChangelogEdittext.setText(it.toString())
        }

        binding.buttonSet.setOnClickListener {
            val value = binding.lastChangelogEdittext.text.toString().toLong()
            viewModel.updateChangelogVersion(value)
        }

        binding.buttonClear.setOnClickListener {
            viewModel.clearChangelogVersion()
        }

        binding.buttonReset.setOnClickListener {
            viewModel.resetChangelogVersion()
        }

        binding.switchContactJournalOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setContactJournalOnboardingDone(value)
        }

        binding.switchDeltaOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setDeltaOnboardingDone(value)
        }

        binding.switchAttendeeOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setAttendeeOnboardingDone(value)
        }

        binding.switchVaccinationOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setVaccinationRegistrationOnboardingDone(value)
        }

        binding.switchNotificationsDeltaOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setNotificationsOnboardingDone(value)
        }

        binding.switchAnalyticsDeltaOnboarding.setOnCheckedChangeListener { _, value ->
            viewModel.setAnalyticsOnboardingDone(value)
        }
    }

    companion object {
        val MENU_ITEM = TestMenuItem(
            title = "Onboarding options",
            description = "Set delta onboarding or new release screen",
            targetId = R.id.test_deltaonboarding_fragment
        )
    }
}
