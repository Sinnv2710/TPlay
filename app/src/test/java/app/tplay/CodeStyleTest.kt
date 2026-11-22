package app.tplay

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fails when a Kotlin file has a line longer than 125 characters (readable code rule). */
class CodeStyleTest {
    @Test
    fun noOverlongLines() {
        val roots = listOf(File("src/main"), File("src/test"), File("src/androidTest"))
        val offenders = roots
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.length > MAX) "${file.path}:${index + 1} (${line.length})" else null
                }
            }
        assertTrue("Lines over $MAX chars, split them:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private companion object {
        const val MAX = 125
    }
}
