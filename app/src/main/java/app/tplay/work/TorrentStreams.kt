package app.tplay.work

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Tells the player which pieces of a half-downloaded file are on disk yet. */
interface PieceSource {
    /** True when every byte of [fromByte, toByte) of the file is already written. */
    fun has(fromByte: Long, toByte: Long): Boolean

    /** Asks the torrent to fetch that range first (a seek). */
    fun hurry(fromByte: Long, toByte: Long)
}

/** Files that are still downloading and can be played anyway. Entries disappear when the download ends. */
object TorrentStreams {
    private val active = ConcurrentHashMap<String, PieceSource>()

    fun register(file: File, source: PieceSource) { active[file.absolutePath] = source }

    fun unregister(file: File) { active.remove(file.absolutePath) }

    fun clear(folder: File) {
        val prefix = folder.absolutePath + File.separator
        active.keys.filter { it.startsWith(prefix) }.forEach { active.remove(it) }
    }

    fun get(path: String): PieceSource? = active[path]

    /** True while something under [folderPath] is still downloading. */
    fun isDownloading(folderPath: String?): Boolean {
        if (folderPath == null) return false
        val prefix = File(folderPath).absolutePath + File.separator
        return active.keys.any { it.startsWith(prefix) }
    }
}
