package app.tplay.data.subs

import app.tplay.domain.SubtitleHit
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * YIFY Subtitles (yifysubtitles.ch), the subtitle site of the YTS family. Looked up by IMDb id, so a hit is always for
 * the right film. Many Vietnamese subtitles. No account or key; a download is a zip with one .srt.
 */
class YifySubtitleSource(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://yifysubtitles.ch",
    private val imdbId: suspend (title: String, year: Int?) -> String?,
) {
    suspend fun search(title: String, year: Int?, language: String): List<SubtitleHit> {
        val imdb = runCatching { imdbId(title, year) }.getOrNull() ?: return emptyList()
        val html = try {
            http.newCall(page("$baseUrl/movie-imdb/$imdb")).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                r.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            return emptyList()
        }
        return parse(html, language, title, year)
    }

    fun parse(html: String, language: String, title: String, year: Int?): List<SubtitleHit> {
        val wanted = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH).lowercase()
        return ROW.findAll(html).mapNotNull { row ->
            val body = row.groupValues[1]
            val lang = LANG.find(body)?.groupValues?.get(1)?.trim()?.lowercase() ?: return@mapNotNull null
            if (lang != wanted) return@mapNotNull null
            val slug = LINK.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val names = LINK_TEXT.find(body)?.groupValues?.get(1).orEmpty()
                .replace(TAGS, " ").replace(Regex("\\s+"), " ").trim().removePrefix("subtitle").trim()
            val rating = RATING.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            SubtitleHit(
                id = slug, language = language, release = names, downloads = rating, source = SOURCE,
                movie = title, year = year,
            )
        }.toList()
    }

    fun download(hit: SubtitleHit, intoFolder: String): String {
        val bytes = http.newCall(page("$baseUrl/subtitle/${hit.id}.zip")).execute().use { r ->
            check(r.isSuccessful) { "Subtitle download failed: HTTP ${r.code}" }
            r.body?.bytes() ?: error("Empty subtitle")
        }
        val srt = ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name.lowercase().endsWith(".srt") }
                ?.let { zip.readBytes() }
        } ?: error("No .srt inside the YIFY zip")
        val target = File(intoFolder, "subtitle.${hit.language.ifBlank { "und" }}.srt")
        target.writeText(decode(srt, hit.language), Charsets.UTF_8)
        return target.path
    }

    private fun page(url: String) = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()

    companion object {
        const val SOURCE = "YIFY"
        private val ROW = Regex("<tr data-id[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        private val LANG = Regex("class=\"sub-lang\">([^<]*)<")
        private val LINK = Regex("href=\"/subtitles/([^\"]+)\"")
        private val LINK_TEXT = Regex("href=\"/subtitles/[^\"]+\">(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        private val RATING = Regex("class=\"label[^\"]*\">(-?\\d+)<")
        private val TAGS = Regex("<[^>]+>")

        /** Subtitle files come in all encodings. Keep UTF-8 as is, otherwise use the legacy code page for the language. */
        fun decode(bytes: ByteArray, language: String): String {
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val data = if (bytes.size > 2 && bytes.copyOf(3).contentEquals(bom)) {
                bytes.copyOfRange(3, bytes.size)
            } else {
                bytes
            }
            return try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
            } catch (e: CharacterCodingException) {
                val legacy = if (language == "vi") "windows-1258" else "windows-1252"
                String(data, Charset.forName(legacy))
            }
        }
    }
}
