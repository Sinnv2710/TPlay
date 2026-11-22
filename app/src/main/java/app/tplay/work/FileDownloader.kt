package app.tplay.work

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class FileDownloader(private val client: OkHttpClient = OkHttpClient()) {
    fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        val existing = destination.length()
        val request = Request.Builder().url(url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416) return
            require(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val append = existing > 0 && response.code == 206
            val offset = if (append) existing else 0L
            val body = requireNotNull(response.body) { "Download returned no body" }
            val total = body.contentLength().takeIf { it >= 0 }?.let { it + offset }
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = offset
                    var count = input.read(buffer)
                    while (count >= 0) {
                        if (count > 0) {
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                        count = input.read(buffer)
                    }
                }
            }
        }
    }
}
