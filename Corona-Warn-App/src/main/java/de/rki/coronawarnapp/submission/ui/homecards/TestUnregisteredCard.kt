package de.rki.coronawarnapp.submission.ui.homecards

import android.view.ViewGroup
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.coronatest.type.CommonSubmissionStates
import de.rki.coronawarnapp.databinding.HomeSubmissionStatusCardUnregisteredBinding
import de.rki.coronawarnapp.submission.ui.homecards.TestUnregisteredCard.Item
import de.rki.coronawarnapp.ui.main.home.HomeAdapter
import de.rki.coronawarnapp.util.lists.diffutil.HasPayloadDiffer

class TestUnregisteredCard(
    parent: ViewGroup
) : HomeAdapter.HomeItemVH<Item, HomeSubmissionStatusCardUnregisteredBinding>(
    R.layout.home_card_container_layout,
    parent
) {

    override val viewBinding = lazy {
        HomeSubmissionStatusCardUnregisteredBinding.inflate(
            layoutInflater,
            itemView.findViewById(R.id.card_container),
            true
        )
    }

    override val onBindData: HomeSubmissionStatusCardUnregisteredBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, payloads ->
        val curItem = payloads.filterIsInstance<Item>().lastOrNull() ?: item

        itemView.setOnClickListener { curItem.onClickAction(item) }
        nextStepsAction.setOnClickListener { curItem.onClickAction(item) }
    }

    data class Item(
        val state: CommonSubmissionStates.TestUnregistered,
        val onClickAction: (Item) -> Unit
    ) : TestResultItem, HasPayloadDiffer
}
