package app.tplay.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val nowMillis = 1_700_000_000_000L

    @Test
    fun exactSevenDaysIsCleaned() {
        val modified = nowMillis - 7 * MILLIS_PER_DAY

        assertTrue(shouldClean(modified, nowMillis, 7, false, false))
    }

    @Test
    fun almostSevenDaysIsKept() {
        val modified = nowMillis - 7 * MILLIS_PER_DAY + 1

        assertFalse(shouldClean(modified, nowMillis, 7, false, false))
    }

    @Test
    fun sixDaysAndTwentyThreeHoursIsKept() {
        val modified = nowMillis - 6 * MILLIS_PER_DAY - 23 * 60 * 60 * 1_000L

        assertFalse(shouldClean(modified, nowMillis, 7, false, false))
    }

    @Test
    fun olderThanSevenDaysIsCleaned() {
        val modified = nowMillis - 7 * MILLIS_PER_DAY - 1

        assertTrue(shouldClean(modified, nowMillis, 7, false, false))
    }

    @Test
    fun exactThirtyDaysIsCleaned() {
        val modified = nowMillis - 30 * MILLIS_PER_DAY

        assertTrue(shouldClean(modified, nowMillis, 30, false, false))
    }

    @Test
    fun justUnderThirtyDaysIsKept() {
        val modified = nowMillis - 30 * MILLIS_PER_DAY + 1

        assertFalse(shouldClean(modified, nowMillis, 30, false, false))
    }

    @Test
    fun historyIsKeptWhenKeepHistoryIsOn() {
        val modified = nowMillis - 30 * MILLIS_PER_DAY

        assertFalse(shouldClean(modified, nowMillis, 30, true, true))
    }

    @Test
    fun historyIsCleanedWhenKeepHistoryIsOff() {
        val modified = nowMillis - 30 * MILLIS_PER_DAY

        assertTrue(shouldClean(modified, nowMillis, 30, false, true))
    }
}
