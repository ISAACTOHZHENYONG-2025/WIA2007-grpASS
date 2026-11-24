package my.edu.um.rainready.ui.map

import android.content.Context
import android.view.Gravity
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonObject
import my.edu.um.rainready.model.LonLat
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.ui.components.style
import my.edu.um.rainready.ui.theme.PinUnconfirmed
import my.edu.um.rainready.ui.theme.Primary
import my.edu.um.rainready.ui.theme.ZoneCautionOutline
import my.edu.um.rainready.ui.theme.ZoneCaution
import my.edu.um.rainready.ui.theme.ZoneClear
import my.edu.um.rainready.ui.theme.ZoneFlooded
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPadding
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The campus map: OpenFreeMap "Liberty" tiles with our zones (colour + outline style + label with
 * symbol and word) and report pins on top. Owns one MapView; call [render] whenever the data changes,
 * and [showRoute] to highlight a route-check path.
 *
 * @param interactive false for a small map inside a scrolling screen: no panning or zooming, so the
 *   page scrolls instead.
 */
internal class CampusMapView(
    private val context: Context,
    zonesGeoJson: String,
    private val interactive: Boolean = true,
) {
    // A small map inside a scrolling page draws on a TextureView, which scrolls with the page.
    val view: MapView = MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(!interactive))
        .apply { onCreate(null) }

    /** Set when the style or tiles fail to load (no internet on first launch, for example). */
    var onLoadFailed: ((String) -> Unit)? = null

    private val zones: List<Feature> = FeatureCollection.fromJson(zonesGeoJson).features().orEmpty()
    private val images = MapImages(context)
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latest: MapState? = null
    private var route: Pair<List<LonLat>?, List<List<LonLat>>>? = null

    init {
        view.addOnDidFailLoadingMapListener { message -> onLoadFailed?.invoke(message) }
        view.getMapAsync { map ->
            this.map = map
            configure(map)
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                style = loaded
                addLayers(loaded)
                latest?.let { render(it) }
                route?.let { (main, others) -> showRoute(main, others) }
            }
        }
    }

    private fun configure(map: MapLibreMap) {
        map.cameraPosition = CameraPosition.Builder().target(START_CENTRE).zoom(START_ZOOM).build()
        map.setMinZoomPreference(MIN_ZOOM)
        map.setMaxZoomPreference(MAX_ZOOM)
        map.setLatLngBoundsForCameraTarget(CAMPUS_BOUNDS)
        map.uiSettings.apply {
            // A flat 2D map: no rotating or tilting.
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            // Our own "© OpenStreetMap contributors" line sits bottom-left; MapLibre's "i" button
            // (OpenFreeMap, OpenMapTiles, OpenStreetMap) stays bottom-right, as the OpenFreeMap terms require.
            isLogoEnabled = false
            isAttributionEnabled = true
            attributionGravity = Gravity.BOTTOM or Gravity.END
            val dp = context.resources.displayMetrics.density
            setAttributionMargins(0, 0, (12 * dp).toInt(), (12 * dp).toInt())
            if (!interactive) {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isDoubleTapGesturesEnabled = false
                isQuickZoomGesturesEnabled = false
            }
        }
    }

    /**
     * Highlights a route: [main] as a solid blue line with a dot at each end, [others] as dashed grey
     * lines (for example the usual path when a safer route is shown), and moves the camera to fit them.
     * Pass null and an empty list to clear it. Safe to call before the style has loaded.
     */
    fun showRoute(main: List<LonLat>?, others: List<List<LonLat>>) {
        route = main to others
        val s = style ?: return
        fun line(points: List<LonLat>) = Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) }))
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE_MAIN)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(main?.let(::line))))
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE_OTHER)?.setGeoJson(FeatureCollection.fromFeatures(others.map(::line)))
        val ends = (main ?: others.firstOrNull()).orEmpty().let { listOfNotNull(it.firstOrNull(), it.lastOrNull()) }
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE_ENDS)?.setGeoJson(
            FeatureCollection.fromFeatures(ends.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        )
        val all = listOfNotNull(main).flatten() + others.flatten()
        if (all.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { all.forEach { include(LatLng(it.lat, it.lon)) } }.build()
            val pad = (40 * context.resources.displayMetrics.density).toInt()
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad))
        }
    }

    /** Shows the latest zone statuses and pins. Safe to call before the style has loaded. */
    fun render(state: MapState) {
        latest = state
        val s = style ?: return

        val zoneFeatures = ArrayList<Feature>(zones.size)
        val labelFeatures = ArrayList<Feature>(zones.size)
        for (z in zones) {
            val id = z.getStringProperty("zoneId") ?: continue
            val status = state.zoneStatus[id] ?: ZoneStatus.CLEAR
            val props = z.properties()?.deepCopy() ?: JsonObject()
            props.addProperty("status", status.key)
            zoneFeatures += Feature.fromGeometry(z.geometry(), props)

            val image = "zone-label-$id-${status.key}"
            if (s.getImage(image) == null) {
                s.addImage(image, images.zoneLabel(z.getStringProperty("label") ?: id, status, context.getString(status.style().word)))
            }
            val label = JsonObject().apply {
                addProperty("image", image)
                addProperty("rank", status.rank) // more serious zones win when labels collide
            }
            val lon = z.getNumberProperty("labelLon")?.toDouble() ?: continue
            val lat = z.getNumberProperty("labelLat")?.toDouble() ?: continue
            labelFeatures += Feature.fromGeometry(Point.fromLngLat(lon, lat), label)
        }
        s.getSourceAs<GeoJsonSource>(SRC_ZONES)?.setGeoJson(FeatureCollection.fromFeatures(zoneFeatures))
        // The small route map is too small for the name tags; colour and outline still show each status.
        s.getSourceAs<GeoJsonSource>(SRC_LABELS)?.setGeoJson(FeatureCollection.fromFeatures(if (interactive) labelFeatures else emptyList()))

        val pins = state.reports.map { r ->
            val props = JsonObject().apply { addProperty("image", if (r.confirmed) PIN_CONFIRMED else PIN_UNCONFIRMED) }
            Feature.fromGeometry(Point.fromLngLat(r.longitude, r.latitude), props)
        }
        s.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(FeatureCollection.fromFeatures(pins))
    }

    private fun addLayers(s: Style) {
        s.addImage(PIN_CONFIRMED, images.pin(confirmed = true))
        s.addImage(PIN_UNCONFIRMED, images.pin(confirmed = false))
        s.addSource(GeoJsonSource(SRC_ZONES))
        s.addSource(GeoJsonSource(SRC_LABELS))
        s.addSource(GeoJsonSource(SRC_PINS))

        // Zone shading and outlines go under the map's own street labels so those stay readable.
        val firstSymbol = s.layers.firstOrNull { it is SymbolLayer }?.id
        for (layer in zoneLayers()) {
            if (firstSymbol != null) s.addLayerBelow(layer, firstSymbol) else s.addLayer(layer)
        }
        // A route-check path goes over the streets but under our zone labels and pins.
        s.addSource(GeoJsonSource(SRC_ROUTE_OTHER))
        s.addSource(GeoJsonSource(SRC_ROUTE_MAIN))
        s.addSource(GeoJsonSource(SRC_ROUTE_ENDS))
        s.addLayer(
            LineLayer("rr-route-other", SRC_ROUTE_OTHER).withProperties(
                lineColor(PinUnconfirmed.toArgb()), lineWidth(4f), lineDasharray(arrayOf(1.2f, 1.2f)),
            )
        )
        s.addLayer(
            LineLayer("rr-route-main", SRC_ROUTE_MAIN).withProperties(
                lineColor(Primary.toArgb()), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        s.addLayer(
            CircleLayer("rr-route-ends", SRC_ROUTE_ENDS).withProperties(
                circleRadius(6f), circleColor(Primary.toArgb()), circleStrokeColor(android.graphics.Color.WHITE), circleStrokeWidth(2.5f),
            )
        )
        // Zone labels and pins go on top of everything.
        s.addLayer(
            SymbolLayer(LAYER_LABELS, SRC_LABELS).withProperties(
                iconImage(get("image")),
                iconAllowOverlap(false),
                iconPadding(2f),
                symbolSortKey(get("rank")),
            )
        )
        s.addLayer(
            SymbolLayer(LAYER_PINS, SRC_PINS).withProperties(
                iconImage(get("image")),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM), // the tip marks the spot
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            )
        )
    }

    /**
     * Colour alone is not enough, so each status also has its own outline (as in Figma):
     * Clear = thin solid, Caution = dashed, Flooded = thick solid.
     */
    private fun zoneLayers(): List<Layer> {
        fun status(s: ZoneStatus) = eq(get("status"), literal(s.key))
        return listOf(
            FillLayer("rr-zone-fill-clear", SRC_ZONES).withFilter(status(ZoneStatus.CLEAR))
                .withProperties(fillColor(ZoneClear.toArgb()), fillOpacity(0.16f)),
            FillLayer("rr-zone-fill-caution", SRC_ZONES).withFilter(status(ZoneStatus.CAUTION))
                .withProperties(fillColor(ZoneCaution.toArgb()), fillOpacity(0.30f)),
            FillLayer("rr-zone-fill-flooded", SRC_ZONES).withFilter(status(ZoneStatus.FLOODED))
                .withProperties(fillColor(ZoneFlooded.toArgb()), fillOpacity(0.22f)),
            LineLayer("rr-zone-line-clear", SRC_ZONES).withFilter(status(ZoneStatus.CLEAR))
                .withProperties(lineColor(ZoneClear.toArgb()), lineWidth(1.5f), lineJoin(Property.LINE_JOIN_ROUND)),
            LineLayer("rr-zone-line-caution", SRC_ZONES).withFilter(status(ZoneStatus.CAUTION))
                .withProperties(lineColor(ZoneCautionOutline.toArgb()), lineWidth(2f), lineDasharray(arrayOf(4f, 2.5f))),
            LineLayer("rr-zone-line-flooded", SRC_ZONES).withFilter(status(ZoneStatus.FLOODED))
                .withProperties(lineColor(ZoneFlooded.toArgb()), lineWidth(3f), lineJoin(Property.LINE_JOIN_ROUND)),
        )
    }

    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private val START_CENTRE = LatLng(3.1225, 101.6545)
        private const val START_ZOOM = 14.6
        private const val MIN_ZOOM = 13.5
        private const val MAX_ZOOM = 19.0
        /** Keeps the map on campus: the zones' extent plus a little margin. */
        private val CAMPUS_BOUNDS = LatLngBounds.Builder()
            .include(LatLng(3.1060, 101.6380))
            .include(LatLng(3.1400, 101.6700))
            .build()

        private const val SRC_ZONES = "rr-zones"
        private const val SRC_LABELS = "rr-zone-labels"
        private const val SRC_PINS = "rr-pins"
        private const val SRC_ROUTE_MAIN = "rr-route-main"
        private const val SRC_ROUTE_OTHER = "rr-route-other"
        private const val SRC_ROUTE_ENDS = "rr-route-ends"
        private const val LAYER_LABELS = "rr-layer-zone-labels"
        private const val LAYER_PINS = "rr-layer-pins"
        private const val PIN_CONFIRMED = "pin-confirmed"
        private const val PIN_UNCONFIRMED = "pin-unconfirmed"

        private val ZoneStatus.key get() = name.lowercase()
        private val ZoneStatus.rank get() = when (this) { ZoneStatus.FLOODED -> 0; ZoneStatus.CAUTION -> 1; ZoneStatus.CLEAR -> 2 }
    }
}
