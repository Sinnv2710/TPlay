package app.tplay.share

import org.junit.Assert.*
import org.junit.Test

class MagnetParserTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    @Test fun validCases() {
        val cases = listOf(
            "magnet:?xt=urn:btih:$hash",
            "MAGNET:?xt=urn:btih:${hash.uppercase()}",
            "magnet:?dn=My%20Film&xt=urn:btih:$hash",
            "magnet:?xt=urn:btih:$hash&tr=a&tr=b",
            "magnet:?xt=urn:btih:$hash&dn=Film",
            "magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUV234567ABCD",
            " magnet:?xt=urn:btih:$hash ",
            "magnet:?tr=a&xt=urn:btih:$hash",
            "magnet:?xt=urn%3Abtih%3A$hash",
            "magnet:?xt=urn:btih:$hash&dn=",
            "magnet:?xt=urn:btih:$hash&dn=A%2FB",
            "magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Ftracker",
            "magnet:?xt=urn:btih:$hash&foo=x",
            "magnet:?xt=urn:btih:$hash&dn=%E2%98%83",
            "magnet:?xt=urn:btih:$hash&xt=urn:btih:bad"
        )
        cases.forEach { assertNotNull("failed: $it", MagnetParser.parse(it)) }
    }
    @Test fun malformedCasesRejected() {
        listOf(
            "", "http://magnet", "magnet:", "magnet:?dn=x",
            "magnet:?xt=urn:btih:bad", "magnet:?xt=urn:btih:0123",
        ).forEach { assertNull(MagnetParser.parse(it)) }
    }
}
