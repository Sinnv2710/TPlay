package app.tplay.data.subs

import app.tplay.domain.ServiceIds
import app.tplay.domain.SubtitleHit
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.VaultRepository
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Vault id for the OpenSubtitles account, stored as "username:password". Needed to download. */
const val OPEN_SUBTITLES_LOGIN = "opensubtitles-login"

/**
 * OpenSubtitles.com REST v1. Search needs only the API key; download also needs a user login
 * (the API answers with a one-time link that the app then fetches).
 */
class SubtitleRepositoryImpl(
    private val vault: VaultRepository,
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.opensubtitles.com/api/v1",
    private val org: OrgSubtitleSource = OrgSubtitleSource(http),
) : SubtitleRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class SearchResponse(val data: List<Entry> = emptyList())

    @Serializable private data class Entry(val id: String = "", val attributes: Attributes = Attributes())

    @Serializable
    private data class Attributes(
        val language: String = "",
        val release: String = "",
        val download_count: Int = 0,
        val files: List<RemoteFile> = emptyList(),
    )

    @Serializable private data class RemoteFile(val file_id: Long, val file_name: String = "")

    @Serializable private data class LoginResponse(val token: String = "")

    @Serializable private data class DownloadResponse(val link: String = "", val file_name: String = "")

    /** Tries opensubtitles.com (needs a key) first, then opensubtitles.org (no key). */
    override suspend fun search(title: String, year: Int?, language: String, fileHash: String?): List<SubtitleHit> {
        val byHash = if (fileHash != null) searchEverywhere(title, year, language, fileHash) else emptyList()
        if (byHash.isNotEmpty()) return byHash
        return searchEverywhere(title, year, language, null)
    }

    private suspend fun searchEverywhere(
        title: String,
        year: Int?,
        language: String,
        fileHash: String?,
    ): List<SubtitleHit> {
        val primary = searchCom(title, year, language, fileHash)
        if (primary.isNotEmpty()) return primary
        return withContext(Dispatchers.IO) { org.search(title, year, language, fileHash) }
    }

    private suspend fun searchCom(title: String, year: Int?, language: String, fileHash: String?): List<SubtitleHit> {
        val key = vault.get(ServiceIds.OPEN_SUBTITLES) ?: return emptyList()
        val query = if (fileHash != null) {
            "moviehash=$fileHash&languages=$language"
        } else {
            val yearPart = year?.let { "&year=$it" }.orEmpty()
            "query=${URLEncoder.encode(title, "UTF-8")}$yearPart&languages=$language"
        }
        val request = Request.Builder()
            .url("$baseUrl/subtitles?$query")
            .header("Api-Key", key)
            .header("User-Agent", USER_AGENT)
            .build()
        val parsed = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                json.decodeFromString<SearchResponse>(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            return emptyList()
        }
        return parsed.data.mapNotNull { entry ->
            val fileId = entry.attributes.files.firstOrNull()?.file_id ?: return@mapNotNull null
            SubtitleHit(
                id = fileId.toString(),
                language = entry.attributes.language,
                release = entry.attributes.release,
                downloads = entry.attributes.download_count,
                source = "OpenSubtitles",
            )
        }
    }

    override suspend fun download(hit: SubtitleHit, intoFolder: String): String {
        if (hit.source == "OpenSubtitles.org") {
            return withContext(Dispatchers.IO) { org.download(hit, intoFolder) }
        }
        return try {
            withContext(Dispatchers.IO) { downloadCom(hit, intoFolder) }
        } catch (e: IllegalStateException) {
            downloadViaOrg(hit, intoFolder, e)
        } catch (e: IOException) {
            downloadViaOrg(hit, intoFolder, e)
        }
    }

    /** opensubtitles.com download often answers 503 or needs a login: retry the same release on .org. */
    private suspend fun downloadViaOrg(hit: SubtitleHit, intoFolder: String, cause: Exception): String =
        withContext(Dispatchers.IO) {
            val name = hit.release.substringBeforeLast('.').replace('.', ' ').replace('_', ' ')
            val best = org.search(name, null, hit.language, null).maxByOrNull { it.downloads }
                ?: throw IllegalStateException("No subtitle on .com (${cause.message}) or .org", cause)
            org.download(best, intoFolder)
        }

    private suspend fun downloadCom(hit: SubtitleHit, intoFolder: String): String {
        val key = vault.get(ServiceIds.OPEN_SUBTITLES) ?: error("OpenSubtitles API key is not set")
        val login = vault.get(OPEN_SUBTITLES_LOGIN) ?: error("OpenSubtitles login is not set (needed to download)")
        val token = login(key, login)
        val link = requestLink(key, token, hit.id)
        val target = File(intoFolder, "subtitle.${hit.language.ifBlank { "und" }}.srt")
        http.newCall(Request.Builder().url(link).build()).execute().use { response ->
            check(response.isSuccessful) { "Subtitle download failed: HTTP ${response.code}" }
            target.writeBytes(response.body?.bytes() ?: error("Empty subtitle"))
        }
        return target.path
    }

    private fun login(key: String, credentials: String): String {
        val user = credentials.substringBefore(':')
        val password = credentials.substringAfter(':', "")
        val body = """{"username":${quote(user)},"password":${quote(password)}}"""
        val request = Request.Builder()
            .url("$baseUrl/login")
            .header("Api-Key", key)
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "OpenSubtitles login failed: HTTP ${response.code}" }
            return json.decodeFromString<LoginResponse>(response.body?.string().orEmpty()).token
        }
    }

    private fun requestLink(key: String, token: String, fileId: String): String {
        val request = Request.Builder()
            .url("$baseUrl/download")
            .header("Api-Key", key)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", USER_AGENT)
            .post("""{"file_id":$fileId}""".toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "OpenSubtitles download request failed: HTTP ${response.code}" }
            val link = json.decodeFromString<DownloadResponse>(response.body?.string().orEmpty()).link
            check(link.isNotBlank()) { "OpenSubtitles returned no link" }
            return link
        }
    }

    private fun quote(text: String) = Json.encodeToString(kotlinx.serialization.serializer<String>(), text)

    private companion object {
        const val USER_AGENT = "TPlay v1.0"
        val JSON = "application/json".toMediaType()
    }
}
