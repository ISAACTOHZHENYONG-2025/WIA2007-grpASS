package my.edu.um.rainready.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.map
import my.edu.um.rainready.R
import my.edu.um.rainready.data.RouteChecker
import my.edu.um.rainready.model.Building
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.RouteVerdict
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.model.nearestTo
import my.edu.um.rainready.ui.components.ZoneBadge
import my.edu.um.rainready.ui.components.ZoneSymbol
import my.edu.um.rainready.ui.map.CampusMapView
import my.edu.um.rainready.ui.theme.OnZoneCautionContainer
import my.edu.um.rainready.ui.theme.OnZoneClearContainer
import my.edu.um.rainready.ui.theme.OnZoneFloodedContainer
import my.edu.um.rainready.ui.theme.ZoneCautionContainer
import my.edu.um.rainready.ui.theme.ZoneClearContainer
import my.edu.um.rainready.ui.theme.ZoneFloodedContainer

/**
 * "Can I get to class?" (FR2.3), from Figma "02 Route check · pick a building" and
 * "03a to 03d Route check". The start is the phone's location, read once and snapped to the nearest
 * building with a checked path; with location off the user picks the start by hand.
 *
 * @param checker gives the verdict; the sample one until Jingying's engine is connected.
 * @param mapState zone colours and pins for the small map and the building list.
 * @param onShowOnMap opens the Map tab with the route highlighted.
 * @param askForLocation false in tests, so no permission dialog appears.
 */
@Composable
fun RouteCheckScreen(
    checker: RouteChecker,
    mapState: MapState?,
    onBack: () -> Unit,
    onShowOnMap: (MapRoute) -> Unit,
    modifier: Modifier = Modifier,
    askForLocation: Boolean = true,
) {
    val campus = checker.campus
    val context = LocalContext.current
    var startId by rememberSaveable { mutableStateOf<String?>(null) }
    var startFromLocation by rememberSaveable { mutableStateOf(false) }
    var locating by rememberSaveable { mutableStateOf(askForLocation) }
    var choosingStart by rememberSaveable { mutableStateOf(false) }
    var destinationId by rememberSaveable { mutableStateOf<String?>(null) }

    // Read the location once, after asking for permission if needed.
    var permissionAnswered by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionAnswered = true
    }
    LaunchedEffect(Unit) {
        if (askForLocation && startId == null && !permissionAnswered) {
            if (hasLocationPermission(context)) permissionAnswered = true else permission.launch(LOCATION_PERMISSIONS)
        }
    }
    LaunchedEffect(permissionAnswered) {
        if (permissionAnswered && startId == null) {
            locating = true
            val here = readLocationOnce(context)
            val near = here?.let { campus.startBuildings.nearestTo(it.lat, it.lon) }
            if (near != null) {
                startId = near.id
                startFromLocation = true
            }
            locating = false
        }
    }

    val destination = destinationId
    val start = startId
    if (destination != null && start != null) {
        VerdictView(
            checker = checker,
            mapState = mapState,
            startId = start,
            destinationId = destination,
            onBack = { destinationId = null },
            onShowOnMap = onShowOnMap,
            modifier = modifier,
        )
    } else {
        PickerView(
            checker = checker,
            mapState = mapState,
            startId = start,
            startFromLocation = startFromLocation,
            locating = locating,
            choosingStart = choosingStart || (start == null && !locating),
            onBack = onBack,
            onChangeStart = { choosingStart = true },
            onPickStart = { id ->
                startId = id
                startFromLocation = false
                choosingStart = false
            },
            onPickDestination = { destinationId = it },
            modifier = modifier,
        )
    }
}

