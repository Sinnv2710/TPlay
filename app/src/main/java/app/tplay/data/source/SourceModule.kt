package app.tplay.data.source

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class FetcherModule {
    @Binds
    abstract fun fetcher(impl: HttpFetcher): Fetcher
}

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    @Provides
    @Singleton
    fun store(@ApplicationContext context: Context, fetcher: Fetcher): SourceStore =
        SourceStore(File(context.filesDir, "sources"), fetcher, builtInSources(context))

    private fun builtInSources(context: Context): List<SourceDef> {
        val json = Json { ignoreUnknownKeys = true }
        val names = context.assets.list("sources").orEmpty().filter { it.endsWith(".json") }
        return names.mapNotNull { name ->
            runCatching {
                json.decodeFromString<SourceDef>(context.assets.open("sources/$name").bufferedReader().readText())
            }.getOrNull()
        }
    }
}
