package app.tplay.data.discover

/** A movie name and year pulled out of a release name. */
data class CleanTitle(val title: String, val year: Int?)

private val YEAR = Regex("(?<![0-9])((?:19|20)\\d{2})(?![0-9])")
private val BRACKETS = Regex("\\[[^\\]]*\\]|\\{[^}]*\\}")
private val JUNK = Regex(
    "(?i)(?<![a-z0-9])(480p|576p|720p|1080p|1440p|2160p|4k|uhd|blu-?ray|bdrip|brrip|webrip|web-dl|hdrip|dvdrip|" +
        "dvdscr|hdtv|hdcam|cam|x264|x265|h\\.?264|h\\.?265|hevc|remux|hdr10?|atmos|truehd|dts|ac3|aac|multi|" +
        "dual|proper|repack|extended|unrated|imax|directors cut)(?![a-z0-9])",
)

/**
 * Turns a release name such as "Sintel (2010) 2160p DMRip x264 DTS - ABI" or "Sintel.2010.1080p.BluRay" into
 * "Sintel" and 2010. The year is the last one before the first quality or codec word, so "Blade Runner 2049 2017"
 * gives the title "Blade Runner 2049".
 */
fun cleanReleaseTitle(raw: String): CleanTitle {
    val text = BRACKETS.replace(raw, " ").replace('.', ' ').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    val junkAt = JUNK.find(text)?.range?.first ?: text.length
    val year = YEAR.findAll(text).filter { it.range.first > 0 && it.range.first < junkAt }.lastOrNull()
    val cut = year?.range?.first ?: junkAt
    val title = text.substring(0, cut).trim(' ', '(', '[', '-', '–', ':')
    return CleanTitle(title.ifBlank { text }, year?.groupValues?.get(1)?.toInt())
}
