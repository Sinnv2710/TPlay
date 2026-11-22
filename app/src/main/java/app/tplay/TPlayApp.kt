package app.tplay

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.tplay.domain.VaultRepository
import app.tplay.work.CleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@HiltAndroidApp
class TPlayApp : Application() {
    @Inject lateinit var vault: VaultRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val cleanup = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("cleanup", ExistingPeriodicWorkPolicy.KEEP, cleanup)
        if (BuildConfig.DEBUG) importDebugTokens()
    }

    /** Debug builds only: tokens pushed to files/import-tokens.json by the developer go into the vault. */
    private fun importDebugTokens() {
        val file = File(getExternalFilesDir(null), "import-tokens.json")
        if (!file.exists()) return
        scope.launch {
            try {
                Json.parseToJsonElement(file.readText()).jsonObject.forEach { (id, value) ->
                    vault.put(id, id, value.jsonPrimitive.content)
                }
                file.delete()
                Log.i("TPlay", "debug tokens imported")
            } catch (e: Exception) {
                Log.e("TPlay", "debug token import failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
}
