package app.tplay.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tplay.domain.CleanupPreset
import app.tplay.domain.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CleanupEntryPoint {
    fun settings(): SettingsRepository
}

/** Daily job: deletes downloaded files older than the chosen cleanup preset. */
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = EntryPointAccessors
            .fromApplication(applicationContext, CleanupEntryPoint::class.java)
            .settings()
        val policy = settings.cleanupPolicy().first()
        val days = when (policy.preset) {
            CleanupPreset.NEVER -> return Result.success()
            CleanupPreset.ONE_WEEK -> 7
            CleanupPreset.ONE_MONTH -> 30
            CleanupPreset.CUSTOM -> policy.customDays
        }
        settings.cleanOlderThan(days)
        return Result.success()
    }
}
