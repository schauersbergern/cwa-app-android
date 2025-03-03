package de.rki.coronawarnapp.test.risklevel.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.rki.coronawarnapp.appconfig.AppConfigProvider
import de.rki.coronawarnapp.diagnosiskeys.download.DownloadDiagnosisKeysSettings
import de.rki.coronawarnapp.diagnosiskeys.download.DownloadDiagnosisKeysTask
import de.rki.coronawarnapp.diagnosiskeys.storage.KeyCacheRepository
import de.rki.coronawarnapp.nearby.modules.detectiontracker.ExposureDetectionTracker
import de.rki.coronawarnapp.nearby.modules.detectiontracker.latestSubmission
import de.rki.coronawarnapp.risk.EwRiskLevelTask
import de.rki.coronawarnapp.risk.RiskState
import de.rki.coronawarnapp.risk.result.EwAggregatedRiskResult
import de.rki.coronawarnapp.risk.storage.RiskLevelStorage
import de.rki.coronawarnapp.storage.TestSettings
import de.rki.coronawarnapp.task.TaskController
import de.rki.coronawarnapp.task.common.DefaultTaskRequest
import de.rki.coronawarnapp.task.submitBlocking
import de.rki.coronawarnapp.test.risklevel.entities.toExposureWindowJson
import de.rki.coronawarnapp.util.TimeStamper
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.di.AppContext
import de.rki.coronawarnapp.util.ui.SingleLiveEvent
import de.rki.coronawarnapp.util.viewmodel.CWAViewModel
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.joda.time.Instant
import org.joda.time.format.DateTimeFormat
import timber.log.Timber
import java.io.File

