package de.rki.coronawarnapp.covidcertificate.vaccination.core.repository

import de.rki.coronawarnapp.bugreporting.reportProblem
import de.rki.coronawarnapp.covidcertificate.signature.core.DscRepository
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.storage.BoosterData
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.storage.BoosterStorage
import de.rki.coronawarnapp.util.TimeStamper
import de.rki.coronawarnapp.util.coroutine.AppScope
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.flow.HotDataFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.plus
import org.joda.time.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoosterRepository @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val timeStamper: TimeStamper,
    private val storage: BoosterStorage,
    @AppScope private val appScope: CoroutineScope,
    dscRepository: DscRepository
) {
    private val internalData: HotDataFlow<Set<BoosterData>> = HotDataFlow(
        loggingTag = TAG,
        scope = appScope + dispatcherProvider.Default,
        sharingBehavior = SharingStarted.Lazily
    ) {
        storage.load().also { Timber.tag(TAG).v("Restored booster data, %d items", it.size) }
    }

    init {
        internalData.data
            .onStart { Timber.tag(TAG).d("Observing BoosterData.") }
            .onEach { boosterData ->
                Timber.tag(TAG).v("Booster data changed, %d items", boosterData.size)
                storage.save(boosterData)
            }
            .catch {
                it.reportProblem(TAG, "Failed to snapshot booster data to storage.")
                throw it
            }
            .launchIn(appScope + dispatcherProvider.IO)
    }

    val boosterDataSet: Flow<Set<BoosterData>> = combine(
        internalData.data,
        dscRepository.dscData // Should it be the case here?
    ) { set, _ ->
        set
    }

    suspend fun getBoosterDataByPersonIndetifierCode(personIdentifierCode: String): BoosterData? {
        Timber.tag(TAG).d("getBoosterDataByPersonIndetifierCode(personIdentifierCode=%s)", personIdentifierCode)
        return internalData.data.first().find { it.personIdentifierCode == personIdentifierCode }
    }

    suspend fun acknowledgeBoosterRule(personIdentifierCode: String, boosterIdentifier: String) {
        Timber.tag(TAG).d("acknowledgeBoosterRule(personIdentifierCode=%s)", personIdentifierCode)

        internalData.updateBlocking {
            val boosterData = singleOrNull { it.personIdentifierCode == personIdentifierCode }

            if (boosterData != null) {
                Timber.tag(TAG).d("acknowledgeBoosterRule booster data found for person %s", personIdentifierCode)
                this.minus(boosterData)
            } else {
                Timber.tag(TAG).d("acknowledgeBoosterRule no booster data found for person %s", personIdentifierCode)
            }

            this.plus(
                BoosterData(
                    personIdentifierCode = personIdentifierCode,
                    lastSeenBoosterRuleIdentifier = boosterIdentifier,
                    lastBoosterNotifiedAt = timeStamper.nowUTC
                )
            )
        }
    }

    suspend fun clearBoosterData(personIdentifierCode: String) {
        Timber.tag(TAG).d("clearBoosterData(personIdentifierCode=%s)", personIdentifierCode)
        internalData.updateBlocking {
            val boosterData = singleOrNull { it.personIdentifierCode == personIdentifierCode }

            if (boosterData == null) {
                Timber.tag(TAG).w("clearBoosterData couldn't find booster data for %s", personIdentifierCode)
                return@updateBlocking this
            }

            val updatedBoosterData = boosterData.copy(
                lastSeenBoosterRuleIdentifier = null,
                lastBoosterNotifiedAt = null
            )

            Timber.tag(TAG).d("clearBoosterData updated booster data =%s", updatedBoosterData)

            this.minus(boosterData).plus(updatedBoosterData)

        }
    }

    suspend fun updateBoosterDataNotifiedAt(
        personIdentifierCode: String,
        time: Instant
    ) {
        Timber.tag(TAG).d("updateBoosterNotifiedAt(personIdentifier=%s, time=%s)", personIdentifierCode, time)
        internalData.updateBlocking {
            val boosterData = singleOrNull { it.personIdentifierCode == personIdentifierCode }

            if (boosterData == null) {
                Timber.tag(TAG).w("updateBoosterNotifiedAt couldn't find booster data for %s", personIdentifierCode)
                return@updateBlocking this
            }

            val updatedBoosterData = boosterData.copy(
                lastBoosterNotifiedAt = time
            )

            Timber.tag(TAG).d("updateBoosterNotifiedAt updated booster data =%s", updatedBoosterData)

            this.minus(boosterData).plus(updatedBoosterData)
        }
    }

    companion object {
        private const val TAG = "BoosterRepository"
    }
}
