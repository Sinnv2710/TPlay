package app.tplay.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import app.tplay.work.PieceSource
import app.tplay.work.TorrentStreams
import java.io.InterruptedIOException

/**
 * Reads a local file. If the file is still being downloaded by the torrent engine, a read waits until the pieces it
 * needs are on disk (and asks the engine to fetch them first). Finished files are read straight through.
 */
@UnstableApi
class GrowingFileDataSource : DataSource {
    private val file = FileDataSource()
    private var source: PieceSource? = null
    private var position = 0L

    override fun addTransferListener(transferListener: TransferListener) = file.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val path = dataSpec.uri.path
        source = path?.let { TorrentStreams.get(it) }
        position = dataSpec.position
        source?.let { waitFor(it, position, position + 1) }
        return file.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val pieces = source
        if (pieces != null && TorrentStreams.get(currentPath()) != null) {
            waitFor(pieces, position, position + minOf(length, CHUNK))
            val read = file.read(buffer, offset, minOf(length, CHUNK))
            if (read > 0) position += read
            return read
        }
        val read = file.read(buffer, offset, length)
        if (read > 0) position += read
        return read
    }

    private fun currentPath(): String = file.uri?.path.orEmpty()

    private fun waitFor(pieces: PieceSource, from: Long, to: Long) {
        if (pieces.has(from, to)) return
        pieces.hurry(from, to + AHEAD)
        while (!pieces.has(from, to)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            // Download ended or was cancelled: the file on disk is all there will ever be.
            if (TorrentStreams.get(currentPath()) == null) return
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        }
    }

    override fun getUri(): Uri? = file.uri

    override fun close() {
        file.close()
        source = null
    }

    companion object {
        private const val CHUNK = 256 * 1024
        private const val AHEAD = 8L * 1024 * 1024
        private const val POLL_MS = 250L
    }
}
