package app.tplay.ui.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.domain.Job
import app.tplay.domain.JobRepository
import app.tplay.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ActiveViewModel @Inject constructor(
    private val jobs: JobRepository,
) : ViewModel() {
    val list: StateFlow<List<Job>> = jobs.jobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(job: Job) {
        viewModelScope.launch { jobs.cancel(job.id) }
    }

    /** Fetches the torrent onto the phone itself, for releases Real-Debrid will not serve. */
    fun downloadDirect(job: Job) {
        viewModelScope.launch { jobs.downloadDirect(job.id) }
    }

    fun retry(job: Job) {
        viewModelScope.launch { jobs.enqueue(job.magnet, job.subtitleUrl, job.parentFolder, true) }
    }
}
