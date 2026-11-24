package app.tplay.data.subs

import app.tplay.domain.SubtitleHit
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * opensubtitles.org legacy REST search. No account or key needed. The hit id carries the
 * download link, so [download] needs nothing else.
 */
class OrgSubtitleSource(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://rest.opensubtitles.org",
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Row(
        val SubLanguageID: String = "",
        val SubFileName: String = "",
        val SubDownloadsCnt: String = "0",
        val SubDownloadLink: String = "",
        val SubFormat: String = "srt",
        val MovieName: String = "",
        val MovieYear: String = "",
    )

    fun search(title: String, year: Int?, language: String, fileHash: String?): List<SubtitleHit> {
        val lang = toIso3(language)
        val path = if (fileHash != null) {
            "moviehash-$fileHash/sublanguageid-$lang"
        } else {
            val query = URLEncoder.encode(title.lowercase(), "UTF-8").replace("+", "%20")
            "query-$query/sublanguageid-$lang"
        }
        val rows = try {
            val request = Request.Builder()
                .url("$baseUrl/search/$path")
                .header("X-User-Agent", "TemporaryUserAgent")
                .header("User-Agent", "TPlay v1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                json.decodeFromString<List<Row>>(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            return emptyList()
        }
        return rows
            .filter { it.SubDownloadLink.isNotBlank() && it.SubFormat.lowercase() in SUPPORTED }
            .map {
                SubtitleHit(
                    id = it.SubDownloadLink,
                    language = language,
                    release = it.SubFileName,
                    downloads = it.SubDownloadsCnt.toIntOrNull() ?: 0,
                    source = "OpenSubtitles.org",
                    movie = it.MovieName,
                    year = it.MovieYear.toIntOrNull(),
                )
            }
    }

    fun download(hit: SubtitleHit, intoFolder: String): String {
        val request = Request.Builder().url(hit.id).header("User-Agent", "TPlay v1.0").build()
        val bytes = http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Subtitle download failed: HTTP ${response.code}" }
            response.body?.bytes() ?: error("Empty subtitle")
        }
        val text = if (bytes.size > 1 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        val target = File(intoFolder, "subtitle.${hit.language.ifBlank { "und" }}.srt")
        target.writeBytes(text)
        return target.path
    }

    companion object {
        private val SUPPORTED = setOf("srt", "sub", "ass", "ssa", "vtt")

        /** The legacy API wants three-letter codes. Unknown input is passed through. */
        fun toIso3(code: String): String = when (code.lowercase()) {
            "en" -> "eng"
            "vi" -> "vie"
            "fr" -> "fre"
            "de" -> "ger"
            "es" -> "spa"
            "it" -> "ita"
            "pt" -> "por"
            "ar" -> "ara"
            "ja" -> "jpn"
            "ko" -> "kor"
            "zh" -> "chi"
            "ru" -> "rus"
            "tr" -> "tur"
            else -> code.lowercase()
        }
    }
}
