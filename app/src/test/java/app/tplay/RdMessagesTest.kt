package app.tplay

import app.tplay.work.friendlyRdError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdMessagesTest {
    @Test fun blockedFileIsExplainedInPlainWords() {
        val text = friendlyRdError(451, "{\n  \"error\": \"infringing_file\",\n  \"error_code\": 35\n}")
        assertTrue(text.startsWith("Real-Debrid blocked this file as copyrighted content"))
        assertTrue(text.endsWith("(451 infringing_file)"))
    }

    @Test fun missingTorrentIsExplained() {
        val text = friendlyRdError(404, "{\"error\": \"unknown_ressource\", \"error_code\": 7}")
        assertTrue(text.contains("no longer has this torrent"))
    }

    @Test fun unknownErrorsKeepTheirCode() {
        assertEquals("Real-Debrid error 500: weird_thing", friendlyRdError(500, "{\"error\":\"weird_thing\"}"))
        assertEquals("Real-Debrid error 502: Bad Gateway", friendlyRdError(502, "Bad Gateway"))
        assertEquals("Real-Debrid error 500: ", friendlyRdError(500, null))
    }
}
