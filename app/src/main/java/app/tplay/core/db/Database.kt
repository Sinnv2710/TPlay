package app.tplay.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import app.tplay.domain.HistoryDay
import app.tplay.domain.Job
import app.tplay.domain.JobStatus
import app.tplay.domain.LibraryRepository
import app.tplay.domain.ProgressRepository
import app.tplay.domain.Title
import app.tplay.domain.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "titles")
data class TitleEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val folderPath: String?,
    val sizeBytes: Long,
    val createdAt: Long,
    val rdTorrentId: String?,
    val rdLink: String?,
    val filesDeleted: Boolean,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long?,
    val magnet: String,
    val subtitleUrl: String?,
    val parentFolder: String,
    val status: String,
    val progress: Int,
    val speedBytesPerSec: Long,
    val step: String,
    val error: String?,
    val createdAt: Long,
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val titleId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
    val watchCount: Int,
)

@Entity(
    tableName = "history_events",
    indices = [Index(value = ["titleId", "epochDay"], unique = true)],
)
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val epochDay: Long,
)

@Dao
interface TitleDao {
    @Query("SELECT * FROM titles")
    fun all(): Flow<List<TitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TitleEntity)

    @Query("UPDATE titles SET folderPath = NULL, filesDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("SELECT * FROM titles WHERE name = :name AND (year IS :year)")
    suspend fun sameMovie(name: String, year: Int?): List<TitleEntity>

    @Query("UPDATE titles SET posterUrl = :url WHERE id = :id")
    suspend fun setPoster(id: Long, url: String)

    @Query("DELETE FROM titles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun get(id: Long): TitleEntity?

    @Query("SELECT * FROM titles WHERE createdAt <= :cutoff AND filesDeleted = 0")
    suspend fun cleanable(cutoff: Long): List<TitleEntity>
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun all(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: Long): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: JobEntity): Long
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM watch_progress")
    fun all(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE titleId = :id")
    suspend fun get(id: Long): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE titleId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun put(entity: HistoryEventEntity)

    @Query("SELECT * FROM history_events ORDER BY epochDay DESC")
    fun all(): Flow<List<HistoryEventEntity>>
}

@Database(
    entities = [TitleEntity::class, JobEntity::class, WatchProgressEntity::class, HistoryEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TPlayDatabase : RoomDatabase() {
    abstract fun titles(): TitleDao
    abstract fun jobs(): JobDao
    abstract fun progress(): ProgressDao
    abstract fun history(): HistoryDao
}

internal fun TitleEntity.toDomain() = Title(
    id, name, year, posterUrl, folderPath, sizeBytes, createdAt, rdTorrentId, rdLink, filesDeleted,
)

private fun WatchProgressEntity.toDomain() = WatchProgress(
    titleId, positionMs, durationMs, updatedAt, finished, watchCount,
)

internal fun JobEntity.toDomain() = Job(
    id, titleId, magnet, subtitleUrl, parentFolder, JobStatus.valueOf(status),
    progress, speedBytesPerSec, step, error, createdAt,
)

class LibraryRepositoryImpl(private val db: TPlayDatabase) : LibraryRepository {
    override fun titles() = db.titles().all().map { it.map(TitleEntity::toDomain) }

    override fun progress() = db.progress().all().map {
        it.associate { entity -> entity.titleId to entity.toDomain() }
    }

    override fun historyByDay() = db.history().all().map { events ->
        events.groupBy { it.epochDay }.map { (day, items) ->
            HistoryDay(day, items.map { it.titleId })
        }
    }

    override suspend fun addRemote(
        name: String,
        year: Int?,
        sizeBytes: Long,
        torrentId: String,
        link: String,
    ): Title {
        val existing = db.titles().sameMovie(name, year).firstOrNull()
        if (existing != null) return existing.toDomain()
        val entity = TitleEntity(
            id = System.currentTimeMillis(),
            name = name,
            year = year,
            posterUrl = null,
            folderPath = null,
            sizeBytes = 0L,
            createdAt = System.currentTimeMillis(),
            rdTorrentId = torrentId,
            rdLink = link,
            filesDeleted = true,
        )
        db.titles().put(entity)
        return entity.toDomain()
    }

    override suspend fun setPoster(titleId: Long, posterUrl: String) {
        db.titles().setPoster(titleId, posterUrl)
    }

    override suspend fun delete(titleId: Long, removeFromRd: Boolean) {
        val title = db.titles().get(titleId)
        title?.folderPath?.let { java.io.File(it).deleteRecursively() }
        db.titles().delete(titleId)
        db.progress().delete(titleId)
    }
}

class ProgressRepositoryImpl(private val db: TPlayDatabase) : ProgressRepository {
    override suspend fun save(titleId: Long, positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        val previous = db.progress().get(titleId)
        db.progress().put(
            WatchProgressEntity(
                titleId = titleId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = now,
                finished = durationMs > 0 && positionMs >= durationMs * 0.9,
                watchCount = (previous?.watchCount ?: 0) + 1,
            ),
        )
        db.history().put(HistoryEventEntity(titleId = titleId, epochDay = java.time.LocalDate.now().toEpochDay()))
    }

    override suspend fun get(titleId: Long) = db.progress().get(titleId)?.toDomain()
}

class JobStore(private val db: TPlayDatabase) {
    fun observe(): Flow<List<Job>> = db.jobs().all().map { it.map(JobEntity::toDomain) }

    suspend fun insert(job: Job): Long = db.jobs().put(job.toEntity())

    suspend fun update(job: Job) {
        db.jobs().put(job.toEntity())
    }

    suspend fun get(id: Long): Job? = db.jobs().get(id)?.toDomain()

    private fun Job.toEntity() = JobEntity(
        id, titleId, magnet, subtitleUrl, parentFolder, status.name, progress,
        speedBytesPerSec, step, error, createdAt,
    )
}

class TitleCleanerImpl(private val db: TPlayDatabase) : app.tplay.core.settings.TitleCleaner {
    override suspend fun clean(cutoffMillis: Long, keepHistory: Boolean): Int {
        val old = db.titles().cleanable(cutoffMillis)
        old.forEach { title ->
            title.folderPath?.let { java.io.File(it).deleteRecursively() }
            if (keepHistory) {
                db.titles().markDeleted(title.id)
            } else {
                db.titles().delete(title.id)
                db.progress().delete(title.id)
            }
        }
        return old.size
    }
}
