package my.edu.um.rainready.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import my.edu.um.rainready.data.ReportRepository
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.coroutines.resume

internal data class ReportLocation(val zoneId: String, val latitude: Double, val longitude: Double)

internal suspend fun currentReportLocation(
    context: Context,
    repository: ReportRepository,
): ReportLocation {
    val manager = context.getSystemService(LocationManager::class.java)
    check(manager != null && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        "Turn on device location, then try again."
    }
    val location = withTimeoutOrNull(15_000) { currentGpsFix(context, manager) }
        ?: error("Could not get your location. Try again outdoors.")
    val latitude = location.latitude
    val longitude = location.longitude
    check(repository.isInsideUm(latitude, longitude)) {
        "You need to be on UM campus to submit a report."
    }
    val zoneId = withContext(Dispatchers.IO) {
        val zones = context.assets.open("zones.geojson").bufferedReader().use { it.readText() }
        campusZoneAt(zones, Point.fromLngLat(longitude, latitude))
    } ?: error("Your location is outside the mapped campus zones. Try again from campus.")
    return ReportLocation(zoneId, latitude, longitude)
}

// The report screen checks precise location permission before calling this function.
@SuppressLint("MissingPermission")
private suspend fun currentGpsFix(context: Context, manager: LocationManager): Location? =
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager,
            LocationManager.GPS_PROVIDER,
            cancellation,
            ContextCompat.getMainExecutor(context),
        ) { location -> continuation.resume(location) }
    }

private fun campusZoneAt(geoJson: String, point: Point): String? =
    FeatureCollection.fromJson(geoJson).features().orEmpty().firstOrNull { feature ->
        when (val geometry = feature.geometry()) {
            is Polygon -> containsPoint(geometry.coordinates(), point)
            is MultiPolygon -> geometry.coordinates().any { containsPoint(it, point) }
            else -> false
        }
    }?.getStringProperty("zoneId")

private fun containsPoint(rings: List<List<Point>>, point: Point): Boolean =
    rings.isNotEmpty() && isInsideRing(rings.first(), point) &&
        rings.drop(1).none { isInsideRing(it, point) }

private fun isInsideRing(ring: List<Point>, point: Point): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var previous = ring.last()
    for (current in ring) {
        val crossesLatitude = (current.latitude() > point.latitude()) !=
            (previous.latitude() > point.latitude())
        if (crossesLatitude) {
            val longitudeAtCrossing = current.longitude() +
                (point.latitude() - current.latitude()) * (previous.longitude() - current.longitude()) /
                (previous.latitude() - current.latitude())
            if (point.longitude() < longitudeAtCrossing) inside = !inside
        }
        previous = current
    }
    return inside
}
