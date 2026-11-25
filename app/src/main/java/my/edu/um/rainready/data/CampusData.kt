package my.edu.um.rainready.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import my.edu.um.rainready.model.Building
import my.edu.um.rainready.model.CampusPath
import my.edu.um.rainready.model.LonLat
import my.edu.um.rainready.model.joins
import my.edu.um.rainready.model.otherEnd

/**
 * The buildings, the hand-made paths between them (FR2.3) and each zone's ground level, as shipped
 * in the app's assets. Jingying's sheet is the source; the path lines were drawn once with the
 * OpenStreetMap walking router through the same points, so the app never calls a router.
 */
class CampusData(
    val buildings: List<Building>,
    val paths: List<CampusPath>,
    /** "low", "medium" or "high" per zone id, from zones.geojson. */
    val groundLevel: Map<String, String>,
    /** Short zone names per zone id, such as "Engineering", from zones.geojson. */
    val zoneLabel: Map<String, String> = emptyMap(),
) {
    private val byId = buildings.associateBy { it.id }

    fun building(id: String): Building? = byId[id]

    /** Buildings at either end of at least one path: the only places a route check can start. */
    val startBuildings: List<Building>
        get() = buildings.filter { b -> paths.any { it.otherEnd(b.id) != null } }

    /** All paths between two buildings, in either direction. */
    fun pathsBetween(a: String, b: String): List<CampusPath> = paths.filter { it.joins(a, b) }

    /** The usual path between two buildings: the one marked usual, else the quickest. */
    fun usualPath(a: String, b: String): CampusPath? =
        pathsBetween(a, b).let { all -> all.firstOrNull { it.usual } ?: all.minByOrNull { it.walkingMinutes } }

    /** Every building you can walk to from [startId] on a checked path. */
    fun destinationsFrom(startId: String): List<Building> =
        paths.mapNotNull { it.otherEnd(startId) }.distinct().mapNotNull { byId[it] }

    companion object {
        fun load(context: Context): CampusData {
            fun read(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
            return parse(read("buildings.json"), read("paths.geojson"), read("zones.geojson"))
        }

        fun parse(buildingsJson: String, pathsGeoJson: String, zonesGeoJson: String): CampusData {
            val buildings = JsonParser.parseString(buildingsJson).asJsonObject.getAsJsonArray("buildings").map {
                val o = it.asJsonObject
                Building(
                    id = o["buildingId"].asString,
                    name = o["name"].asString,
                    nameMs = o["nameMs"].asString,
                    zoneId = o["zoneId"].asString,
                    latitude = o["latitude"].asDouble,
                    longitude = o["longitude"].asDouble,
                )
            }
            val paths = features(pathsGeoJson).map { f ->
                val p = f.getAsJsonObject("properties")
                CampusPath(
                    id = p["pathId"].asString,
                    fromId = p["fromId"].asString,
                    toId = p["toId"].asString,
                    zoneIds = p.getAsJsonArray("zoneIds").map { it.asString },
                    walkingMinutes = p["walkingMinutes"].asInt,
                    usual = p["usual"].asBoolean,
                    line = f.getAsJsonObject("geometry").getAsJsonArray("coordinates").map {
                        val c = it.asJsonArray
                        LonLat(c[0].asDouble, c[1].asDouble)
                    },
                )
            }
            val zones = features(zonesGeoJson).map { it.getAsJsonObject("properties") }
            val groundLevel = zones.associate { it["zoneId"].asString to it["groundLevel"].asString }
            val zoneLabel = zones.associate { it["zoneId"].asString to it["label"].asString }
            return CampusData(buildings, paths, groundLevel, zoneLabel)
        }

        private fun features(geoJson: String): List<JsonObject> =
            JsonParser.parseString(geoJson).asJsonObject.getAsJsonArray("features").map { it.asJsonObject }
    }
}
