package my.edu.um.rainready

import my.edu.um.rainready.model.HomeState
import my.edu.um.rainready.model.STALE_AFTER_MILLIS
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.model.ZoneSummary
import my.edu.um.rainready.model.isStale
import my.edu.um.rainready.model.worstStatus
import my.edu.um.rainready.model.zoneStatusOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules behind the Home status banner. */
class ZoneRulesTest {
    private fun zone(status: ZoneStatus) = ZoneSummary(status.name, status.name, "", status)

    @Test
    fun worstStatus_floodedBeatsCautionBeatsClear() {
        assertEquals(ZoneStatus.FLOODED, listOf(zone(ZoneStatus.CLEAR), zone(ZoneStatus.FLOODED), zone(ZoneStatus.CAUTION)).worstStatus())
        assertEquals(ZoneStatus.CAUTION, listOf(zone(ZoneStatus.CLEAR), zone(ZoneStatus.CAUTION)).worstStatus())
        assertEquals(ZoneStatus.CLEAR, listOf(zone(ZoneStatus.CLEAR)).worstStatus())
    }

    @Test
    fun worstStatus_noZonesIsClear() {
        assertEquals(ZoneStatus.CLEAR, emptyList<ZoneSummary>().worstStatus())
    }

    @Test
    fun zoneStatusOf_readsTheDataColours() {
        assertEquals(ZoneStatus.CLEAR, zoneStatusOf("green"))
        assertEquals(ZoneStatus.CAUTION, zoneStatusOf("YELLOW"))
        assertEquals(ZoneStatus.FLOODED, zoneStatusOf(" Red "))
        assertNull(zoneStatusOf(""))
        assertNull(zoneStatusOf(null))
        assertNull(zoneStatusOf("blue"))
    }

    @Test
    fun isStale_onlyAfterFifteenMinutes() {
        val state = HomeState(weatherLine = "", zones = emptyList(), updatedAtMillis = 1_000_000L)
        assertFalse(state.isStale(1_000_000L))
        assertFalse(state.isStale(1_000_000L + STALE_AFTER_MILLIS))
        assertTrue(state.isStale(1_000_000L + STALE_AFTER_MILLIS + 1))
    }
}
