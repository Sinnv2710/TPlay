package app.tplay.share

import java.net.URLDecoder
import java.util.Locale

data class MagnetInfo(
    val uri: String,
    val infoHash: String,
    val displayName: String?,
    val trackers: List<String>,
)

/** Parses user-provided magnet links without contacting trackers. */
object MagnetParser {
    fun parse(value: String): MagnetInfo? {
        val raw = value.trim()
        if (!raw.startsWith("magnet:", ignoreCase = true)) return null
        val query = raw.substringAfter('?', "")
        val parameters = query.split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2) {
                pair[0] to URLDecoder.decode(pair[1], "UTF-8")
            } else {
                null
            }
        }
        val xt = parameters
            .filter { it.first.equals("xt", ignoreCase = true) }
            .map { it.second }
            .firstOrNull { it.startsWith("urn:btih:", ignoreCase = true) }
            ?: return null
        val hash = xt.substringAfter("urn:btih:").trim()
        val valid = (hash.length == 40 && hash.all { it in "0123456789abcdefABCDEF" }) ||
            (hash.length == 32 && hash.uppercase(Locale.ROOT).all {
                it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            })
        if (!valid) return null
        return MagnetInfo(
            uri = raw,
            infoHash = hash.uppercase(Locale.ROOT),
            displayName = parameters.firstOrNull { it.first.equals("dn", true) }
                ?.second?.takeIf { it.isNotBlank() },
            trackers = parameters.filter { it.first.equals("tr", true) }.map { it.second },
        )
    }
}
