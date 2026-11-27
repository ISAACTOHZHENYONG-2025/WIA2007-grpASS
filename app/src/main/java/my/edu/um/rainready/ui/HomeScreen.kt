package my.edu.um.rainready.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import my.edu.um.rainready.R
import my.edu.um.rainready.data.SampleHomeRepository
import my.edu.um.rainready.model.HomeState
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.model.worstStatus
import my.edu.um.rainready.ui.components.ZoneCard
import my.edu.um.rainready.ui.components.ZoneSymbol
import my.edu.um.rainready.ui.components.style
import my.edu.um.rainready.ui.theme.RainReadyTheme
import java.util.Date
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Home screen from Figma "01 Home · rain" and "08 Home · no problems".
 *
 * @param onChooseBuilding opens the "Can I get to class?" building picker.
 * @param onShowLegend opens the colour legend. The link is hidden while this is null
 *   (the legend dialog is Isaac's card).
 * @param clock the time now, in epoch milliseconds. Tests pass a fake clock.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onChooseBuilding: () -> Unit,
    modifier: Modifier = Modifier,
    onShowLegend: (() -> Unit)? = null,
    clock: () -> Long = System::currentTimeMillis,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.weatherLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        RouteCheckCard(onChooseBuilding)

        StatusBanner(state, rememberIsStale(state.updatedAtMillis, clock))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_your_zones),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (onShowLegend != null) {
                TextButton(onClick = onShowLegend) { Text(stringResource(R.string.home_colour_legend)) }
            }
        }

        val context = LocalContext.current
        val preferences = androidx.compose.runtime.remember {
            context.getSharedPreferences(
                "rainready_settings",
                android.content.Context.MODE_PRIVATE
            )
        }

        var followedZones by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(
                preferences.getStringSet("followed_zones", emptySet())
                    ?.toSet() ?: emptySet()
            )
        }

        androidx.compose.runtime.DisposableEffect(preferences) {
            val listener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "followed_zones") {
                        followedZones = preferences
                            .getStringSet("followed_zones", emptySet())
                            ?.toSet() ?: emptySet()
                    }
                }

            preferences.registerOnSharedPreferenceChangeListener(listener)

            onDispose {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val visibleZones = if (followedZones.isEmpty()) {
            state.zones
        } else {
            state.zones.filter { it.id in followedZones }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visibleZones.isEmpty()) {
                Text(
                    stringResource(R.string.home_no_zone_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleZones.forEach {
                    ZoneCard(it.name, it.detail, it.status)
                }
            }
        }
    }
}

/** The main feature's entry point: "Can I get to class?" with a building picker field. */
@Composable
private fun RouteCheckCard(onChooseBuilding: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_route_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_route_body), style = MaterialTheme.typography.bodyMedium)
            Surface(
                onClick = onChooseBuilding,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                    Text(
                        stringResource(R.string.home_choose_building),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        }
    }
}

/**
 * Summary banner: worst status across the zones, with the "last updated" time (FR2.1).
 * Grey "Can't get new reports" instead when [stale].
 */
@Composable
private fun StatusBanner(state: HomeState, stale: Boolean) {
    val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(state.updatedAtMillis))
    val flooded = state.zones.filter { it.status == ZoneStatus.FLOODED }
    val caution = state.zones.filter { it.status == ZoneStatus.CAUTION }
    val worst = state.zones.worstStatus()
    val title: String
    val body: String
    val bg: Color
    val fg: Color
    if (stale) {
        title = stringResource(R.string.banner_offline_title)
        body = stringResource(R.string.banner_offline_body, time)
        bg = MaterialTheme.colorScheme.surfaceContainerHighest
        fg = MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        val style = worst.style()
        bg = style.container
        fg = style.onContainer
        when (worst) {
            ZoneStatus.FLOODED -> {
                title = pluralStringResource(R.plurals.banner_flooded_title, flooded.size, flooded.size)
                body = stringResource(R.string.banner_zones_updated, flooded.joinToString(", ") { it.name }, time)
            }
            ZoneStatus.CAUTION -> {
                title = pluralStringResource(R.plurals.banner_caution_title, caution.size, caution.size)
                body = stringResource(R.string.banner_zones_updated, caution.joinToString(", ") { it.name }, time)
            }
            ZoneStatus.CLEAR -> {
                title = stringResource(R.string.banner_clear_title)
                body = stringResource(R.string.banner_updated, time)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!stale) ZoneSymbol(worst, size = 24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
            Text(body, style = MaterialTheme.typography.bodySmall, color = fg)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeRainyPreview() {
    RainReadyTheme {
        Surface { HomeScreen(SampleHomeRepository.rainyDay(), onChooseBuilding = {}, onShowLegend = {}) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeCalmPreview() {
    RainReadyTheme {
        Surface { HomeScreen(SampleHomeRepository.calmDay(), onChooseBuilding = {}, onShowLegend = {}) }
    }
}
