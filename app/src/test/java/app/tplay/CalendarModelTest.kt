package app.tplay

import app.tplay.ui.calendar.monthGrid
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarModelTest {
    @Test
    fun gridIsWholeWeeks() {
        listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 11), YearMonth.of(2024, 2)).forEach {
            assertEquals(0, monthGrid(it).size % 7)
        }
    }

    @Test
    fun novemberTwentyTwentySixStartsOnSunday() {
        val grid = monthGrid(YearMonth.of(2026, 11))
        assertEquals(6, grid.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), grid[6])
    }

    @Test
    fun leapFebruaryHasTwentyNineDays() {
        assertEquals(29, monthGrid(YearMonth.of(2024, 2)).filterNotNull().size)
    }

    @Test
    fun paddingCellsAreNull() {
        assertNull(monthGrid(YearMonth.of(2026, 11)).last().takeIf { it == null })
    }
}

class DayEntriesTest {
    private fun title(id: Long, name: String, createdDay: Long) = app.tplay.domain.Title(
        id, name, 2020, "http://p/$id", "/f", 1L, createdDay * 86_400_000L, null, null, false,
    )

    @Test fun watchedAndAddedAreSeparateEntriesOnTheirDays() {
        val titles = listOf(title(1, "A", 10), title(2, "B", 12))
        val history = listOf(app.tplay.domain.HistoryDay(12, listOf(1)))
        val map = app.tplay.ui.calendar.buildDayEntries(titles, history)
        assertEquals(listOf(app.tplay.ui.calendar.EntryKind.ADDED), map.getValue(10).map { it.kind })
        assertEquals(setOf("A", "B"), map.getValue(12).map { it.name }.toSet())
        assertEquals(2, map.getValue(12).size)
    }

    @Test fun historyForDeletedTitleIsIgnored() {
        val map = app.tplay.ui.calendar.buildDayEntries(emptyList(), listOf(app.tplay.domain.HistoryDay(5, listOf(9))))
        assertEquals(true, map.isEmpty())
    }

    @Test fun postersTravelWithTheEntry() {
        val map = app.tplay.ui.calendar.buildDayEntries(listOf(title(3, "C", 7)), emptyList())
        assertEquals("http://p/3", map.getValue(7).single().posterUrl)
    }
}

class LocalDayTest {
    private val dubai = java.time.ZoneId.of("Asia/Dubai")
    private val utc = java.time.ZoneOffset.UTC
    private val lateEveningUtc = java.time.LocalDate.of(2026, 11, 22)
        .atTime(20, 49)
        .toInstant(utc)
        .toEpochMilli()

    @Test fun lateEveningUtcIsNextDayInDubai() {
        val nextDay = java.time.LocalDate.of(2026, 11, 23).toEpochDay()
        assertEquals(nextDay, app.tplay.ui.calendar.localEpochDay(lateEveningUtc, dubai))
    }

    @Test fun sameInstantIsSameDayInUtc() {
        val sameDay = java.time.LocalDate.of(2026, 11, 22).toEpochDay()
        assertEquals(sameDay, app.tplay.ui.calendar.localEpochDay(lateEveningUtc, utc))
    }
}
