package de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.storage

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.rki.coronawarnapp.util.di.AppContext
import de.rki.coronawarnapp.util.serialization.BaseGson
import de.rki.coronawarnapp.util.serialization.fromJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoosterStorage @Inject constructor(
    @AppContext val context: Context,
    @BaseGson val baseGson: Gson
) {
    private val mutex = Mutex()
    private val prefs by lazy {
        context.getSharedPreferences("booster_localdata", Context.MODE_PRIVATE)
    }

    private val gson by lazy {
        baseGson.newBuilder().create()
    }

    suspend fun load(): Set<BoosterData> = mutex.withLock {
        Timber.tag(TAG).d("load()")
        val boosterDataSet = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(PKEY_PERSON_BOOSTER_PREFIX)) {
                return@mapNotNull null
            }
            value as String
            gson.fromJson<BoosterData>(value).also { boosterData ->
                Timber.tag(TAG).v("Booster data loaded: %s", boosterData)
                requireNotNull(boosterData.lastSeenBoosterRuleIdentifier)
            }
        }
        return boosterDataSet.toSet()
    }

    suspend fun save(boosterDataSet: Set<BoosterData>) = mutex.withLock {
        Timber.tag(TAG).d("save(%s)", boosterDataSet)

        prefs.edit(commit = true) {
            prefs.all.keys.filter { it.startsWith(PKEY_PERSON_BOOSTER_PREFIX) }.forEach {
                Timber.tag(TAG).v("Removing data for %s", it)
                remove(it)
            }
            boosterDataSet.forEach {
                if(!it.personIdentifierCode.isNullOrBlank()) {
                    val raw = gson.toJson(it)
                    val identifier = it.personIdentifierCode
                    Timber.tag(TAG).v("Storing booster data %s -> %s", identifier, raw)
                    putString("$PKEY_PERSON_BOOSTER_PREFIX${identifier}", raw)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BoosterStorage"
        private const val PKEY_PERSON_BOOSTER_PREFIX = "booster.person."
    }
}
