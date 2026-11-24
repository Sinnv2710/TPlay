package app.tplay.work

import java.io.File

/** What a direct torrent download is doing right now. */
data class TorrentProgress(val percent: Int, val bytesPerSec: Long, val peers: Int, val stage: String)

/** A BitTorrent client the pipeline can ask to download a magnet link onto the phone. */
interface TorrentEngine {
    /**
     * Downloads [magnet] into [dir] and returns when every file is complete. Stops sharing as soon as it is done.
     * Downloads in order and calls [onStreamable] with the main video file once enough of its start is on disk to play it.
     * Throws [TorrentException] with a readable reason when nobody sends data or the torrent cannot start.
     */
    suspend fun download(
        magnet: String,
        dir: File,
        onStreamable: (File) -> Unit = {},
        onProgress: (TorrentProgress) -> Unit,
    )
}

class TorrentException(message: String) : RuntimeException(message)
