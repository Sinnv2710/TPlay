package app.tplay.data.rd

import app.tplay.domain.ServiceIds
import app.tplay.domain.VaultRepository
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class RdResult<out T> {
    data class Ok<T>(val value: T) : RdResult<T>()
    data class Failure(val error: RdError) : RdResult<Nothing>()
}

sealed class RdError {
    data class Http(val code: Int, val message: String) : RdError()
    data class Network(val cause: Throwable) : RdError()
    data class Api(val message: String) : RdError()
}

@Serializable
data class AddMagnetResponse(val id: String, val uri: String? = null)

@Serializable
data class RdFile(val id: Int, val path: String, val bytes: Long, val selected: Int? = null)

@Serializable
data class TorrentInfo(
    val id: String,
    val filename: String? = null,
    val status: String,
    val progress: Double = 0.0,
    val speed: Long? = null,
    val files: List<RdFile> = emptyList(),
    val links: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
data class RdTorrentSummary(
    val id: String,
    val filename: String = "",
    val bytes: Long = 0,
    val status: String = "",
    val added: String? = null,
    val links: List<String> = emptyList(),
)

@Serializable
data class Unrestricted(
    val id: String? = null,
    val filename: String? = null,
    val link: String,
    val download: String? = null,
    val filesize: Long? = null,
)

/**
 * Real-Debrid REST v1.0 client. Limit is 250 requests per minute: polling is spaced
 * at least [POLL_MS] apart and 429 answers back off with jitter.
 */
class RealDebridClient(
    private val vault: VaultRepository,
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.real-debrid.com/rest/1.0/",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var lastPoll = 0L

    suspend fun addMagnet(magnet: String): RdResult<AddMagnetResponse> {
        val body = FormBody.Builder().add("magnet", magnet).build()
        return request("POST", "torrents/addMagnet", body).decode()
    }

    suspend fun info(id: String): RdResult<TorrentInfo> {
        return request("GET", "torrents/info/$id", poll = true).decode()
    }

    suspend fun selectFiles(id: String, files: String): RdResult<String> {
        val body = FormBody.Builder().add("files", files).build()
        return request("POST", "torrents/selectFiles/$id", body)
    }

    suspend fun unrestrict(link: String): RdResult<Unrestricted> {
        val body = FormBody.Builder().add("link", link).build()
        return request("POST", "unrestrict/link", body).decode()
    }

    /** Torrents already on the account, newest first. */
    suspend fun list(limit: Int = 100): RdResult<List<RdTorrentSummary>> {
        return request("GET", "torrents?limit=$limit&page=1").decode()
    }

    suspend fun delete(id: String): RdResult<String> = request("DELETE", "torrents/delete/$id")

    suspend fun user(): RdResult<String> = request("GET", "user")

    private suspend fun request(
        method: String,
        path: String,
        body: FormBody? = null,
        poll: Boolean = false,
    ): RdResult<String> {
        if (poll) spacePoll()
        val token = vault.get(ServiceIds.REAL_DEBRID)
            ?: return RdResult.Failure(RdError.Api("token not configured"))
        val sendBody = body ?: if (method == "POST" || method == "PUT") FormBody.Builder().build() else null
        var attempt = 0
        while (true) {
            val outcome = try {
                withContext(Dispatchers.IO) { call(method, path, sendBody, token) }
            } catch (e: IOException) {
                return RdResult.Failure(RdError.Network(e))
            }
            if (outcome.code != 429) return outcome.result
            if (attempt >= MAX_RETRIES) return RdResult.Failure(RdError.Http(429, "rate limit"))
            attempt++
            delay((BASE_BACKOFF_MS shl attempt) + Random.nextLong(JITTER_MS))
        }
    }

    private class Outcome(val code: Int, val result: RdResult<String>)

    private fun call(method: String, path: String, body: FormBody?, token: String): Outcome {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val result = if (response.isSuccessful) {
                RdResult.Ok(text)
            } else {
                RdResult.Failure(RdError.Http(response.code, text.take(MAX_ERROR_CHARS)))
            }
            return Outcome(response.code, result)
        }
    }

    private suspend fun spacePoll() {
        val wait = POLL_MS - (System.currentTimeMillis() - lastPoll)
        if (wait > 0) delay(wait)
        lastPoll = System.currentTimeMillis()
    }

    private inline fun <reified T> RdResult<String>.decode(): RdResult<T> = when (this) {
        is RdResult.Failure -> this
        is RdResult.Ok -> try {
            RdResult.Ok(json.decodeFromString<T>(value))
        } catch (e: kotlinx.serialization.SerializationException) {
            RdResult.Failure(RdError.Api("invalid response"))
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val MAX_RETRIES = 5
        const val BASE_BACKOFF_MS = 500L
        const val JITTER_MS = 250L
        const val MAX_ERROR_CHARS = 300
    }
}
