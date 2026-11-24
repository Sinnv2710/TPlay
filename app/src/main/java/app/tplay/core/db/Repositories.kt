package app.tplay.core.db

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.tplay.domain.Job
import app.tplay.domain.JobRepository
import app.tplay.domain.JobStatus
import app.tplay.domain.SettingsRepository
import app.tplay.domain.Title
import app.tplay.work.PipelineStore
import app.tplay.work.PipelineWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PipelineStoreImpl(private val db: TPlayDatabase, private val store: JobStore) : PipelineStore {
    override suspend fun job(id: Long): Job? = store.get(id)

    override suspend fun update(job: Job) = store.update(job)

    /** Adding the same movie again (same name and year) replaces the old entry and keeps its watch progress. */
    override suspend fun addTitle(title: Title) {
        val previous = db.titles().sameMovie(title.name, title.year).firstOrNull()
        if (previous != null) {
            db.titles().delete(previous.id)
            db.progress().get(previous.id)?.let { db.progress().put(it.copy(titleId = title.id)) }
            db.progress().delete(previous.id)
        }
        db.titles().put(
            TitleEntity(
                id = title.id,
                name = title.name,
                year = title.year,
                posterUrl = title.posterUrl,
                folderPath = title.folderPath,
                sizeBytes = title.sizeBytes,
                createdAt = title.createdAt,
                rdTorrentId = title.rdTorrentId,
                rdLink = title.rdLink,
                filesDeleted = title.filesDeleted,
            ),
        )
    }
}

class JobRepositoryImpl(
    private val context: Context,
    private val store: JobStore,
    private val settings: SettingsRepository,
) : JobRepository {
    override fun jobs(): Flow<List<Job>> = store.observe()

    override suspend fun enqueue(
        magnet: String,
        subtitleUrl: String?,
        parentFolder: String,
        playWhenReady: Boolean,
    ): Long {
        val job = Job(
            id = 0,
            titleId = null,
            magnet = magnet.trim(),
            subtitleUrl = subtitleUrl,
            parentFolder = parentFolder,
            status = JobStatus.QUEUED,
            progress = 0,
            speedBytesPerSec = 0,
            step = "Queued",
            error = null,
            createdAt = System.currentTimeMillis(),
        )
        val id = store.insert(job)
        val network = if (settings.wifiOnly().first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(PipelineWorker.KEY_JOB_ID to id, PipelineWorker.KEY_STREAM to playWhenReady))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("pipeline")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("job-$id", ExistingWorkPolicy.KEEP, request)
        return id
    }

    override suspend fun downloadDirect(jobId: Long) {
        val job = store.get(jobId) ?: return
        store.update(job.copy(status = JobStatus.QUEUED, progress = 0, speedBytesPerSec = 0, step = "Queued", error = null))
        val network = if (settings.wifiOnly().first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(PipelineWorker.KEY_JOB_ID to jobId, PipelineWorker.KEY_DIRECT to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .addTag("pipeline")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("job-$jobId-direct", ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun cancel(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("job-$jobId")
        WorkManager.getInstance(context).cancelUniqueWork("job-$jobId-direct")
        store.get(jobId)?.let {
            store.update(it.copy(status = JobStatus.CANCELLED, step = "Cancelled", speedBytesPerSec = 0))
        }
    }
}
