package app.tplay.work

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.torrent_flags_t

/** libtorrent4j behind [TorrentEngine]. One short-lived session per download, nothing left running afterwards. */
class LibtorrentEngine(
    private val metadataTimeoutMs: Long = 3 * 60_000L,
    private val stallTimeoutMs: Long = 5 * 60_000L,
    private val tickMs: Long = 1_000L,
) : TorrentEngine {
    override suspend fun download(magnet: String, dir: File, onProgress: (TorrentProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val hash = AddTorrentParams.parseMagnetUri(magnet).infoHashes.best
            val session = SessionManager(false)
            var handle: TorrentHandle? = null
            try {
                session.start()
                session.download(magnet, dir, torrent_flags_t())
                waitUntilDone(session, hash, onProgress).also { handle = it }
            } finally {
                runCatching { handle?.let { session.remove(it) } }
                runCatching { session.stop() }
            }
        }
    }

    private suspend fun waitUntilDone(
        session: SessionManager,
        hash: org.libtorrent4j.Sha1Hash,
        onProgress: (TorrentProgress) -> Unit,
    ): TorrentHandle? {
        val started = System.currentTimeMillis()
        var lastMoved = started
        var lastDone = 0L
        var handle: TorrentHandle? = null
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

    companion object {
        const val NO_PEERS = "No one is sharing this torrent right now (no peers found)."
        const val STALLED = "The download stopped: no peer has sent data for 5 minutes."
    }
}
