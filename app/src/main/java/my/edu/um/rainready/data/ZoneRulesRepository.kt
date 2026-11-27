package my.edu.um.rainready.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import my.edu.um.rainready.model.HomeState
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.ReportPin
import my.edu.um.rainready.model.ZoneCause
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.model.ZoneSummary
import my.edu.um.rainready.model.zoneStatusOf
import my.edu.um.rainready.zone.ReportInput
import my.edu.um.rainready.zone.ZoneColour
import my.edu.um.rainready.zone.ZoneColourRules
import my.edu.um.rainready.zone.ZoneInput
import my.edu.um.rainready.zone.ZoneState

/** What the rules run on: every zone's inputs and the report pins, plus (live data only) how current it is and the rain. */
data class ZoneFeed(
    val inputs: List<ZoneInput>,
    val pins: List<ReportPin>,
    /** When the data was last known to be current (0 = never); null means the time the rules ran (sample data). */
    val updatedAtMillis: Long? = null,
    /** Rain forecast for the next hour, for Home's weather line. */
    val rainNextHourMm: Double? = null,
)

/**
 * Runs Jingying's [ZoneColourRules] over every zone's inputs (reports, staff override, rain) and gives
 * the map, Home and the route check the same zone colours, so they never disagree. Each run gets
 * the zone's previous state, as the rules expect when the weather data is old.
 *
 * [live] reads the inputs from Firestore and Open-Meteo; [sample] builds inputs that give the demo
 * colours. Colours, symbols and outlines stay on the screens: this only passes on red, yellow or
 * green per zone. With a [scope], one run of the rules is shared by every screen (one set of
 * Firestore listeners), and stops 5 seconds after the last screen stops watching.
 */
class ZoneRulesRepository(
    feed: Flow<ZoneFeed>,
    private val zoneLabel: Map<String, String>,
    private val weatherLine: String,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = null,
) : MapRepository, HomeRepository {

    constructor(
        inputs: Flow<List<ZoneInput>>,
        pins: Flow<List<ReportPin>>,
        zoneLabel: Map<String, String>,
        weatherLine: String,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(combine(inputs, pins) { i, p -> ZoneFeed(i, p) }, zoneLabel, weatherLine, clock)

    private val runs: Flow<Pair<ZoneFeed, List<ZoneState>>> = feed
        .runningFold(null as Pair<ZoneFeed, List<ZoneState>>?) { previous, f ->
            val before = previous?.second.orEmpty().associateBy { it.zoneId }
            val now = clock()
            f to f.inputs.map { ZoneColourRules.compute(it, now, before[it.zoneId]) }
        }
        .filterNotNull()
        .let { if (scope != null) it.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1) else it }

    /** The latest state of every zone. */
    val states: Flow<List<ZoneState>> = runs.map { it.second }

    override val map: Flow<MapState> = runs.map { (f, s) ->
        toMapState(s, f.pins).let { m -> f.updatedAtMillis?.let { m.copy(updatedAtMillis = it) } ?: m }
    }

    override val home: Flow<HomeState> = runs.map { (f, s) ->
        toHomeState(s, zoneLabel, weatherLine)
            .copy(rainNextHourMm = f.rainNextHourMm)
            .let { h -> f.updatedAtMillis?.let { h.copy(updatedAtMillis = it) } ?: h }
    }

    companion object {
        private const val MINUTE = 60_000L

        fun ZoneColour.toZoneStatus(): ZoneStatus = zoneStatusOf(name) ?: ZoneStatus.CLEAR

        fun toMapState(states: List<ZoneState>, pins: List<ReportPin>) = MapState(
            zoneStatus = states.associate { it.zoneId to it.colour.toZoneStatus() },
            reports = pins,
            updatedAtMillis = states.maxOfOrNull { it.computedAtMillis } ?: 0L,
            causes = states.mapNotNull { s -> s.reportCategory?.let { s.zoneId to ZoneCause(it) } }.toMap(),
        )

        /** Every zone for Home, most serious first (Home filters to the followed zones itself). */
        fun toHomeState(states: List<ZoneState>, zoneLabel: Map<String, String>, weatherLine: String) = HomeState(
            weatherLine = weatherLine,
            zones = states
                .map { ZoneSummary(it.zoneId, zoneLabel[it.zoneId] ?: it.zoneId, it.message, it.colour.toZoneStatus()) }
                .sortedWith(compareByDescending<ZoneSummary> { it.status }.thenBy { it.id.drop(1).toIntOrNull() ?: 0 }),
            updatedAtMillis = states.maxOfOrNull { it.computedAtMillis } ?: 0L,
        )

        /**
         * The real data: staff overrides and reports from Firestore, and the campus rain forecast from
         * Open-Meteo. The rules run again on every change and once a minute, so a report that turns
         * 6 hours old, an override that ends or a forecast that goes stale shows without new data.
         */
        fun live(
            campus: CampusData,
            db: FirebaseFirestore,
            scope: CoroutineScope,
            rain: Flow<RainReading?> = OpenMeteo.readings(),
            clock: () -> Long = System::currentTimeMillis,
            tickMs: Long = MINUTE,
        ): ZoneRulesRepository {
            val firestore = FirestoreZoneFeed(db, clock)
            val ticks = flow { while (true) { emit(Unit); delay(tickMs) } }
            val feed = combine(firestore.zones, firestore.reports, rain, ticks) { z, r, w, _ -> Triple(z, r, w) }
                .runningFold(null as ZoneFeed?) { previous, (z, r, w) ->
                    LiveZoneMapping.feed(previous, campus.groundLevel, z, r, w, clock())
                }
                .filterNotNull()
            return ZoneRulesRepository(feed, campus.zoneLabel, weatherLine = "", clock = clock, scope = scope)
        }

        /**
         * Inputs for the demo: a confirmed flood in Engineering (Z7), a confirmed fallen tree in
         * Science and APM (Z5), heavy rain on the low ground of Main Library and DTC (Z6) with an
         * unconfirmed report there, and light rain elsewhere. The rules then make Z7 and Z5 red,
         * Z6 yellow and the rest green, which gives one route-check trip for each verdict.
         */
        fun sampleInputs(groundLevel: Map<String, String>, now: Long): List<ZoneInput> = (1..10).map { n ->
            val id = "Z$n"
            ZoneInput(
                zoneId = id,
                groundLevel = groundLevel[id],
                staffOverride = null,
                staffOverrideUntilMillis = null,
                reports = when (id) {
                    "Z7" -> listOf(ReportInput("confirmed", "flood", now - 25 * MINUTE, null))
                    "Z5" -> listOf(ReportInput("confirmed", "fallen tree", now - 40 * MINUTE, null))
                    "Z6" -> listOf(ReportInput("unconfirmed", "flood", now - 10 * MINUTE, null))
                    else -> emptyList()
                },
                rainMmPerHour = if (id == "Z6") 8.0 else 1.0,
                weatherFetchedAtMillis = now - 5 * MINUTE,
            )
        }

        /** The demo repository: [sampleInputs] through the real rules, with the sample pins. */
        fun sample(campus: CampusData, clock: () -> Long = System::currentTimeMillis) = ZoneRulesRepository(
            inputs = flow { emit(sampleInputs(campus.groundLevel, clock())) },
            pins = flowOf(SampleMapRepository.samplePins),
            zoneLabel = campus.zoneLabel,
            weatherLine = SampleHomeRepository.rainyDay(0L).weatherLine,
            clock = clock,
        )
    }
}
