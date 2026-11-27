package my.edu.um.rainready

import kotlinx.coroutines.flow.flowOf
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.SampleMapRepository
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.distanceMetres
import my.edu.um.rainready.model.nearestTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The route-check data in assets and the stand-in FR2.3 rules. */
class RouteCheckDataTest {
    private val campus by lazy {
        fun read(name: String) = File("src/main/assets/$name").readText()
        CampusData.parse(read("buildings.json"), read("paths.geojson"), read("zones.geojson"))
    }
    private val map = SampleMapRepository.rainyDay(now = 0L)
    private val checker by lazy { SampleRouteChecker(campus, flowOf(map)) }

    @Test
    fun buildingsAndPathsMatchTheSheet() {
        assertEquals(35, campus.buildings.size)
        assertEquals(campus.buildings.size, campus.buildings.map { it.id }.toSet().size)
        assertEquals((1..14).map { "P%02d".format(it) }, campus.paths.map { it.id })
        val zones = (1..10).map { "Z$it" }.toSet()
        for (p in campus.paths) {
            assertTrue("${p.id} from", campus.building(p.fromId) != null)
            assertTrue("${p.id} to", campus.building(p.toId) != null)
            assertTrue("${p.id} zones", zones.containsAll(p.zoneIds))
        }
        assertEquals(zones, campus.groundLevel.keys)
    }

    @Test
    fun everyPathLineStartsAndEndsAtItsBuildings() {
        for (p in campus.paths) {
            val from = campus.building(p.fromId)!!
            val to = campus.building(p.toId)!!
            val first = p.line.first()
            val last = p.line.last()
            assertTrue("${p.id} start", distanceMetres(first.lat, first.lon, from.latitude, from.longitude) < 150)
            assertTrue("${p.id} end", distanceMetres(last.lat, last.lon, to.latitude, to.longitude) < 150)
        }
    }

    @Test
    fun phoneSnapsToTheNearestBuildingOnlyOnCampus() {
        // A few metres from Za'ba Residential College (KK7).
        assertEquals("KK7", campus.buildings.nearestTo(3.12640, 101.65050)?.id)
        // Mid Valley, about 3 km away: off campus, so the user picks the start by hand.
        assertNull(campus.buildings.nearestTo(3.1180, 101.6770))
    }

    @Test
    fun destinationsAreThoseWithACheckedPath() {
        assertEquals(setOf("FSKTM"), campus.destinationsFrom("KK8").map { it.id }.toSet())
        assertEquals(setOf("KK7", "LRT", "FENG"), campus.destinationsFrom("MAINLIB").map { it.id }.toSet())
        assertTrue(campus.destinationsFrom("KK13").isEmpty())
    }

    @Test
    fun allFourVerdictsAppearInTheSampleData() {
        assertEquals(Verdict.GO, checker.verdictFor(map, "KK8", "FSKTM")?.verdict)
        assertEquals(Verdict.GO_TAKE_CARE, checker.verdictFor(map, "LRT", "MAINLIB")?.verdict)
        assertEquals(Verdict.USE_SAFER_ROUTE, checker.verdictFor(map, "KK3", "FASS")?.verdict)
        assertEquals(Verdict.NO_SAFE_ROUTE, checker.verdictFor(map, "KK12", "FSCI")?.verdict)
    }

    @Test
    fun saferRouteAvoidsTheFloodedZoneAndCountsTheExtraMinutes() {
        val v = checker.verdictFor(map, "KK3", "FASS")!!
        assertEquals("P03", v.usualPath.id)
        assertEquals("P04", v.recommendedPath?.id)
        assertEquals(4, v.extraMinutes)
        assertEquals("Z5", v.causeZoneId)
        assertEquals("fallen tree", v.causeCategory)
        assertEquals(listOf("P03"), v.otherPaths.map { it.id })
        // The same trip walked the other way gives the same answer.
        assertEquals("P04", checker.verdictFor(map, "FASS", "KK3")?.recommendedPath?.id)
    }

    @Test
    fun cautionOnLowGroundAddsLeaveEarly() {
        val v = checker.verdictFor(map, "LRT", "MAINLIB")!!
        assertTrue(v.leaveEarly)
        assertEquals("Z6", v.causeZoneId)
        assertFalse(checker.verdictFor(map, "KK8", "FSKTM")!!.leaveEarly)
    }

    @Test
    fun noSafeRouteHasNothingToRecommend() {
        val v = checker.verdictFor(map, "KK2", "FENG")!!
        assertEquals(Verdict.NO_SAFE_ROUTE, v.verdict)
        assertNull(v.recommendedPath)
        assertEquals(setOf("P08", "P09"), v.otherPaths.map { it.id }.toSet())
    }

    @Test
    fun tripWithoutAPathHasNoVerdict() {
        assertNull(checker.verdictFor(map, "KK13", "FSKTM"))
    }
}
