package my.edu.um.rainready.maptest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/** OpenFreeMap "Liberty" style. No API key, no sign-up. Attribution is shown by MapLibre (the "i" button). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Roughly the middle of the four test zones at UM. */
private val UM_CENTRE = LatLng(3.1231, 101.6529)
private const val START_ZOOM = 15.0
private const val ZONES_SOURCE = "zones"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this) // must run before any MapView is created
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MapCheckScreen()
            }
        }
    }
}

/** Numbers to copy into the Drive note. All times are measured from the moment the style is requested. */
data class MapStats(
    val run: Int = 1,
    val styleMs: Long? = null,
    val mapLoadedMs: Long? = null,
    val fullyRenderedMs: Long? = null,
    val framesLastSecond: Int = 0,
    val peakFramesPerSecond: Int = 0,
    val error: String? = null,
)

/** Owns the MapView listeners and the timing. Kept out of the composable so it is easy to read. */
class MapCheck(mapView: MapView, private val zonesGeoJson: String) {
    var stats by mutableStateOf(MapStats())
        private set

    private var map: MapLibreMap? = null
    private var startedAt = 0L
    private var frameCount = 0

    private fun elapsed() = SystemClock.elapsedRealtime() - startedAt

    init {
        mapView.addOnDidFinishLoadingStyleListener {
            if (stats.styleMs == null) stats = stats.copy(styleMs = elapsed())
        }
        mapView.addOnDidFinishLoadingMapListener {
            if (stats.mapLoadedMs == null) stats = stats.copy(mapLoadedMs = elapsed())
        }
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (fully && stats.fullyRenderedMs == null) stats = stats.copy(fullyRenderedMs = elapsed())
        }
        mapView.addOnDidFailLoadingMapListener { message ->
            stats = stats.copy(error = message)
        }
        // Counts frames the map actually draws. Pan and zoom, then read the peak.
        mapView.addOnDidFinishRenderingFrameListener { _, _, _ -> frameCount++ }
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(UM_CENTRE).zoom(START_ZOOM).build()
            load(isReload = false)
        }
    }

    /** Loads (or reloads) the OpenFreeMap style with our zone layers on top. */
    fun load(isReload: Boolean) {
        val m = map ?: return
        stats = MapStats(run = if (isReload) stats.run + 1 else 1)
        startedAt = SystemClock.elapsedRealtime()
        m.setStyle(
            Style.Builder()
                .fromUri(STYLE_URL)
                .withSource(GeoJsonSource(ZONES_SOURCE, zonesGeoJson))
                .withLayers(*zoneLayers())
        )
    }

    /** Called once a second by the screen. */
    fun tick() {
        val frames = frameCount
        frameCount = 0
        stats = stats.copy(framesLastSecond = frames, peakFramesPerSecond = maxOf(stats.peakFramesPerSecond, frames))
    }

    fun summary(): String {
        val s = stats
        fun ms(v: Long?) = v?.let { "$it ms" } ?: "not yet"
        return buildString {
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Run ${s.run}: style ${ms(s.styleMs)}, map loaded ${ms(s.mapLoadedMs)}, fully drawn ${ms(s.fullyRenderedMs)}")
            appendLine("Peak frames per second while panning: ${s.peakFramesPerSecond}")
            append("Error: ${s.error ?: "none"}")
        }
    }
}

/**
 * Zone fills and outlines. Colour alone is not enough, so each status also has its own outline:
 * Clear = thin solid, Caution = dashed, Flooded = thick solid. Colours match the Figma file.
 */
private fun zoneLayers(): Array<Layer> {
    fun status(s: String) = eq(get("status"), literal(s))
    return arrayOf(
        FillLayer("zone-fill-clear", ZONES_SOURCE).withFilter(status("clear"))
            .withProperties(fillColor("#1E7A34"), fillOpacity(0.20f)),
        FillLayer("zone-fill-caution", ZONES_SOURCE).withFilter(status("caution"))
            .withProperties(fillColor("#F2B600"), fillOpacity(0.35f)),
        FillLayer("zone-fill-flooded", ZONES_SOURCE).withFilter(status("flooded"))
            .withProperties(fillColor("#BA1A1A"), fillOpacity(0.30f)),
        LineLayer("zone-line-clear", ZONES_SOURCE).withFilter(status("clear"))
            .withProperties(lineColor("#1E7A34"), lineWidth(1.5f)),
        LineLayer("zone-line-caution", ZONES_SOURCE).withFilter(status("caution"))
            .withProperties(lineColor("#5C4300"), lineWidth(2.5f), lineDasharray(arrayOf(2f, 1.5f))),
        LineLayer("zone-line-flooded", ZONES_SOURCE).withFilter(status("flooded"))
            .withProperties(lineColor("#BA1A1A"), lineWidth(3.5f)),
    )
}

@Composable
fun MapCheckScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Create the MapView once and drive its lifecycle from the screen's lifecycle.
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val check = remember {
        val zones = context.assets.open("zones.geojson").bufferedReader().use { it.readText() }
        MapCheck(mapView, zones)
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(check) {
        while (true) {
            delay(1000)
            check.tick()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        StatsCard(
            stats = check.stats,
            onReload = { check.load(isReload = true) },
            onCopy = { copyToClipboard(context, check.summary()) },
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun StatsCard(stats: MapStats, onReload: () -> Unit, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    fun ms(v: Long?) = v?.let { "$it ms" } ?: "…"
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OpenFreeMap check · run ${stats.run}", style = MaterialTheme.typography.titleMedium)
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall)
            Text("Style loaded: ${ms(stats.styleMs)}", style = MaterialTheme.typography.bodyMedium)
            Text("Map loaded: ${ms(stats.mapLoadedMs)}", style = MaterialTheme.typography.bodyMedium)
            Text("Fully drawn: ${ms(stats.fullyRenderedMs)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Frames last second: ${stats.framesLastSecond} (peak ${stats.peakFramesPerSecond})",
                style = MaterialTheme.typography.bodyMedium,
            )
            stats.error?.let {
                Text("Error: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Green thin = Clear · amber dashed = Caution · red thick = Flooded",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onReload) { Text("Reload") }
                OutlinedButton(onClick = onCopy) { Text("Copy results") }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("RainReady map check", text))
    Toast.makeText(context, "Results copied", Toast.LENGTH_SHORT).show()
}
