package de.rki.coronawarnapp.reyclebin.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.databinding.RecyclerBinOverviewFragmentBinding
import de.rki.coronawarnapp.reyclebin.ui.adapter.RecyclerBinAdapter
import de.rki.coronawarnapp.reyclebin.ui.dialog.RecycleBinDialogType
import de.rki.coronawarnapp.reyclebin.ui.dialog.show
import de.rki.coronawarnapp.util.di.AutoInject
import de.rki.coronawarnapp.util.list.setupSwipe
import de.rki.coronawarnapp.util.lists.diffutil.update
import de.rki.coronawarnapp.util.ui.doNavigate
import de.rki.coronawarnapp.util.ui.observe2
import de.rki.coronawarnapp.util.ui.popBackStack
import de.rki.coronawarnapp.util.ui.viewBinding
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactoryProvider
import de.rki.coronawarnapp.util.viewmodel.cwaViewModels
import javax.inject.Inject

class RecyclerBinOverviewFragment : Fragment(R.layout.recycler_bin_overview_fragment), AutoInject {

    @Inject lateinit var viewModelFactory: CWAViewModelFactoryProvider.Factory
    private val viewModel: RecyclerBinOverviewViewModel by cwaViewModels { viewModelFactory }
    private val binding: RecyclerBinOverviewFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerBinAdapter = RecyclerBinAdapter()

        with(binding) {
            toolbar.apply {
                setNavigationOnClickListener { popBackStack() }
                setOnMenuItemClickListener { onMenuItemClicked(it) }
            }

            recyclerBinList.apply {
                adapter = recyclerBinAdapter
                setupSwipe(context = requireContext())
            }
        }

        viewModel.listItems.observe2(this) {
            binding.emptyListInfoContainer.isVisible = it.isEmpty()
            recyclerBinAdapter.update(it)
        }

        viewModel.events.observe2(this) { handleRecyclerEvent(it) }
    }

    private fun handleRecyclerEvent(event: RecyclerBinEvent): Unit = when (event) {
        RecyclerBinEvent.ConfirmRemoveAll -> RecycleBinDialogType.RemoveAllItemsConfirmation.show(
            fragment = this,
            positiveButtonAction = { viewModel.onRemoveAllItemsConfirmation() }
        )

        is RecyclerBinEvent.RemoveCertificate -> viewModel.onRemoveCertificate(event.certificate)

        is RecyclerBinEvent.ConfirmRestoreCertificate -> RecycleBinDialogType.RestoreCertificateConfirmation.show(
            fragment = this,
            positiveButtonAction = { viewModel.onRestoreCertificateConfirmation(event.certificate) }
        )

        is RecyclerBinEvent.ConfirmRestoreTest -> RecycleBinDialogType.RestoreTestConfirmation.show(
            fragment = this,
            positiveButtonAction = { viewModel.onRestoreTestConfirmation(event.test) }
        )

        is RecyclerBinEvent.RemoveTest -> viewModel.onRemoveTest(event.test)
        is RecyclerBinEvent.RestoreDuplicateTest -> doNavigate(
            RecyclerBinOverviewFragmentDirections.actionRecyclerBinOverviewFragmentToSubmissionDeletionWarningFragment(
                event.restoreRecycledTestRequest
            )
        )
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_remove_all -> {
            viewModel.onRemoveAllItemsClicked()
            true
        }
        else -> onOptionsItemSelected(item)
    }
}
