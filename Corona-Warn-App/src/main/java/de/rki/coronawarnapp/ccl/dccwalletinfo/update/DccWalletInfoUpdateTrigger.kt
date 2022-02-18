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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
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
        personCertificatesProvider.personCertificates
            .zipWithNext { prevPersons, currentPersons ->
                val prevsCerts = prevPersons.qrcodeHashSet()
                val currentCerts = currentPersons.qrcodeHashSet()
                if (prevsCerts != currentCerts) {
                    Timber.tag(TAG).d("prevsCerts=%s, currentCerts=%s", prevsCerts, currentCerts)
                    dccWalletInfoCalculationManager.triggerCalculationAfterCertificateChange()
                    dccWalletInfoCleaner.clean()
                }
            }
            .catch { e -> Timber.tag(TAG).e(e.localizedMessage ?: e.message ?: e.javaClass.canonicalName) }
            .launchIn(appScope)
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

@Suppress("UNCHECKED_CAST")
fun <T, R> Flow<T>.zipWithNext(transform: suspend (T, T) -> R): Flow<R> = flow {
    var prev: Any? = UNDEFINED
    collect { value ->
        if (prev !== UNDEFINED) emit(transform(prev as T, value))
        prev = value
    }
}

private object UNDEFINED
