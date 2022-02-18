package de.rki.coronawarnapp.ccl.dccwalletinfo.update

import de.rki.coronawarnapp.ccl.dccwalletinfo.DccWalletInfoCleaner
import de.rki.coronawarnapp.ccl.dccwalletinfo.calculation.DccWalletInfoCalculationManager
import de.rki.coronawarnapp.ccl.dccwalletinfo.update.DccWalletInfoUpdateTask.DccWalletInfoUpdateTriggerType.TriggeredAfterConfigUpdate
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificates
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesProvider
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.task.TaskController
import de.rki.coronawarnapp.task.common.DefaultTaskRequest
import de.rki.coronawarnapp.util.coroutine.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UNCHECKED_CAST")
@Singleton
class DccWalletInfoUpdateTrigger @Inject constructor(
    personCertificatesProvider: PersonCertificatesProvider,
    private val taskController: TaskController,
    @AppScope val appScope: CoroutineScope,
    private val dccWalletInfoCalculationManager: DccWalletInfoCalculationManager,
    private val dccWalletInfoCleaner: DccWalletInfoCleaner
) {

    init {
        appScope.launch {
            personCertificatesProvider.personCertificates
                .distinctUntilChanged { old, new ->
                    old.qrcodeHashSet() == new.qrcodeHashSet()
                }
                .collectLatest {
                    dccWalletInfoCalculationManager.triggerCalculationAfterCertificateChange()
                    dccWalletInfoCleaner.clean()
                }
        }
    }

    private fun Set<PersonCertificates>.qrcodeHashSet() = flatMap {
        it.certificates.map { cert -> cert.qrCodeHash }
    }.sorted().toSet()

    fun triggerDccWalletInfoUpdateAfterConfigUpdate(configurationChanged: Boolean = false) {
        Timber.tag(TAG).d("triggerDccWalletInfoUpdateAfterConfigUpdate()")
        taskController.submit(
            DefaultTaskRequest(
                type = DccWalletInfoUpdateTask::class,
                arguments = DccWalletInfoUpdateTask.Arguments(
                    TriggeredAfterConfigUpdate(
                        configurationChanged
                    )
                ),
                originTag = TAG
            )
        )
    }

    companion object {
        private val TAG = tag<DccWalletInfoUpdateTrigger>()
    }
}
