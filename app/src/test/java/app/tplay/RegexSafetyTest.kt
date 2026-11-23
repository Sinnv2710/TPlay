package app.tplay

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's regex engine refuses patterns the desktop JVM accepts: a lone `}` or `]` that is not escaped. Unit tests run
 * on the desktop JVM, so such a pattern passes here and then crashes the app on the phone. This test reads every
 * Regex("...") in the app and fails on those characters.
 */
class RegexSafetyTest {
    private val literal = Regex("Regex\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val quantifierEnd = Regex("\\{[0-9]+(,[0-9]*)?\\}$")

    /** Unescapes a Kotlin string literal enough to see the pattern's own backslashes. */
    private fun pattern(raw: String): String = raw.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\")

    /** Positions of `}` or `]` that Android would reject: not escaped, not closing a `{n,m}` or an open `[...]` class. */
    internal fun unsafe(pattern: String): List<Int> {
        val bad = mutableListOf<Int>()
        var inClass = false
        var i = 0
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '\\' -> i++
                '[' -> if (!inClass) inClass = true
                ']' -> if (inClass) inClass = false else bad += i
                '}' -> if (!inClass && !quantifierEnd.containsMatchIn(pattern.substring(0, i + 1))) bad += i
                else -> Unit
            }
            i++
        }
        return bad
    }

    @Test fun theCheckItselfFindsTheBugsItShould() {
        assertTrue(unsafe("\\{([A-Za-z0-9_]+)}").isNotEmpty())
        assertTrue(unsafe("\\[[^\\]]*]").isNotEmpty())
        assertTrue(unsafe("\\{([A-Za-z0-9_]+)\\}").isEmpty())
        assertTrue(unsafe("[a-z0-9][a-z0-9-]{0,39}").isEmpty())
        assertTrue(unsafe("(19|20)\\d{2}").isEmpty())
        assertTrue(unsafe("[^}]*").isEmpty())
    }

    @Test fun noRegexInTheAppUsesACharacterAndroidRejects() {
        val problems = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            literal.findAll(file.readText()).mapNotNull { match ->
                val text = pattern(match.groupValues[1])
                unsafe(text).takeIf { it.isNotEmpty() }?.let { "${file.name}: $text" }
            }
        }.toList()
        assertTrue("Escape these with a backslash:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
