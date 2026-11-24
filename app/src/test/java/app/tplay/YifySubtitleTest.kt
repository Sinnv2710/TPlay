package app.tplay

import app.tplay.data.subs.YifySubtitleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YifySubtitleTest {
    private val source = YifySubtitleSource(imdbId = { _, _ -> null })
    private val page = javaClass.getResource("/discover/yify-movie.html")!!.readText()

    @Test fun listsOnlyTheWantedLanguage() {
        val hits = source.parse(page, "vi", "Oppenheimer", 2023)
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.language == "vi" && it.source == "YIFY" })
        assertEquals("oppenheimer-2023-vietnamese-yify-570201", hits.first().id)
        assertTrue(hits.first().release.contains("Oppenheimer.2023"))
    }

    @Test fun otherLanguagesAreSkipped() {
        assertEquals(1, source.parse(page, "fr", "Oppenheimer", 2023).size)
        assertTrue(source.parse(page, "de", "Oppenheimer", 2023).isEmpty())
    }

    @Test fun decodesUtf8AndVietnameseLegacyCodePage() {
        val text = "Xin chào các bạn"
        assertEquals(text, YifySubtitleSource.decode(text.toByteArray(Charsets.UTF_8), "vi"))
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val legacy = decomposed.toByteArray(java.nio.charset.Charset.forName("windows-1258"))
        val back = YifySubtitleSource.decode(legacy, "vi")
        assertEquals(text, java.text.Normalizer.normalize(back, java.text.Normalizer.Form.NFC))
    }
}
