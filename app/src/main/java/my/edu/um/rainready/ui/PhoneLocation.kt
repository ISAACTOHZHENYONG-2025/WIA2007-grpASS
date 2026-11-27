package my.edu.um.rainready.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import my.edu.um.rainready.model.LonLat
import kotlin.coroutines.resume

/** The location permissions the route check asks for: precise, or approximate if the user prefers. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Reads the phone's position once (FR2.3: "read once"), or returns null when there is no permission,
 * location is off, or no fix comes within [timeoutMillis]. Nothing is stored or sent anywhere.
 */
@SuppressLint("MissingPermission") // checked by hasLocationPermission()
suspend fun readLocationOnce(context: Context, timeoutMillis: Long = 15_000): LonLat? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val provider = listOfNotNull(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null

    val fresh = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        }
    }
    val location = fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: return null
    return LonLat(location.longitude, location.latitude)
}
