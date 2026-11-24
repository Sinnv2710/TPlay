package app.tplay.work

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus

/** libtorrent4j behind [TorrentEngine]. One short-lived session per download, nothing left running afterwards. */
class LibtorrentEngine(
    private val metadataTimeoutMs: Long = 3 * 60_000L,
    private val stallTimeoutMs: Long = 5 * 60_000L,
    private val tickMs: Long = 1_000L,
) : TorrentEngine {
    override suspend fun download(
        magnet: String,
        dir: File,
        onStreamable: (File) -> Unit,
        onProgress: (TorrentProgress) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val hash = AddTorrentParams.parseMagnetUri(magnet).infoHashes.best
            val session = SessionManager(false)
            var handle: TorrentHandle? = null
            try {
                session.start()
                session.download(magnet, dir, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                waitUntilDone(session, hash, dir, onStreamable, onProgress).also { handle = it }
            } finally {
                TorrentStreams.clear(dir)
                runCatching { handle?.let { session.remove(it) } }
                runCatching { session.stop() }
            }
        }
    }

    private suspend fun waitUntilDone(
        session: SessionManager,
        hash: org.libtorrent4j.Sha1Hash,
        dir: File,
        onStreamable: (File) -> Unit,
        onProgress: (TorrentProgress) -> Unit,
    ): TorrentHandle? {
        val started = System.currentTimeMillis()
        var lastMoved = started
        var lastDone = 0L
        var handle: TorrentHandle? = null
        var streamSource: PieceSource? = null
        var streamFile: File? = null
        var announced = false
        var streamTried = false
        while (true) {
            delay(tickMs)
            val now = System.currentTimeMillis()
            handle = handle ?: session.find(hash)?.takeIf { it.isValid }
            val status = handle?.status()
            if (status == null || status.state() == TorrentStatus.State.DOWNLOADING_METADATA) {
                if (now - started > metadataTimeoutMs) throw TorrentException(NO_PEERS)
                onProgress(TorrentProgress(0, 0, status?.numPeers() ?: 0, "Looking for peers"))
                continue
            }
            if (!streamTried) {
                streamTried = true
                val found = openStream(handle!!, dir)
                streamFile = found?.first
                streamSource = found?.second
            }
            val source = streamSource
            val file = streamFile
            if (!announced && source != null && file != null) {
                if (source.has(0, HEAD_BYTES)) {
                    announced = true
                    onStreamable(file)
                }
            }
            val error = status.errorCode()
            if (error.isError) throw TorrentException("Torrent error: ${error.message}")
            val done = status.totalDone()
            if (done > lastDone) {
                lastDone = done
                lastMoved = now
            }
            val percent = (status.progress() * 100).toInt().coerceIn(0, 100)
            if (status.isFinished || status.isSeeding) {
                onProgress(TorrentProgress(100, 0, status.numPeers(), "Finished"))
                return handle
            }
            if (now - lastMoved > stallTimeoutMs) throw TorrentException(STALLED)
            val stage = if (status.state() == TorrentStatus.State.CHECKING_FILES) "Checking files" else "Downloading"
            onProgress(TorrentProgress(percent, status.downloadRate().toLong(), status.numPeers(), stage))
        }
    }

    /** Registers the biggest video file as playable-while-downloading. Null when the torrent has no video. */
    private fun openStream(handle: TorrentHandle, dir: File): Pair<File, PieceSource>? {
        val info = handle.torrentFile() ?: return null
        val files = info.files()
        val index = (0 until info.numFiles())
            .filter { isVideo(files.filePath(it)) }
            .maxByOrNull { files.fileSize(it) } ?: return null
        val file = File(dir, files.filePath(index))
        val start = files.fileOffset(index)
        val size = files.fileSize(index)
        val pieceLength = info.pieceLength().toLong()
        val lastPiece = info.numPieces() - 1
        fun first(byte: Long) = ((start + byte) / pieceLength).toInt().coerceIn(0, lastPiece)
        val source = object : PieceSource {
            override fun has(fromByte: Long, toByte: Long): Boolean {
                val to = toByte.coerceAtMost(size)
                if (to <= fromByte) return true
                return (first(fromByte)..first(to - 1)).all { handle.havePiece(it) }
            }

            override fun hurry(fromByte: Long, toByte: Long) {
                val from = first(fromByte)
                val to = first((toByte.coerceAtMost(size) - 1).coerceAtLeast(fromByte))
                var rank = 0
                for (piece in from..minOf(to, from + HURRY_PIECES)) {
                    if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 200 * rank++)
                }
            }
        }
        TorrentStreams.register(file, source)
        // Players often read the index at the end of the file first: ask for it early.
        source.hurry(maxOf(0, size - pieceLength * 2), size)
        return file to source
    }

    companion object {
        private const val HEAD_BYTES = 12L * 1024 * 1024
        private const val HURRY_PIECES = 12
        const val NO_PEERS = "No one is sharing this torrent right now (no peers found)."
        const val STALLED = "The download stopped: no peer has sent data for 5 minutes."
    }
}
