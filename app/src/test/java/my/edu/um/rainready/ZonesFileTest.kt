package my.edu.um.rainready

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.STALE_AFTER_MILLIS
import my.edu.um.rainready.model.isStale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the zones file the map ships with (app/src/main/assets/zones.geojson). */
class ZonesFileTest {
    private val zones: List<JsonObject> by lazy {
        val text = File("src/main/assets/zones.geojson").readText()
        JsonParser.parseString(text).asJsonObject.getAsJsonArray("features").map { it.asJsonObject }
    }

    @Test
    fun hasTheTenDraftZones() {
        // Field names follow DATA_LAYOUT.md v1: zoneId and groundLevel.
        val ids = zones.map { it.getAsJsonObject("properties")["zoneId"].asString }
        assertEquals((1..10).map { "Z$it" }, ids)
    }

    @Test
    fun everyZoneHasAGroundLevel() {
        for (z in zones) {
            val p = z.getAsJsonObject("properties")
            assertTrue("${p["zoneId"]} groundLevel", p["groundLevel"].asString in setOf("low", "medium", "high"))
        }
    }

    @Test
    fun everyZoneHasAShortLabel() {
        for (z in zones) {
            val p = z.getAsJsonObject("properties")
            assertTrue("${p["zoneId"]} label", p["label"].asString.isNotBlank() && p["label"].asString.length <= 30)
        }
    }

    @Test
    fun everyLabelPointIsInsideItsZone() {
        for (z in zones) {
            val p = z.getAsJsonObject("properties")
            val x = p["labelLon"].asDouble
            val y = p["labelLat"].asDouble
            val g = z.getAsJsonObject("geometry")
            val polygons: List<JsonArray> = when (g["type"].asString) {
                "Polygon" -> listOf(g.getAsJsonArray("coordinates"))
                else -> g.getAsJsonArray("coordinates").map { it.asJsonArray }
            }
            assertTrue("${p["zoneId"]} label point", polygons.any { inside(x, y, it) })
        }
    }

    @Test
    fun mapDataGoesStaleAfterFifteenMinutes() {
        val state = MapState(emptyMap(), emptyList(), updatedAtMillis = 0L)
        assertFalse(state.isStale(STALE_AFTER_MILLIS))
        assertTrue(state.isStale(STALE_AFTER_MILLIS + 1))
    }

    /** Even-odd ray casting over the outer ring and any holes. */
    private fun inside(x: Double, y: Double, polygon: JsonArray): Boolean {
        var result = false
        for (ringEl in polygon) {
            val ring = ringEl.asJsonArray.map { it.asJsonArray }
            for (i in 0 until ring.size - 1) {
                val x1 = ring[i][0].asDouble; val y1 = ring[i][1].asDouble
                val x2 = ring[i + 1][0].asDouble; val y2 = ring[i + 1][1].asDouble
                if ((y1 > y) != (y2 > y) && x < (x2 - x1) * (y - y1) / (y2 - y1) + x1) result = !result
            }
        }
        return result
    }
}
