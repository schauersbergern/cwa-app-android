package de.rki.coronawarnapp.ccl.dccwalletinfo.update

import de.rki.coronawarnapp.ccl.dccwalletinfo.update.DccWalletInfoUpdateTask.DccWalletInfoUpdateTriggerType.TriggeredAfterCertificateChange
import de.rki.coronawarnapp.ccl.dccwalletinfo.update.DccWalletInfoUpdateTask.DccWalletInfoUpdateTriggerType.TriggeredAfterConfigUpdate
import de.rki.coronawarnapp.covidcertificate.person.core.PersonCertificatesProvider
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.task.TaskController
import de.rki.coronawarnapp.task.common.DefaultTaskRequest
import de.rki.coronawarnapp.util.coroutine.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import timber.log.Timber
import javax.inject.Inject

class DccWalletInfoUpdateTrigger @Inject constructor(
    personCertificatesProvider: PersonCertificatesProvider,
    private val taskController: TaskController,
    @AppScope val appScope: CoroutineScope
) {

    init {
        personCertificatesProvider.personCertificates
            .zipWithNext { prevPersons, currentPersons ->
                val prevsCerts = prevPersons.flatMap { it.certificates.map { it.qrCodeHash } }.sorted().toSet()
                val currentCerts = currentPersons.flatMap { it.certificates.map { it.qrCodeHash } }.sorted().toSet()
                if (prevsCerts != currentCerts) {
                    triggerDccWalletInfoUpdateAfterCertificateChange()
                }
            }.launchIn(appScope)
    }

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

    fun triggerDccWalletInfoUpdateAfterCertificateChange() {
        Timber.tag(TAG).d("triggerDccWalletInfoUpdateAfterCertificateChange()")
        taskController.submit(
            DefaultTaskRequest(
                type = DccWalletInfoUpdateTask::class,
                arguments = DccWalletInfoUpdateTask.Arguments(TriggeredAfterCertificateChange),
                originTag = TAG
            )
        )
    }

    companion object {
        private val TAG = tag<DccWalletInfoUpdateTrigger>()
    }
}

fun <T, R> Flow<T>.zipWithNext(transform: (T, T) -> R): Flow<R> = flow {
    var prev: Any? = UNDEFINED
    collectLatest { value ->
        if (prev !== UNDEFINED) emit(transform(prev as T, value))
        prev = value
    }
}

private object UNDEFINED
