package my.edu.um.rainready.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
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

/**
 * Runs Jingying's [ZoneColourRules] over every zone's inputs (reports, staff override, rain) and gives
 * the map, Home and the route check the same zone colours, so they never disagree. Each run gets
 * the zone's previous state, as the rules expect when the weather data is old.
 *
 * The inputs come from Firestore and the weather job once Jingying connects them; until then
 * [sample] builds inputs that give the demo colours. Colours, symbols and outlines stay on the
 * screens: this only passes on red, yellow or green per zone.
 */
class ZoneRulesRepository(
    inputs: Flow<List<ZoneInput>>,
    pins: Flow<List<ReportPin>>,
    private val zoneLabel: Map<String, String>,
    private val weatherLine: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : MapRepository, HomeRepository {

    /** The latest state of every zone. */
    val states: Flow<List<ZoneState>> = inputs
        .runningFold(emptyList<ZoneState>()) { previous, zones ->
            val before = previous.associateBy { it.zoneId }
            val now = clock()
            zones.map { ZoneColourRules.compute(it, now, before[it.zoneId]) }
        }
        .drop(1) // the empty starting value

    override val map: Flow<MapState> = combine(states, pins) { s, p -> toMapState(s, p) }

    override val home: Flow<HomeState> = states.map { toHomeState(it, zoneLabel, weatherLine) }

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
