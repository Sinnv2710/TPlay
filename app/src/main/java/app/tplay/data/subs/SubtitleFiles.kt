package app.tplay.data.subs

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object SubtitleFiles {
    fun openSubtitlesHash(file: RandomAccessFile): String {
        val size = file.length()
        var hash = size
        val chunk = ByteArray(65536)

        fun add(offset: Long, count: Int) {
            file.seek(offset)
            var left = count
            var carry = ByteArray(0)
            while (left > 0) {
                val read = file.read(chunk, 0, min(left, chunk.size))
                if (read <= 0) break
                val data = carry + chunk.copyOf(read)
                var index = 0
                while (index + 8 <= data.size) {
                    hash += ByteBuffer.wrap(data, index, 8)
                        .order(ByteOrder.LITTLE_ENDIAN).long
                    index += 8
                }
                carry = data.copyOfRange(index, data.size)
                left -= read
            }
        }

        add(0, min(size, 65536).toInt())
        if (size > 65536) {
            add(maxOf(0, size - 65536), min(size, 65536).toInt())
        }
        return "%016x".format(hash)
    }

    fun decode(bytes: ByteArray): String {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val utf16Bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        if (bytes.startsWith(utf8Bom)) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        if (bytes.startsWith(utf16Bom)) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        return runCatching {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse {
            bytes.toString(Charsets.ISO_8859_1)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        return size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
