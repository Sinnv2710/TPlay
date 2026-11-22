package app.tplay.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.domain.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CalendarViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow<LocalDate?>(LocalDate.now())

    val entries: StateFlow<Map<Long, List<DayEntry>>> =
        combine(library.historyByDay(), library.titles()) { days, titles -> buildDayEntries(titles, days) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }
}