/** The building name in the app's language. */
@Composable
private fun Building.displayName(): String =
    if (stringResource(R.string.language_code) == "ms") nameMs else name

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // IconButton gives a 48 dp tap area around the 24 dp icon.
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun PickerView(
    checker: RouteChecker,
    mapState: MapState?,
    startId: String?,
    startFromLocation: Boolean,
    locating: Boolean,
    choosingStart: Boolean,
    onBack: () -> Unit,
    onChangeStart: () -> Unit,
    onPickStart: (String) -> Unit,
    onPickDestination: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val campus = checker.campus
    var query by rememberSaveable { mutableStateOf("") }
    val start = startId?.let(campus::building)
    val ms = stringResource(R.string.language_code) == "ms"
    fun matches(b: Building) =
        query.isBlank() || listOf(b.name, b.nameMs, b.id).any { it.contains(query.trim(), ignoreCase = true) }

    Column(modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.home_route_title), onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.route_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                StartRow(
                    text = when {
                        locating -> stringResource(R.string.route_finding_location)
                        start == null -> stringResource(R.string.route_location_off)
                        startFromLocation -> stringResource(R.string.route_from_location, start.displayName())
                        else -> stringResource(R.string.route_from_building, start.displayName())
                    },
                    showChange = start != null && !choosingStart,
                    onChange = onChangeStart,
                )
            }
            if (locating && start == null) return@LazyColumn

            if (choosingStart) {
                item { SectionLabel(stringResource(R.string.route_pick_start_title)) }
                val starts = campus.startBuildings.filter(::matches).sortedBy { if (ms) it.nameMs else it.name }
                items(starts, key = { "start-" + it.id }) { b ->
                    BuildingRow(
                        title = b.displayName(),
                        subtitle = campus.zoneLabel[b.zoneId] ?: b.zoneId,
                        status = null,
                        onClick = {
                            query = "" // the search was for the start, not the destination
                            onPickStart(b.id)
                        },
                    )
                }
            } else if (start != null) {
                val destinations = campus.destinationsFrom(start.id)
                val shown = destinations.filter(::matches)
                item { SectionLabel(stringResource(R.string.route_buildings)) }
                if (destinations.isEmpty()) {
                    item { Note(stringResource(R.string.route_no_paths_from, start.displayName())) }
                } else if (shown.isEmpty()) {
                    item { Note(stringResource(R.string.route_no_results, query.trim())) }
                }
                items(shown, key = { "dest-" + it.id }) { b ->
                    val minutes = campus.usualPath(start.id, b.id)?.walkingMinutes
                    BuildingRow(
                        title = b.displayName(),
                        subtitle = minutes?.let { stringResource(R.string.route_usual_walk, it) }.orEmpty(),
                        status = mapState?.zoneStatus?.get(b.zoneId) ?: ZoneStatus.CLEAR,
                        onClick = { onPickDestination(b.id) },
                    )
                }
                if (destinations.isNotEmpty()) item { Note(stringResource(R.string.route_only_checked)) }
            }
        }
    }
}

