package my.edu.um.rainready

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.Feed
import my.edu.um.rainready.data.LiveZoneMapping
import my.edu.um.rainready.data.OpenMeteo
import my.edu.um.rainready.data.RainReading
import my.edu.um.rainready.data.ReportDoc
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.data.ZoneDoc
import my.edu.um.rainready.data.ZoneFeed
import my.edu.um.rainready.data.ZoneRulesRepository
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Live data: Firestore rows and the Open-Meteo forecast turned into Jingying's rule inputs and the map pins. */
class LiveZoneDataTest {
    private val now = 1_795_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val campus by lazy {
        fun read(name: String) = File("src/main/assets/$name").readText()
        CampusData.parse(read("buildings.json"), read("paths.geojson"), read("zones.geojson"))
    }

    private fun report(id: String, zone: String, status: String, age: Long, fixedAt: Long? = null, lat: Double? = 3.12) =
        ReportDoc(id, zone, "flood", status, now - age, fixedAt, lat, 101.65)

    private fun server(vararg rows: ReportDoc) = Feed(rows.toList(), fromServer = true)
    private val noZones = Feed(emptyList<ZoneDoc>(), fromServer = true)

    private fun repoFor(zones: Feed<ZoneDoc>, reports: Feed<ReportDoc>, rain: RainReading? = null) = ZoneRulesRepository(
        feed = flowOf(LiveZoneMapping.feed(null, campus.groundLevel, zones, reports, rain, now)),
        zoneLabel = campus.zoneLabel,
        weatherLine = "",
        clock = { now },
    )

    @Test
    fun everyZoneGetsItsOverrideReportsAndTheRain() {
        val inputs = LiveZoneMapping.zoneInputs(
            campus.groundLevel,
            zones = listOf(ZoneDoc("Z6", "red", now + hour)),
            reports = listOf(report("r1", "Z1", "confirmed", 10 * minute), report("r2", "Z1", "unconfirmed", 5 * minute)),
            rain = RainReading(8.0, now - 2 * minute),
        )
        assertEquals((1..10).map { "Z$it" }, inputs.map { it.zoneId })
        val z6 = inputs.single { it.zoneId == "Z6" }
        assertEquals("red", z6.staffOverride)
        assertEquals(now + hour, z6.staffOverrideUntilMillis)
        assertEquals(2, inputs.single { it.zoneId == "Z1" }.reports.size)
        assertTrue(inputs.all { it.rainMmPerHour == 8.0 && it.weatherFetchedAtMillis == now - 2 * minute })
        assertEquals(campus.groundLevel["Z6"], z6.groundLevel)
    }

    @Test
    fun onlyAConfirmedRecentUnfixedReportOrAStaffOverrideTurnsAZoneRed() = runBlocking {
        val map = repoFor(
            zones = Feed(listOf(ZoneDoc("Z6", "red", now + hour), ZoneDoc("Z2", "", null)), fromServer = true),
            reports = server(
                report("r1", "Z1", "confirmed", 10 * minute),           // red
                report("r3", "Z3", "unconfirmed", 10 * minute),         // a grey pin only
                report("r4", "Z4", "fixed", 30 * minute, fixedAt = now - minute),
                report("r8", "Z8", "confirmed", 7 * hour),              // more than 6 hours old
            ),
        ).map.first()
        assertEquals(ZoneStatus.FLOODED, map.zoneStatus["Z1"])
        assertEquals(ZoneStatus.FLOODED, map.zoneStatus["Z6"])
        for (zone in listOf("Z2", "Z3", "Z4", "Z8")) assertEquals(zone, ZoneStatus.CLEAR, map.zoneStatus[zone])
        assertEquals("flood", map.causes["Z1"]?.category)
        assertEquals(listOf("r1" to true, "r3" to false), map.reports.map { it.id to it.confirmed })
    }

    @Test
    fun heavyRainOnLowGroundIsAYellowFromTheForecast() = runBlocking {
        val low = campus.groundLevel.filterValues { it == "low" }.keys
        val map = repoFor(noZones, server(), RainReading(8.0, now - 2 * minute)).map.first()
        assertTrue(low.isNotEmpty())
        for (zone in low) assertEquals(zone, ZoneStatus.CAUTION, map.zoneStatus[zone])
        assertEquals(8.0, repoFor(noZones, server(), RainReading(8.0, now)).home.first().rainNextHourMm!!, 0.0)
    }

