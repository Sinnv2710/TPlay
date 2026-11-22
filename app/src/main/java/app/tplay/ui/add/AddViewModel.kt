package app.tplay.ui.add

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import app.tplay.domain.JobRepository
import app.tplay.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AddViewModel @Inject constructor(
    private val jobs: JobRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _magnet = MutableStateFlow("")
    private val _subtitle = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    private val _folder = MutableStateFlow("")

    val magnet: StateFlow<String> = _magnet.asStateFlow()
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val folder: StateFlow<String> = _folder.asStateFlow()

    init {
        viewModelScope.launch {
            settings.parentFolder().collect { _folder.value = it }
        }
    }

    fun setMagnet(value: String) { _magnet.value = value; _error.value = null }
    fun setSubtitle(value: String) { _subtitle.value = value }

    fun setParentFolder(path: String) {
        if (path.isBlank()) return
        _folder.value = path
        viewModelScope.launch { settings.setParentFolder(path) }
    }

    /** [stream] true plays from Real-Debrid without copying the file to the phone. */
    fun enqueue(stream: Boolean): Boolean {
        if (!isValidMagnet(_magnet.value)) {
            _error.value = "Enter valid magnet link"
            return false
        }
        viewModelScope.launch {
            jobs.enqueue(_magnet.value, _subtitle.value.ifBlank { null }, _folder.value, stream)
        }
        return true
    }

    companion object {
        fun isValidMagnet(value: String): Boolean =
            value.trim().startsWith("magnet:?", ignoreCase = true) &&
                value.contains("xt=urn:btih:", ignoreCase = true)
    }
}
