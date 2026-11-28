package my.edu.um.rainready

import kotlinx.coroutines.flow.flowOf
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.SampleMapRepository
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.model.ReportPin
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.zone.ReportInput
import my.edu.um.rainready.zone.ZoneColour
import my.edu.um.rainready.zone.ZoneColourRules
import my.edu.um.rainready.zone.ZoneInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The four FR2.3 verdicts on the shared sample data, and unconfirmed pins not changing anything. */
class RouteVerdictEngineTest {
    private val campus by lazy {
        fun read(name: String) = File("src/main/assets/$name").readText()
        CampusData.parse(read("buildings.json"), read("paths.geojson"), read("zones.geojson"))
    }
    private val map = SampleMapRepository.rainyDay(now = 0L)
    private val checker by lazy { SampleRouteChecker(campus, flowOf(map)) }

    @Test
    fun case1_usualPathClear_go() {
        val v = checker.verdictFor(map, "KK8", "FSKTM")!!
        assertEquals(Verdict.GO, v.verdict)
        assertEquals("P01", v.recommendedPath?.id)
        assertEquals(0, v.extraMinutes)
        assertFalse(v.leaveEarly)
    }

    @Test
    fun case2_yellowOnUsualPath_goTakeCareAndLeaveEarly() {
        val v = checker.verdictFor(map, "LRT", "MAINLIB")!!
        assertEquals(Verdict.GO_TAKE_CARE, v.verdict)
        assertEquals("P14", v.recommendedPath?.id)
        assertEquals("Z6", v.causeZoneId)
        assertTrue(v.leaveEarly)
    }

    @Test
    fun case3_usualPathRedButSaferExists_useSaferRoute() {
        val v = checker.verdictFor(map, "KK3", "FASS")!!
        assertEquals(Verdict.USE_SAFER_ROUTE, v.verdict)
        assertEquals("P03", v.usualPath.id)
        assertEquals("P04", v.recommendedPath?.id)
        assertEquals(4, v.extraMinutes)
        assertEquals("Z5", v.causeZoneId)
        assertEquals("fallen tree", v.causeCategory)
    }

    @Test
    fun case4_everyPathRed_noSafeRoute() {
        val v = checker.verdictFor(map, "KK12", "FSCI")!!
        assertEquals(Verdict.NO_SAFE_ROUTE, v.verdict)
        assertNull(v.recommendedPath)
    }

    @Test
    fun unconfirmedGreyPin_doesNotChangeAnyVerdict() {
        val trips = listOf("KK8" to "FSKTM", "LRT" to "MAINLIB", "KK3" to "FASS", "KK12" to "FSCI")
        // A grey (unconfirmed) pin placed at KK8, on the clear route.
        val withGreyPin = map.copy(
            reports = map.reports + ReportPin("grey1", latitude = 3.12992, longitude = 101.64935, confirmed = false),
        )
        val noPins = map.copy(reports = emptyList())
        for ((from, to) in trips) {
            val expected = checker.verdictFor(map, from, to)?.verdict
            assertEquals("$from to $to with a grey pin", expected, checker.verdictFor(withGreyPin, from, to)?.verdict)
            assertEquals("$from to $to with no pins", expected, checker.verdictFor(noPins, from, to)?.verdict)
        }
    }

    @Test
    fun unconfirmedReport_doesNotTurnAZoneRed() {
        val now = 1_700_000_000_000L
        val input = ZoneInput(
            zoneId = "Z1",
            groundLevel = "medium",
            staffOverride = null,
            staffOverrideUntilMillis = null,
            reports = listOf(ReportInput("unconfirmed", "flood", now - 10 * 60_000L, null)),
            rainMmPerHour = 1.0,
            weatherFetchedAtMillis = now - 5 * 60_000L,
        )
        assertEquals(ZoneColour.GREEN, ZoneColourRules.compute(input, now).colour)
    }
}