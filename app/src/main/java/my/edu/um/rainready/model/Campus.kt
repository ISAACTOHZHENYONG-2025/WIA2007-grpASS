package my.edu.um.rainready.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A campus building from the buildings list (assets/buildings.json, from Jingying's sheet). */
data class Building(
    val id: String,
    val name: String,
    val nameMs: String,
    /** The zone the building is in ("Z1" to "Z10"). */
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
)

/** A point on the map. */
data class LonLat(val lon: Double, val lat: Double)

/**
 * A hand-made campus path between two buildings (FR2.3), from assets/paths.geojson.
 * Paths can be walked both ways, so [fromId] and [toId] only say how the line is stored.
 */
data class CampusPath(
    val id: String,
    val fromId: String,
    val toId: String,
    /** The zones the path passes, in walking order from [fromId]. */
    val zoneIds: List<String>,
    val walkingMinutes: Int,
    /** True for the path most people take between the two buildings. */
    val usual: Boolean,
    /** The line on the map, from [fromId] to [toId]. */
    val line: List<LonLat>,
)

/** True when the path joins buildings [a] and [b], in either direction. */
fun CampusPath.joins(a: String, b: String): Boolean =
    (fromId == a && toId == b) || (fromId == b && toId == a)

/** The far end of the path when walking from [startId], or null if the path does not start or end there. */
fun CampusPath.otherEnd(startId: String): String? = when (startId) {
    fromId -> toId
    toId -> fromId
    else -> null
}

/** Great-circle distance in metres. */
fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** How far from a building a phone can be and still count as "near" it. Further away is off campus. */
const val NEAR_BUILDING_METRES = 600.0

/**
 * The building nearest to the phone's position (FR2.3: the start is snapped to the nearest building),
 * or null when the nearest one is more than [maxMetres] away, so the user picks the start by hand.
 */
fun List<Building>.nearestTo(latitude: Double, longitude: Double, maxMetres: Double = NEAR_BUILDING_METRES): Building? =
    map { it to distanceMetres(latitude, longitude, it.latitude, it.longitude) }
        .filter { it.second <= maxMetres }
        .minByOrNull { it.second }
        ?.first
