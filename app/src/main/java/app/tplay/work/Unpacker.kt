package app.tplay.work

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.util.zip.ZipInputStream

class Unpacker {
    fun unpack(archive: File, destination: File) {
        destination.mkdirs()
        when (archive.extension.lowercase()) {
            "zip" -> unpackZip(archive, destination)
            "rar" -> unpackRar(archive, destination)
            else -> error("Unsupported archive: ${archive.extension}")
        }
    }

    private fun unpackZip(archive: File, destination: File) {
        ZipInputStream(archive.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = safeTarget(destination, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use(input::copyTo)
                }
            }
        }
    }

    private fun unpackRar(archive: File, destination: File) {
        Archive(archive).use { rar ->
            var header: FileHeader? = rar.nextFileHeader()
            while (header != null) {
                val current = requireNotNull(header)
                val target = safeTarget(destination, current.fileName ?: "")
                if (current.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> rar.extractFile(current, output) }
                }
                header = rar.nextFileHeader()
            }
        }
    }

    private fun safeTarget(destination: File, entryName: String): File {
        require(!entryName.startsWith("/") && !entryName.startsWith("\\")) {
            "Archive entry must be relative: $entryName"
        }
        val root = destination.canonicalFile
        val target = File(root, entryName).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "Archive entry escapes destination: $entryName"
        }
        return target
    }
}