class TestRiskLevelCalculationFragmentCWAViewModel @AssistedInject constructor(
    @Assisted private val handle: SavedStateHandle,
    @Assisted private val exampleArg: String?,
    @AppContext private val context: Context, // App context
    private val dispatcherProvider: DispatcherProvider,
    private val taskController: TaskController,
    private val keyCacheRepository: KeyCacheRepository,
    appConfigProvider: AppConfigProvider,
    private val riskLevelStorage: RiskLevelStorage,
    private val testSettings: TestSettings,
    private val timeStamper: TimeStamper,
    private val exposureDetectionTracker: ExposureDetectionTracker,
    private val downloadDiagnosisKeysSettings: DownloadDiagnosisKeysSettings
) : CWAViewModel(
    dispatcherProvider = dispatcherProvider
) {

    // Use unique instance for pretty output
    private val gson: Gson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }

    val fakeWindowsState = testSettings.fakeExposureWindows.flow.asLiveData()

    init {
        Timber.d("CWAViewModel: %s", this)
        Timber.d("SavedStateHandle: %s", handle)
        Timber.d("Example arg: %s", exampleArg)
    }

    val dataResetEvent = SingleLiveEvent<String>()
    val shareFileEvent = SingleLiveEvent<File>()

    private val lastRiskResult = riskLevelStorage.allEwRiskLevelResultsWithExposureWindows.map { results ->
        results.maxByOrNull { it.calculatedAt }
    }

    val exposureWindowCount = lastRiskResult
        .map { it?.exposureWindows?.size ?: 0 }
        .asLiveData()

    val aggregatedRiskResult = lastRiskResult
        .map {
            if (it == null) return@map "No results yet."

            if (it.wasSuccessfullyCalculated) {
                // wasSuccessfullyCalculated check for aggregatedRiskResult != null
                it.ewAggregatedRiskResult!!.toReadableString()
            } else {
                var notAvailable = "Aggregated risk result is not available"
                it.failureReason?.let { failureReason -> notAvailable += " because ${failureReason.failureCode}" }
                notAvailable
            }
        }
        .asLiveData()

    private fun EwAggregatedRiskResult.toReadableString(): String = StringBuilder()
        .appendLine("Total RiskLevel: $totalRiskLevel")
        .appendLine("Total Minimum Distinct Encounters With High Risk: $totalMinimumDistinctEncountersWithHighRisk")
        .appendLine("Total Minimum Distinct Encounters With Low Risk: $totalMinimumDistinctEncountersWithLowRisk")
        .appendLine("Most Recent Date With High Risk: $mostRecentDateWithHighRisk")
        .appendLine("Most Recent Date With Low Risk: $mostRecentDateWithLowRisk")
        .appendLine("Number of Days With High Risk: $numberOfDaysWithHighRisk")
        .appendLine("Number of Days With Low Risk: $numberOfDaysWithLowRisk")
        .toString()

    @Suppress("DEPRECATION")
    val backendParameters = appConfigProvider
        .currentConfig
        .map { it.rawConfig.riskCalculationParameters.toString() }
        .asLiveData()

    val additionalRiskCalcInfo = combine(
        riskLevelStorage.latestAndLastSuccessfulCombinedEwPtRiskLevelResult,
        exposureDetectionTracker.latestSubmission()
    ) { riskLevelResults, latestSubmission ->

        val latestCalc = riskLevelResults.lastCalculated.ewRiskLevelResult
        val latestSuccessfulCalc = riskLevelResults.lastSuccessfullyCalculated.ewRiskLevelResult

        createAdditionalRiskCalcInfo(
            latestCalc.calculatedAt,
            riskLevel = latestCalc.riskState,
            riskLevelLastSuccessfulCalculated = latestSuccessfulCalc.riskState,
            matchedKeyCount = latestCalc.matchedKeyCount,
            noOfDaysWithExposures = if (latestCalc.riskState == RiskState.INCREASED_RISK)
                latestCalc.ewAggregatedRiskResult?.numberOfDaysWithHighRisk ?: 0
            else latestCalc.ewAggregatedRiskResult?.numberOfDaysWithLowRisk ?: 0,
            lastKeySubmission = latestSubmission?.startedAt
        )
    }.asLiveData()

    private fun createAdditionalRiskCalcInfo(
        lastTimeRiskLevelCalculation: Instant,
        riskLevel: RiskState,
        riskLevelLastSuccessfulCalculated: RiskState,
        matchedKeyCount: Int,
        noOfDaysWithExposures: Int,
        lastKeySubmission: Instant?
    ): String = StringBuilder()
        .appendLine("Risk Level: $riskLevel")
        .appendLine("Last successful Risk Level: $riskLevelLastSuccessfulCalculated")
        .appendLine("Matched key count: $matchedKeyCount")
        .appendLine("Days with exposures: $noOfDaysWithExposures days")
        .appendLine("Last key submission: $lastKeySubmission")
        .appendLine("Last time risk level calculation $lastTimeRiskLevelCalculation")
        .toString()

    fun retrieveDiagnosisKeys() {
        Timber.d("Starting download diagnosis keys task")
        launch {
            taskController.submitBlocking(
                DefaultTaskRequest(
                    DownloadDiagnosisKeysTask::class,
                    DownloadDiagnosisKeysTask.Arguments(),
                    originTag = "TestRiskLevelCalculationFragmentCWAViewModel.retrieveDiagnosisKeys()"
                )
            )
        }
    }

    fun calculateRiskLevel() {
        Timber.d("Starting calculate risk task")
        taskController.submit(
            DefaultTaskRequest(
                EwRiskLevelTask::class,
                originTag = "TestRiskLevelCalculationFragmentCWAViewModel.calculateRiskLevel()"
            )
        )
    }

    fun resetRiskLevel() {
        Timber.d("Resetting risk level")
        launch {
            riskLevelStorage.clear()
            dataResetEvent.postValue("Risk level calculation related data reset.")
        }
    }

    fun shareExposureWindows() {
        Timber.d("Creating text file for Exposure Windows")
        launch {
            val exposureWindows = lastRiskResult.firstOrNull()?.exposureWindows?.map { it.toExposureWindowJson() }
            val fileNameCompatibleTimestamp = timeStamper.nowUTC.toString(
                DateTimeFormat.forPattern("yyyy-MM-DD-HH-mm-ss")
            )

            val path = File(context.cacheDir, "share/")
            path.mkdirs()

            val file = File(path, "exposureWindows-$fileNameCompatibleTimestamp.json")
            file.bufferedWriter()
                .use { writer ->
                    if (exposureWindows.isNullOrEmpty()) {
                        writer.appendLine("Exposure windows list was empty")
                    } else {
                        writer.appendLine(gson.toJson(exposureWindows))
                    }
                }
            shareFileEvent.postValue(file)
        }
    }

    fun clearKeyCache() {
        Timber.d("Clearing key cache")
        launch {
            keyCacheRepository.clear()
            downloadDiagnosisKeysSettings.clear()
            exposureDetectionTracker.clear()

            dataResetEvent.postValue("Download & Submission related data reset.")
        }
    }

    fun selectFakeExposureWindowMode(newMode: TestSettings.FakeExposureWindowTypes) {
        testSettings.fakeExposureWindows.update { newMode }
    }

    @AssistedFactory
    interface Factory : CWAViewModelFactory<TestRiskLevelCalculationFragmentCWAViewModel> {
        fun create(
            handle: SavedStateHandle,
            exampleArg: String?
        ): TestRiskLevelCalculationFragmentCWAViewModel
    }
}
