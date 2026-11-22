package app.tplay.ui.browse

import androidx.lifecycle.ViewModel
import app.tplay.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Remembers the last page so switching tabs does not lose your place. */
@HiltViewModel
class BrowseViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private var last: String? = null

    var lastUrl: String
        get() = last ?: startPage()
        set(value) {
            last = value
        }

    fun startPage(): String = startUrl(runBlocking { settings.browseStartPage().first() })
}
