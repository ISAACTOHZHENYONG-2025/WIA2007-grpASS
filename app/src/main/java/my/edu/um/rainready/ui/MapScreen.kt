package my.edu.um.rainready.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import my.edu.um.rainready.R
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.ui.components.ZoneSymbol
import my.edu.um.rainready.ui.components.style
import my.edu.um.rainready.ui.map.CampusMapView
import my.edu.um.rainready.ui.theme.PinConfirmed
import my.edu.um.rainready.ui.theme.PinUnconfirmed
import java.util.Date

/**
 * Campus map (FR2.1, FR2.2), from Figma "04 Map": zones with colour, outline style, symbol and word,
 * grey and red report pins, "Updated" time, a grey banner when data is over 15 minutes old,
 * a colour legend and the OpenStreetMap credit.
 *
 * @param clock the time now, in epoch milliseconds. Tests pass a fake clock.
 */
@Composable
fun MapScreen(state: MapState?, modifier: Modifier = Modifier, clock: () -> Long = System::currentTimeMillis) {
    val context = LocalContext.current
    val stale = rememberIsStale(state?.updatedAtMillis, clock)
    val map = remember {
        val zones = context.assets.open("zones.geojson").bufferedReader().use { it.readText() }
        CampusMapView(context, zones)
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showLegend by rememberSaveable { mutableStateOf(true) }
    MapLifecycle(map)
    LaunchedEffect(map) { map.onLoadFailed = { loadError = it } }
    LaunchedEffect(state) { state?.let(map::render) }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { map.view }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapHeader(state, showLegend, onToggleLegend = { showLegend = !showLegend })
            if (state != null && stale) {
                InfoBanner(
                    stringResource(R.string.banner_offline_title),
                    stringResource(R.string.banner_offline_body, timeText(state.updatedAtMillis)),
                )
            }
            loadError?.let { InfoBanner(stringResource(R.string.map_load_failed_title), stringResource(R.string.map_load_failed_body)) }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showLegend) MapLegend()
            Surface(color = Color.White.copy(alpha = 0.85f), shape = RoundedCornerShape(4.dp)) {
                Text(
                    stringResource(R.string.map_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A4F55),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Forwards the screen's lifecycle to the MapView, and cleans it up when the screen leaves. */
@Composable
private fun MapLifecycle(map: CampusMapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> map.view.onStart()
                Lifecycle.Event.ON_RESUME -> map.view.onResume()
                Lifecycle.Event.ON_PAUSE -> map.view.onPause()
                Lifecycle.Event.ON_STOP -> map.view.onStop()
                Lifecycle.Event.ON_DESTROY -> { map.view.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Leaving the Map tab removes the screen while the app keeps running.
            if (!destroyed) {
                val current = lifecycle.currentState
                if (current.isAtLeast(Lifecycle.State.RESUMED)) map.view.onPause()
                if (current.isAtLeast(Lifecycle.State.STARTED)) map.view.onStop()
                map.view.onDestroy()
            }
        }
    }
}

@Composable
private fun timeText(millis: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(millis))

@Composable
private fun MapHeader(state: MapState?, showLegend: Boolean, onToggleLegend: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (state != null) {
                    Text(
                        stringResource(R.string.map_updated, timeText(state.updatedAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleLegend) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(if (showLegend) R.string.map_legend_hide else R.string.map_legend_show),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Small legend card from the Figma map: the three zone states and the two pin types. */
@Composable
private fun MapLegend() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ZoneStatus.entries.forEach { status ->
                LegendRow(stringResource(status.style().word)) { ZoneSymbol(status, size = 14.dp) }
            }
            LegendRow(stringResource(R.string.legend_unconfirmed)) { PinIcon(PinUnconfirmed) }
            LegendRow(stringResource(R.string.legend_confirmed)) { PinIcon(PinConfirmed) }
        }
    }
}

@Composable
private fun LegendRow(word: String, symbol: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 14.dp, height = 16.dp), contentAlignment = Alignment.Center) { symbol() }
        Text(word, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The teardrop pin, small, for the legend. */
@Composable
private fun PinIcon(color: Color, width: Dp = 11.dp) {
    Canvas(Modifier.width(width).height(width * 36f / 28f)) {
        val k = size.width / 28f
        val body = Path().apply {
            moveTo(14 * k, 0f)
            cubicTo(6.27f * k, 0f, 0f, 6.27f * k, 0f, 14 * k)
            cubicTo(0f, 24.5f * k, 14 * k, 36 * k, 14 * k, 36 * k)
            cubicTo(14 * k, 36 * k, 28 * k, 24.5f * k, 28 * k, 14 * k)
            cubicTo(28 * k, 6.27f * k, 21.73f * k, 0f, 14 * k, 0f)
            close()
        }
        drawPath(body, color)
        drawCircle(Color.White, radius = 5 * k, center = androidx.compose.ui.geometry.Offset(14 * k, 14 * k))
    }
}
