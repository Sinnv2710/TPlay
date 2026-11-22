package app.tplay.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Cells of a month grid starting on Monday. Null cells pad the first and last week. */
fun monthGrid(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = MutableList<LocalDate?>(leading) { null }
    for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
    while (cells.size % 7 != 0) cells += null
    return cells
}

enum class EntryKind { WATCHED, ADDED }

data class DayEntry(val titleId: Long, val name: String, val posterUrl: String?, val kind: EntryKind)

/** Local calendar day (epoch day in the phone's time zone) of a timestamp. */
fun localEpochDay(millis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/** Per calendar day: titles watched that day and titles added (downloaded or streamed) that day. */
fun buildDayEntries(
    titles: List<app.tplay.domain.Title>,
    history: List<app.tplay.domain.HistoryDay>,
): Map<Long, List<DayEntry>> {
    val byId = titles.associateBy { it.id }
    val result = mutableMapOf<Long, MutableList<DayEntry>>()
    for (day in history) {
        for (id in day.titleIds) {
            val title = byId[id] ?: continue
            result.getOrPut(day.epochDay) { mutableListOf() }
                .add(DayEntry(id, title.name, title.posterUrl, EntryKind.WATCHED))
        }
    }
    for (title in titles) {
        val day = localEpochDay(title.createdAt)
        result.getOrPut(day) { mutableListOf() }
            .add(DayEntry(title.id, title.name, title.posterUrl, EntryKind.ADDED))
    }
    return result
}
