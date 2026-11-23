package app.tplay.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import app.tplay.domain.LibraryRepository
import app.tplay.domain.SettingsRepository
import app.tplay.player.PlaybackResolver
import app.tplay.player.PlayerLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val resolver: PlaybackResolver,
) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow<LocalDate?>(LocalDate.now())

    val entries: StateFlow<Map<Long, List<DayEntry>>> =
        combine(library.historyByDay(), library.titles()) { days, titles -> buildDayEntries(titles, days) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    /** Opens the title in the chosen player, resuming from the saved position. */
    fun play(context: Context, titleId: Long, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val title = library.titles().first().firstOrNull { it.id == titleId }
            if (title == null) {
                onMessage("This title is no longer in the library.")
                return@launch
            }
            when (val result = resolver.resolve(title)) {
                is PlaybackResolver.Result.Failed -> onMessage(result.reason)
                is PlaybackResolver.Result.Ready -> {
                    val progress = library.progress().first()[titleId]
                    val resume = if (progress == null || progress.finished) 0L else progress.positionMs
                    PlayerLauncher.launch(
                        context, title.id, title.name, result.uri, resume,
                        settings.preferredPlayer().first(), title.folderPath,
                    )
                }
            }
        }
    }
}
