package app.tplay.core.di

import android.content.Context
import androidx.room.Room
import app.tplay.core.db.JobRepositoryImpl
import app.tplay.core.db.JobStore
import app.tplay.core.db.LibraryRepositoryImpl
import app.tplay.core.db.PipelineStoreImpl
import app.tplay.core.db.ProgressRepositoryImpl
import app.tplay.core.db.TPlayDatabase
import app.tplay.core.db.TitleCleanerImpl
import app.tplay.core.settings.SettingsRepositoryImpl
import app.tplay.core.vault.VaultRepositoryImpl
import app.tplay.domain.JobRepository
import app.tplay.domain.LibraryRepository
import app.tplay.domain.ProgressRepository
import app.tplay.domain.SettingsRepository
import app.tplay.domain.TraktRepository
import app.tplay.domain.VaultRepository
import app.tplay.ui.fake.FakeTraktRepository
import app.tplay.work.PipelineStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TPlayDatabase =
        Room.databaseBuilder(context, TPlayDatabase::class.java, "tplay.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    @Singleton
    fun jobStore(db: TPlayDatabase) = JobStore(db)

    @Provides
    @Singleton
    fun library(db: TPlayDatabase): LibraryRepository = LibraryRepositoryImpl(db)

    @Provides
    @Singleton
    fun progress(db: TPlayDatabase): ProgressRepository = ProgressRepositoryImpl(db)

    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context, db: TPlayDatabase): SettingsRepository =
        SettingsRepositoryImpl(context, TitleCleanerImpl(db))

    @Provides
    @Singleton
    fun vault(@ApplicationContext context: Context): VaultRepository = VaultRepositoryImpl(context)

    @Provides
    @Singleton
    fun jobs(
        @ApplicationContext context: Context,
        store: JobStore,
        settings: SettingsRepository,
    ): JobRepository = JobRepositoryImpl(context, store, settings)

    @Provides
    @Singleton
    fun pipelineStore(db: TPlayDatabase, store: JobStore): PipelineStore = PipelineStoreImpl(db, store)

    @Provides
    @Singleton
    fun trakt(): TraktRepository = FakeTraktRepository()
}
