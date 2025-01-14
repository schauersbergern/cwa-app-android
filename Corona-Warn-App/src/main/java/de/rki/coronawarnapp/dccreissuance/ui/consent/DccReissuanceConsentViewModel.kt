package de.rki.coronawarnapp.dccreissuance.ui.consent

import androidx.lifecycle.LiveData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.rki.coronawarnapp.ccl.dccwalletinfo.model.CertificateReissuance
import de.rki.coronawarnapp.ccl.ui.text.CclTextFormatter
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccQrCodeExtractor
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccV1
import de.rki.coronawarnapp.covidcertificate.common.certificate.DccV1Parser
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesProvider
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesSettings
import de.rki.coronawarnapp.dccreissuance.core.reissuer.DccReissuer
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.ui.SingleLiveEvent
import de.rki.coronawarnapp.util.viewmodel.CWAViewModel
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactory
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DccReissuanceConsentViewModel @AssistedInject constructor(
    dispatcherProvider: DispatcherProvider,
    personCertificatesProvider: PersonCertificatesProvider,
    @Assisted private val personIdentifierCode: String,
    private val dccReissuer: DccReissuer,
    private val format: CclTextFormatter,
    private val dccQrCodeExtractor: DccQrCodeExtractor,
    private val personCertificatesSettings: PersonCertificatesSettings,
) : CWAViewModel(dispatcherProvider) {

    internal val event = SingleLiveEvent<Event>()

    private val reissuanceData = personCertificatesProvider.findPersonByIdentifierCode(personIdentifierCode)
        .map { person ->
            person?.personIdentifier?.let { personCertificatesSettings.dismissReissuanceBadge(it) }
            person?.dccWalletInfo?.certificateReissuance
        }
    internal val stateLiveData: LiveData<State> = reissuanceData.map {
        // Make sure DccReissuance exist, otherwise screen is dismissed
        it!!.toState()
    }.catch {
        Timber.tag(TAG).d(it, "dccReissuanceData failed")
        event.postValue(Back) // Fallback: Could happen when DccReissuance is gone while user is here for a long time
    }.asLiveData2()

    internal fun startReissuance() = launch {
        runCatching {
            event.postValue(ReissuanceInProgress)
            dccReissuer.startReissuance(reissuanceData.first()!!)
        }.onFailure { e ->
            Timber.d(e, "startReissuance() failed")
            event.postValue(ReissuanceError(e))
        }.onSuccess {
            Timber.d("startReissuance() succeeded")
            event.postValue(ReissuanceSuccess)
        }
    }

    fun navigateBack() = event.postValue(Back)

    private suspend fun CertificateReissuance?.toState(): State {
        var certificate: DccV1.MetaData? = null
        this?.certificateToReissue?.certificateRef?.barcodeData?.let {
            certificate = dccQrCodeExtractor.extract(
                it,
                DccV1Parser.Mode.CERT_SINGLE_STRICT
            ).data.certificate
        }
        return State(
            certificate = certificate,
            divisionVisible = this?.reissuanceDivision?.visible ?: false,
            title = format(this?.reissuanceDivision?.titleText),
            subtitle = format(this?.reissuanceDivision?.subtitleText),
            content = format(this?.reissuanceDivision?.longText),
            url = format(this?.reissuanceDivision?.faqAnchor)
        )
    }

    fun openPrivacyScreen() = event.postValue(OpenPrivacyScreen)

    internal data class State(
        val certificate: DccV1.MetaData?,
        val divisionVisible: Boolean = false,
        val title: String?,
        val subtitle: String?,
        val content: String?,
        val url: String?,
    )

    internal sealed class Event
    internal object ReissuanceInProgress : Event()
    internal object ReissuanceSuccess : Event()
    internal object Back : Event()
    internal object OpenPrivacyScreen : Event()
    internal data class ReissuanceError(val error: Throwable) : Event()

    @AssistedFactory
    interface Factory : CWAViewModelFactory<DccReissuanceConsentViewModel> {
        fun create(
            personIdentifierCode: String
        ): DccReissuanceConsentViewModel
    }

    companion object {
        private val TAG = tag<DccReissuanceConsentViewModel>()
    }
}
