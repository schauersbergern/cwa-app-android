package de.rki.coronawarnapp.covidcertificate.vaccination.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.rki.coronawarnapp.ccl.dccwalletinfo.update.DccWalletInfoUpdateTrigger
import de.rki.coronawarnapp.covidcertificate.common.repository.VaccinationCertificateContainerId
import de.rki.coronawarnapp.covidcertificate.pdf.ui.canBeExported
import de.rki.coronawarnapp.covidcertificate.vaccination.core.VaccinationCertificate
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.VaccinationRepository
import de.rki.coronawarnapp.covidcertificate.validation.core.DccValidationRepository
import de.rki.coronawarnapp.util.coroutine.AppScope
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.qrcode.coil.CoilQrCode
import de.rki.coronawarnapp.util.ui.SingleLiveEvent
import de.rki.coronawarnapp.util.viewmodel.CWAViewModel
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import timber.log.Timber

class VaccinationDetailsViewModel @AssistedInject constructor(
    @Assisted private val containerId: VaccinationCertificateContainerId,
    @Assisted private val fromScanner: Boolean,
    private val vaccinationRepository: VaccinationRepository,
    private val dccValidationRepository: DccValidationRepository,
    private val dccWalletInfoUpdateTrigger: DccWalletInfoUpdateTrigger,
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : CWAViewModel(dispatcherProvider) {

    private var qrCode: CoilQrCode? = null

    val vaccinationCertificate: LiveData<VaccinationCertificate?> = vaccinationRepository.certificates.map { certificates ->
        certificates.find { it.containerId == containerId }?.vaccinationCertificate?.also {
            qrCode = it.qrCodeToDisplay
        }
    }.asLiveData(dispatcherProvider.Default)

    val errors = SingleLiveEvent<Throwable>()
    val events = SingleLiveEvent<VaccinationDetailsNavigation>()

    val exportError = SingleLiveEvent<Unit>()

    fun onClose() = events.postValue(VaccinationDetailsNavigation.Back)

    fun openFullScreen() = qrCode?.let { events.postValue(VaccinationDetailsNavigation.FullQrCode(it)) }

    fun recycleVaccinationCertificateConfirmed() = launch(scope = appScope) {
        Timber.d("Recycling Vaccination Certificate=$containerId")
        vaccinationRepository.recycleCertificateNew(containerId)
        dccWalletInfoUpdateTrigger.triggerDccWalletInfoUpdateAfterCertificateChange()
        events.postValue(VaccinationDetailsNavigation.Back)
    }

    fun startValidationRulesDownload() = launch {
        try {
            dccValidationRepository.refresh()
            events.postValue(VaccinationDetailsNavigation.ValidationStart(containerId))
        } catch (e: Exception) {
            Timber.d(e, "validation rule download failed for covidCertificate=%s", containerId)
            errors.postValue(e)
        }
    }

    fun refreshCertState() = launch(scope = appScope) {
        Timber.v("refreshCertState()")
        vaccinationRepository.acknowledgeStateNew(containerId)
        if (!fromScanner) vaccinationRepository.markAsSeenByUserNew(containerId)
    }

    fun onExport() {
        if (vaccinationCertificate.value?.canBeExported() == false) {
            exportError.postValue(null)
        } else {
            events.postValue(VaccinationDetailsNavigation.Export(containerId))
        }
    }

    fun onCovPassInfoAction() {
        events.postValue(VaccinationDetailsNavigation.OpenCovPassInfo)
    }

    @AssistedFactory
    interface Factory : CWAViewModelFactory<VaccinationDetailsViewModel> {
        fun create(
            containerId: VaccinationCertificateContainerId,
            fromScanner: Boolean,
        ): VaccinationDetailsViewModel
    }
}

data class VaccinationDetails(
    val certificate: VaccinationCertificate?,
    val isImmune: Boolean = false
)