    @Test
    fun aConfirmedReportChangesTheVerdict() = runBlocking {
        val before = SampleRouteChecker(campus, repoFor(noZones, server()).map)
        assertEquals(Verdict.GO, before.check("KK8", "FSKTM").first()?.verdict)
        val after = SampleRouteChecker(campus, repoFor(noZones, server(report("r1", "Z1", "confirmed", minute))).map)
        assertEquals(Verdict.NO_SAFE_ROUTE, after.check("KK8", "FSKTM").first()?.verdict)
    }

    @Test
    fun pinsAreOnlyActiveReportsWithAPosition() {
        val pins = LiveZoneMapping.pins(
            listOf(
                report("ok", "Z1", "unconfirmed", hour),
                report("old", "Z1", "unconfirmed", 6 * hour),
                report("fixed", "Z1", "fixed", hour, fixedAt = now - minute),
                report("noPlace", "Z1", "confirmed", hour, lat = null),
                ReportDoc("noTime", "Z1", "flood", "confirmed", null, null, 3.12, 101.65),
            ),
            now,
        )
        assertEquals(listOf("ok"), pins.map { it.id })
    }

    @Test
    fun dataCountsAsCurrentOnlyWhileBothListenersHaveServerData() {
        fun feed(previous: ZoneFeed?, zones: Boolean, reports: Boolean, at: Long) = LiveZoneMapping.feed(
            previous, campus.groundLevel, Feed(emptyList(), zones), Feed(emptyList(), reports), null, at,
        )
        val neverOnline = feed(null, zones = true, reports = false, at = now)
        assertEquals(0L, neverOnline.updatedAtMillis)
        val online = feed(neverOnline, zones = true, reports = true, at = now)
        assertEquals(now, online.updatedAtMillis)
        val offline = feed(online, zones = false, reports = true, at = now + 20 * minute)
        assertEquals("keeps the last time it was current", now, offline.updatedAtMillis)
        assertEquals(now + 30 * minute, feed(offline, zones = true, reports = true, at = now + 30 * minute).updatedAtMillis)
    }

    @Test
    fun neverCurrentDataShowsAsTimeZeroOnTheScreens() = runBlocking {
        val repo = ZoneRulesRepository(
            feed = flowOf(LiveZoneMapping.feed(null, campus.groundLevel, Feed(emptyList(), false), Feed(emptyList(), false), null, now)),
            zoneLabel = campus.zoneLabel, weatherLine = "", clock = { now },
        )
        assertEquals(0L, repo.map.first().updatedAtMillis)
        assertEquals(0L, repo.home.first().updatedAtMillis)
        assertNull(repo.home.first().rainNextHourMm)
    }

    @Test
    fun forecastUsesTheHoursOverlappingTheNext60Minutes() {
        // Interval ends at 10:00, 11:00, 12:00 and 13:00 (unix seconds); each value is the hour before it.
        val tenOClock = 1_795_000_000L - 1_795_000_000L % 3600
        fun json(vararg mm: String) =
            """{"hourly":{"time":[${(0..3).joinToString { (tenOClock + it * 3600).toString() }}],"precipitation":[${mm.joinToString()}]}}"""
        val half = (tenOClock + 1800) * 1000   // 10:30: the 11:00 and 12:00 intervals overlap the next hour
        assertEquals(9.0, OpenMeteo.mmNextHour(json("20", "4", "9", "30"), half)!!, 0.0)
        val exact = tenOClock * 1000           // 10:00: only the 11:00 interval (the one that just ended is left out)
        assertEquals(4.0, OpenMeteo.mmNextHour(json("20", "4", "9", "30"), exact)!!, 0.0)
        assertEquals(4.0, OpenMeteo.mmNextHour(json("20", "4", "null", "30"), half)!!, 0.0)
        assertNull(OpenMeteo.mmNextHour(json("1", "null", "null", "1"), half))
        assertNull(OpenMeteo.mmNextHour("""{"error":true}""", half))
    }
}
