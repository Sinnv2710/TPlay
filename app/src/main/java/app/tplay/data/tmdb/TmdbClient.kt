package app.tplay.data.tmdb

import app.tplay.domain.ServiceIds
import app.tplay.domain.VaultRepository
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class TmdbInfo(val posterUrl: String?, val overview: String?, val releaseDate: String?, val rating: Double?)

/** TMDB v3 lookup for posters and details. The key comes from the vault; this product uses the TMDB API. */
class TmdbClient(
    private val vault: VaultRepository,
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.themoviedb.org/3",
    private val imageBase: String = "https://image.tmdb.org/t/p/w342",
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Results(val results: List<Hit> = emptyList())

    @Serializable
    private data class Hit(
        val poster_path: String? = null,
        val overview: String? = null,
        val release_date: String? = null,
        val first_air_date: String? = null,
        val vote_average: Double? = null,
        val popularity: Double? = null,
    )

    /** Searches movies first, then TV shows when [tv] is true or nothing was found. Null when no key or no match. */
    suspend fun lookup(title: String, year: Int?, tv: Boolean = false): TmdbInfo? = withContext(Dispatchers.IO) {
        val key = vault.get(ServiceIds.TMDB) ?: return@withContext null
        val order = if (tv) listOf("tv", "movie") else listOf("movie", "tv")
        for (kind in order) {
            val hit = search(key, kind, title, year) ?: continue
            return@withContext TmdbInfo(
                posterUrl = hit.poster_path?.let { imageBase + it },
                overview = hit.overview?.takeIf { it.isNotBlank() },
                releaseDate = hit.release_date ?: hit.first_air_date,
                rating = hit.vote_average,
            )
        }
        null
    }

    private fun search(key: String, kind: String, title: String, year: Int?): Hit? {
        val yearParam = when {
            year == null -> ""
            kind == "movie" -> "&year=$year"
            else -> "&first_air_date_year=$year"
        }
        val query = URLEncoder.encode(title, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/search/$kind?query=$query$yearParam&include_adult=false&api_key=$key")
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                json.decodeFromString<Results>(response.body?.string().orEmpty()).results
                    .filter { it.poster_path != null }
                    .maxByOrNull { it.popularity ?: 0.0 }
            }
        } catch (e: IOException) {
            null
        }
    }
}
