package app.tplay.work.di

import kotlinx.coroutines.flow.first

import app.tplay.data.rd.RealDebridClient
import app.tplay.data.tmdb.TmdbClient
import app.tplay.data.subs.SubtitleRepositoryImpl
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.VaultRepository
import app.tplay.work.PipelineRunner
import app.tplay.work.PipelineStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {
    @Provides
    @Singleton
    fun realDebrid(vault: VaultRepository) = RealDebridClient(vault)

    @Provides
    @Singleton
    fun tmdb(vault: VaultRepository) = TmdbClient(vault)

    @Provides
    @Singleton
    fun subtitles(vault: VaultRepository): SubtitleRepository = SubtitleRepositoryImpl(vault)

    @Provides
    @Singleton
    fun runner(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        rd: RealDebridClient,
        subtitles: SubtitleRepository,
        store: PipelineStore,
        tmdb: TmdbClient,
        settings: app.tplay.domain.SettingsRepository,
    ) = PipelineRunner(
        rd, subtitles, store, tmdb = tmdb, engine = app.tplay.work.LibtorrentEngine(),
        directRoot = java.io.File(context.filesDir, "Direct"),
        subtitleLanguage = { settings.subtitleLanguage().first() },
    )
}
