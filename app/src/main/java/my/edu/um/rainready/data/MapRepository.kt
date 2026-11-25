package my.edu.um.rainready.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.ReportPin
import my.edu.um.rainready.model.ZoneStatus

/**
 * Where the campus map gets zone statuses and report pins. A real repository only passes on each
 * zone's colour: zoneStatusOf("green" / "yellow" / "red") turns it into a status, and the map
 * decides how that status is drawn.
 */
interface MapRepository {
    val map: Flow<MapState>
}

/**
 * Sample data that matches the Home sample (Engineering flooded, Main Library and DTC on caution),
 * used until Jingying's zone colour rules and the reports collection on Firestore are connected.
 * Science is flooded too, so the sample route check can show all four verdicts.
 * Swap it for the real repository in RainReadyApp; the map screen does not need to change.
 */
class SampleMapRepository : MapRepository {
    override val map: Flow<MapState> = flowOf(rainyDay())

    companion object {
        fun rainyDay(now: Long = System.currentTimeMillis()) = MapState(
            zoneStatus = mapOf("Z7" to ZoneStatus.FLOODED, "Z5" to ZoneStatus.FLOODED, "Z6" to ZoneStatus.CAUTION),
            reports = listOf(
                // Positions are the building pins from the draft building list.
                ReportPin("r1", latitude = 3.11785, longitude = 101.65534, confirmed = true),   // Faculty of Engineering
                ReportPin("r2", latitude = 3.11586, longitude = 101.65530, confirmed = true),   // Faculty of Built Environment
                ReportPin("r3", latitude = 3.12135, longitude = 101.65492, confirmed = false),  // Kompleks Perdanasiswa
                ReportPin("r4", latitude = 3.12190, longitude = 101.65391, confirmed = true),   // Faculty of Science
            ),
            updatedAtMillis = now,
        )

        /** What the sample reports in the flooded zones are, for the route-check reason line. */
        val causes = mapOf(
            "Z7" to SampleCause("flood", confirmations = 3),
            "Z5" to SampleCause("fallen tree", confirmations = 3),
        )
    }
}
