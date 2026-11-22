package app.tplay.ui.common

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

object FolderPicker {
    /** Converts a document tree uri such as primary:Movies/TPlay to a file path, or null if unknown. */
    fun treeToPath(uri: Uri): String? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val rest = id.substringAfter(':', "")
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (rest.isEmpty()) root else "$root/$rest"
    }

    @Composable
    fun rememberLauncher(onPicked: (String) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(::treeToPath)?.let(onPicked)
        }
        return { launcher.launch(null) }
    }
}
