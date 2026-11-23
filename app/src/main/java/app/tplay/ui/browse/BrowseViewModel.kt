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

    /** Makes the site of [url] the one Discover reads. Returns the site, or null if [url] is not a web page. */
    suspend fun useForDiscover(url: String?): String? {
        val site = siteOf(url) ?: return null
        settings.setDiscoverSite(site)
        return site
    }

    fun startPage(): String = startUrl(runBlocking { settings.browseStartPage().first() })
}
