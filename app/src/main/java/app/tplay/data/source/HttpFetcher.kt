package app.tplay.data.source

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a text page. Returns null when the site cannot be reached, answers an error, or is too large. */
fun interface Fetcher {
    suspend fun get(url: String, maxBytes: Int): String?
}

@Singleton
class HttpFetcher @Inject constructor() : Fetcher {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.byteStream()?.readNBytes(maxBytes + 1) ?: return@use null
                if (bytes.size > maxBytes) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0 Mobile Safari/537.36"
    }
}
