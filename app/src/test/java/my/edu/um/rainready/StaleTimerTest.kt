package my.edu.um.rainready

import my.edu.um.rainready.model.STALE_AFTER_MILLIS
import my.edu.um.rainready.model.millisUntilStale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wait the stale-banner timer sleeps before it checks again. */
class StaleTimerTest {
    private val updated = 1_000_000L

    @Test
    fun freshData_waitsUntilJustAfterFifteenMinutes() {
        assertEquals(STALE_AFTER_MILLIS + 1, millisUntilStale(updated, updated))
        assertEquals(1L, millisUntilStale(updated, updated + STALE_AFTER_MILLIS))
    }

    @Test
    fun staleData_waitsZero() {
        assertEquals(0L, millisUntilStale(updated, updated + STALE_AFTER_MILLIS + 1))
        assertEquals(0L, millisUntilStale(updated, updated + 3 * STALE_AFTER_MILLIS))
    }
}
