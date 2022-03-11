package de.rki.coronawarnapp.test.keydownload.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.rki.coronawarnapp.diagnosiskeys.download.DownloadDiagnosisKeysTask
import de.rki.coronawarnapp.diagnosiskeys.download.KeyPackageSyncTool
import de.rki.coronawarnapp.diagnosiskeys.storage.KeyCacheRepository
import de.rki.coronawarnapp.storage.TestSettings
import de.rki.coronawarnapp.task.TaskController
import de.rki.coronawarnapp.task.common.DefaultTaskRequest
import de.rki.coronawarnapp.task.submitBlocking
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.network.NetworkStateProvider
import de.rki.coronawarnapp.util.ui.SingleLiveEvent
import de.rki.coronawarnapp.util.viewmodel.CWAViewModel
import de.rki.coronawarnapp.util.viewmodel.SimpleCWAViewModelFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import timber.log.Timber

class KeyDownloadTestFragmentViewModel @AssistedInject constructor(
    dispatcherProvider: DispatcherProvider,
    networkStateProvider: NetworkStateProvider,
    private val testSettings: TestSettings,
    private val keyPackageSyncTool: KeyPackageSyncTool,
    private val keyCacheRepository: KeyCacheRepository,
    private val taskController: TaskController
) : CWAViewModel(dispatcherProvider = dispatcherProvider) {

    val currentCache = liveData {
        emitSource(
            keyCacheRepository.allCachedKeys().sample(250)
                .map { items ->
                    items
                        .sortedWith(compareBy({ it.info.day }, { it.info.hour }))
                        .reversed()
                        .map { CachedKeyListItem(it.info, it.path.length()) }
                }.asLiveData2()
        )
    }

    val isMeteredConnection = networkStateProvider.networkState
        .map { it.isMeteredConnection }
        .asLiveData()

    val fakeMeteredConnection = testSettings.fakeMeteredConnection.flow.asLiveData()

    val isSyncRunning = MutableLiveData(false)
    val errorEvent = SingleLiveEvent<Throwable>()

    fun toggleAllowMeteredConnections() {
        testSettings.fakeMeteredConnection.update { !it }
    }

    fun download() = launchWithSyncProgress {
        keyPackageSyncTool.syncKeyFiles()
    }

    fun clearDownloads() = launchWithSyncProgress { keyCacheRepository.clear() }

    private fun launchWithSyncProgress(action: suspend () -> Unit) {
        isSyncRunning.postValue(true)
        launch {
            try {
                action()
            } catch (e: Exception) {
                Timber.e(e, "Call failed.")
                errorEvent.postValue(e)
            } finally {
                isSyncRunning.postValue(false)
            }
        }
    }

    fun deleteKeyFile(it: CachedKeyListItem) = launchWithSyncProgress {
        keyCacheRepository.delete(listOf(it.info))
    }

    fun runDownloadTask() = launch {
        val taskState = taskController.submitBlocking(
            DefaultTaskRequest(
                DownloadDiagnosisKeysTask::class,
                DownloadDiagnosisKeysTask.Arguments(),
                originTag = "DiagnosisKeyRetrievalWorker"
            )
        )

        taskState.error?.let { errorEvent.postValue(it) }
    }

    @AssistedFactory
    interface Factory : SimpleCWAViewModelFactory<KeyDownloadTestFragmentViewModel>
}
