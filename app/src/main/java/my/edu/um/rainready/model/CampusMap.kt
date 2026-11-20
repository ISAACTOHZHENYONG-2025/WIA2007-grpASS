package my.edu.um.rainready.model

/** A flood or fallen-tree report shown as a pin (FR2.2): grey until confirmed, then red. */
data class ReportPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val confirmed: Boolean,
)

/** Everything the campus map needs besides the zone shapes (those ship with the app in assets/zones.geojson). */
data class MapState(
    /** Status per zone id ("Z1" to "Z10"). A zone missing from the map is shown as Clear. */
    val zoneStatus: Map<String, ZoneStatus>,
    val reports: List<ReportPin>,
    /** When the data was last refreshed, in epoch milliseconds. */
    val updatedAtMillis: Long,
)

/** True when the map data was last refreshed more than 15 minutes before [nowMillis] (FR2.1). */
fun MapState.isStale(nowMillis: Long): Boolean = nowMillis - updatedAtMillis > STALE_AFTER_MILLIS
