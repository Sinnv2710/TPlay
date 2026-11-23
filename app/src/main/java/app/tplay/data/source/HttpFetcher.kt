package app.tplay.data.source

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The site asked for a human check (Cloudflare or similar). The user has to pass it in the in-app browser first. */
class ChallengeRequired(val url: String) : RuntimeException("The site wants a quick check in the browser")

/** Downloads a text page. Returns null when the site cannot be reached, answers an error, or is too large. */
fun interface Fetcher {
    suspend fun get(url: String, maxBytes: Int): String?
}

@Singleton
class HttpFetcher @Inject constructor(@ApplicationContext private val context: Context) : Fetcher {
    /** Same address bar identity as the in-app browser, so a cleared check there is accepted here too. */
    private val userAgent: String by lazy { WebSettings.getDefaultUserAgent(context) }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).apply {
            CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) }
        }.build()
        try {
            http.newCall(request).execute().use { response ->
                val bytes = response.body?.byteStream()?.readNBytes(maxBytes + 1) ?: ByteArray(0)
                val text = if (bytes.size > maxBytes) null else String(bytes, Charsets.UTF_8)
                if (isChallenge(response.code, response.header("cf-mitigated"), text)) throw ChallengeRequired(url)
                if (response.isSuccessful) text else null
            }
        } catch (e: java.io.IOException) {
            null
        }
    }
}

/** True for the "checking your browser" wall that some sites put in front of every page. */
fun isChallenge(code: Int, mitigatedHeader: String?, body: String?): Boolean {
    if (mitigatedHeader.equals("challenge", ignoreCase = true)) return true
    if (code != 403 && code != 503) return false
    val text = body.orEmpty()
    return text.contains("cf-chl") || text.contains("Just a moment") || text.contains("challenge-platform")
}
