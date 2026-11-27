package my.edu.um.rainready

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.data.ZoneRulesRepository
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.zone.ReportInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Jingying's zone colour rules feeding the map, Home and the route check (one source for all three). */
class ZoneRulesRepositoryTest {
    private val now = 1_700_000_000_000L
    private val campus by lazy {
        fun read(name: String) = File("src/main/assets/$name").readText()
        CampusData.parse(read("buildings.json"), read("paths.geojson"), read("zones.geojson"))
    }
    private val inputs by lazy { MutableStateFlow(ZoneRulesRepository.sampleInputs(campus.groundLevel, now)) }
    private val repo by lazy {
        ZoneRulesRepository(inputs, flowOf(emptyList()), campus.zoneLabel, weatherLine = "", clock = { now })
    }

    @Test
    fun sampleInputsGiveTheDemoColours() = runBlocking {
        val map = repo.map.first()
        assertEquals(ZoneStatus.FLOODED, map.zoneStatus["Z7"])
        assertEquals(ZoneStatus.FLOODED, map.zoneStatus["Z5"])
        assertEquals(ZoneStatus.CAUTION, map.zoneStatus["Z6"]) // heavy rain on low ground
        assertEquals(10, map.zoneStatus.size)
        assertEquals(7, map.zoneStatus.values.count { it == ZoneStatus.CLEAR })
        assertEquals("flood", map.causes["Z7"]?.category)
        assertEquals("fallen tree", map.causes["Z5"]?.category)
        assertNull("an unconfirmed report is not a cause", map.causes["Z6"])
        assertEquals(now, map.updatedAtMillis)
    }

    @Test
    fun homeListsEveryZoneMostSeriousFirst() = runBlocking {
        val home = repo.home.first()
        assertEquals(10, home.zones.size)
        assertEquals(listOf("Z5", "Z7", "Z6"), home.zones.take(3).map { it.id })
        assertEquals("Engineering", home.zones[1].name)
        assertEquals("Flood confirmed", home.zones[1].detail)
    }

    @Test
    fun routeCheckGivesAllFourVerdictsFromTheRules() = runBlocking {
        val checker = SampleRouteChecker(campus, repo.map)
        assertEquals(Verdict.GO, checker.check("KK8", "FSKTM").first()?.verdict)
        assertEquals(Verdict.GO_TAKE_CARE, checker.check("LRT", "MAINLIB").first()?.verdict)
        assertEquals(Verdict.USE_SAFER_ROUTE, checker.check("KK3", "FASS").first()?.verdict)
        assertEquals(Verdict.NO_SAFE_ROUTE, checker.check("KK12", "FSCI").first()?.verdict)
        assertEquals("fallen tree", checker.check("KK3", "FASS").first()?.causeCategory)
    }

    @Test
    fun aNewlyConfirmedReportUpdatesTheMapAndTheVerdict() = runBlocking {
        val checker = SampleRouteChecker(campus, repo.map)
        // Keep watching the trip from KK8 to FSKTM while a flood is confirmed in Z1 (FSKTM's zone).
        val seen = mutableListOf<Verdict?>()
        val firstSeen = CompletableDeferred<Unit>()
        withTimeout(5_000) {
            val watching = launch {
                checker.check("KK8", "FSKTM").take(2).collect {
                    seen += it?.verdict
                    firstSeen.complete(Unit)
                }
            }
            firstSeen.await() // the watcher is subscribed before the report arrives
            inputs.value = inputs.value.map {
                if (it.zoneId == "Z1") it.copy(reports = listOf(ReportInput("confirmed", "flood", now - 60_000L, null))) else it
            }
            watching.join()
        }
        assertEquals(listOf(Verdict.GO, Verdict.NO_SAFE_ROUTE), seen)
        assertEquals(ZoneStatus.FLOODED, repo.map.first().zoneStatus["Z1"])
    }
}