@Composable
private fun StartRow(text: String, showChange: Boolean, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_my_location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (showChange) TextButton(onClick = onChange) { Text(stringResource(R.string.route_change_start)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** One building in a list: at least 56 dp tall, and the whole row can be tapped. */
@Composable
private fun BuildingRow(title: String, subtitle: String, status: ZoneStatus?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status != null) ZoneBadge(status)
    }
}

/** What the verdict screen is showing. */
private sealed interface VerdictState {
    data object Loading : VerdictState
    data object NoPath : VerdictState
    data class Ready(val verdict: RouteVerdict) : VerdictState
}

@Composable
private fun VerdictView(
    checker: RouteChecker,
    mapState: MapState?,
    startId: String,
    destinationId: String,
    onBack: () -> Unit,
    onShowOnMap: (MapRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val campus = checker.campus
    val destinationName = campus.building(destinationId)?.displayName() ?: destinationId
    val state by remember(startId, destinationId) {
        checker.check(startId, destinationId).map { v -> v?.let { VerdictState.Ready(it) } ?: VerdictState.NoPath }
    }.collectAsState(initial = VerdictState.Loading)
    val routeTitle = stringResource(R.string.map_route_to, destinationName)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(destinationName, onBack)
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                VerdictState.Loading -> Unit
                VerdictState.NoPath -> Note(stringResource(R.string.route_no_path))
                is VerdictState.Ready -> {
                    val v = s.verdict
                    val route = MapRoute(routeTitle, v.recommendedPath?.line, v.otherPaths.map { it.line })
                    RouteMapCard(mapState, route)
                    VerdictCard(v, destinationName, zoneName = { campus.zoneLabel[it] ?: it })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onShowOnMap(route) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.route_show_on_map)) }
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.route_check_another)) }
                    }
                    Text(
                        stringResource(R.string.route_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The small map above the verdict: zones, pins and the path, without panning so the page scrolls. */
@Composable
private fun RouteMapCard(mapState: MapState?, route: MapRoute) {
    val context = LocalContext.current
    val map = remember {
        val zones = context.assets.open("zones.geojson").bufferedReader().use { it.readText() }
        CampusMapView(context, zones, interactive = false)
    }
    MapLifecycle(map)
    LaunchedEffect(mapState) { mapState?.let(map::render) }
    LaunchedEffect(route) { map.showRoute(route.main, route.others) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        AndroidView(factory = { map.view }, modifier = Modifier.fillMaxSize())
        Surface(
            color = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        ) {
            Text(
                stringResource(R.string.map_credit),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4A4F55),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** The coloured verdict card: symbol and title, what to do, why, and when the reports are from. */
@Composable
private fun VerdictCard(v: RouteVerdict, destinationName: String, zoneName: (String) -> String) {
    val (container, onContainer) = when (v.verdict) {
        Verdict.GO -> ZoneClearContainer to OnZoneClearContainer
        Verdict.GO_TAKE_CARE -> ZoneCautionContainer to OnZoneCautionContainer
        Verdict.USE_SAFER_ROUTE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Verdict.NO_SAFE_ROUTE -> ZoneFloodedContainer to OnZoneFloodedContainer
    }
    val cause = v.causeZoneId?.let(zoneName)
    val title = when (v.verdict) {
        Verdict.GO -> stringResource(R.string.verdict_go_title)
        Verdict.GO_TAKE_CARE -> stringResource(R.string.verdict_care_title)
        Verdict.USE_SAFER_ROUTE -> stringResource(R.string.verdict_safer_title)
        Verdict.NO_SAFE_ROUTE -> stringResource(R.string.verdict_none_title)
    }
    val body = when (v.verdict) {
        Verdict.GO -> stringResource(R.string.verdict_go_body, destinationName, v.usualPath.walkingMinutes)
        Verdict.GO_TAKE_CARE -> stringResource(R.string.verdict_care_body, cause.orEmpty(), v.usualPath.walkingMinutes)
        Verdict.USE_SAFER_ROUTE -> stringResource(R.string.verdict_safer_body, cause.orEmpty())
        Verdict.NO_SAFE_ROUTE -> stringResource(R.string.verdict_none_body)
    }
    // FR2.3: every verdict says why. The text above already does for "Go" and for caution, so the
    // reason line adds the report's category when there is one, and names the zone for "No safe route".
    val confirmations = v.causeConfirmations
    val reason = when {
        v.causeCategory != null && confirmations != null && cause != null ->
            pluralStringResource(R.plurals.verdict_cause_report, confirmations, categoryText(v.causeCategory), confirmations, cause)
        cause != null && v.verdict == Verdict.NO_SAFE_ROUTE -> stringResource(R.string.verdict_cause_flooded, cause)
        else -> null
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(24.dp))
            .padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (v.verdict) {
                Verdict.GO -> ZoneSymbol(ZoneStatus.CLEAR, size = 32.dp)
                Verdict.GO_TAKE_CARE -> ZoneSymbol(ZoneStatus.CAUTION, size = 32.dp)
                Verdict.NO_SAFE_ROUTE -> ZoneSymbol(ZoneStatus.FLOODED, size = 32.dp)
                Verdict.USE_SAFER_ROUTE -> Icon(
                    painterResource(R.drawable.ic_alt_route),
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = onContainer)
        }
        Text(body, style = MaterialTheme.typography.bodyLarge, color = onContainer)
        if (v.leaveEarly) {
            Text(stringResource(R.string.verdict_leave_early), style = MaterialTheme.typography.bodyLarge, color = onContainer)
        }
        val safer = v.recommendedPath
        if (v.verdict == Verdict.USE_SAFER_ROUTE && safer != null) {
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                Text(
                    stringResource(R.string.verdict_safer_minutes, v.extraMinutes, safer.walkingMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = onContainer) }
        Text(
            stringResource(R.string.verdict_based_on, timeText(v.updatedAtMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = onContainer,
        )
    }
}

@Composable
private fun categoryText(category: String): String = when (category.trim().lowercase()) {
    "flood" -> stringResource(R.string.category_flood)
    "fallen tree" -> stringResource(R.string.category_fallen_tree)
    "blocked drain" -> stringResource(R.string.category_blocked_drain)
    else -> category
}
