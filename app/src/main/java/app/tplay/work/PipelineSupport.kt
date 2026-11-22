package app.tplay.work

import app.tplay.data.rd.RdFile

private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm", "ts", "wmv")
private val ARCHIVE_EXT = setOf("rar", "zip")
private val RAR_PART = Regex("r\\d{2,3}")
private val YEAR = Regex("[.\\s_(\\[]((?:19|20)\\d{2})(?=[.\\s_)\\]]|$)")

fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

fun isArchivePart(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in ARCHIVE_EXT || RAR_PART.matches(ext)
}

/**
 * Chooses which files of a torrent to fetch: the largest video, else all archive
 * parts (a split RAR needs every part), else the single largest file.
 */
fun chooseFiles(files: List<RdFile>): List<RdFile> {
    val video = files.filter { isVideo(it.path) }.maxByOrNull { it.bytes }
    if (video != null) return listOf(video)
    val archives = files.filter { isArchivePart(it.path) }
    if (archives.isNotEmpty()) return archives.sortedBy { it.path }
    return listOfNotNull(files.maxByOrNull { it.bytes })
}

data class ParsedName(val title: String, val year: Int?)

/** Turns a release name like "The.Movie.2026.1080p.WEB" into "The Movie" and 2026. */
fun parseTitleName(raw: String): ParsedName {
    val withoutExt = if (isVideo(raw) || isArchivePart(raw)) raw.substringBeforeLast('.') else raw
    val match = YEAR.find(withoutExt)
    val cut = match?.range?.first ?: withoutExt.length
    val title = withoutExt.substring(0, cut)
        .replace('.', ' ')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { withoutExt }
    return ParsedName(title, match?.groupValues?.get(1)?.toInt())
}

fun folderNameFor(parsed: ParsedName): String {
    val safe = parsed.title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "Movie" }
    return if (parsed.year != null) "$safe (${parsed.year})" else safe
}
