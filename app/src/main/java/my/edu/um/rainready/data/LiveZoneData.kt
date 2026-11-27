package my.edu.um.rainready.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import my.edu.um.rainready.BuildConfig
import my.edu.um.rainready.model.ReportPin
import my.edu.um.rainready.zone.ReportInput
import my.edu.um.rainready.zone.ZoneInput
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

private const val TAG = "LiveZoneData"

/** The rain forecast for the next hour on campus, and when it was fetched. */
data class RainReading(val mmNextHour: Double?, val fetchedAtMillis: Long)

/** One row of the `zones` collection: the staff override (docs/DATA_LAYOUT.md 3.1). */
data class ZoneDoc(val zoneId: String, val staffOverride: String?, val staffOverrideUntilMillis: Long?)

/** The fields of a `reports` row that the zone colours and the map pins use (docs/DATA_LAYOUT.md 3.2). */
data class ReportDoc(
    val reportId: String,
    val zoneId: String,
    val category: String,
    val status: String,
    val createdAtMillis: Long?,
    val fixedAtMillis: Long?,
    val lat: Double?,
    val lng: Double?,
)

/** The rows from one Firestore listener, and whether they came from the server (not only the phone's cache). */
data class Feed<T>(val rows: List<T>, val fromServer: Boolean)

/** Turns the Firestore rows and the forecast into Jingying's rule inputs and the map pins. No Firebase here, so it is unit tested. */
object LiveZoneMapping {
    /** A confirmed report stops counting after 6 hours (docs/DATA_LAYOUT.md 3.2), so its pin goes too. */
    const val REPORT_WINDOW_MS = 6 * 60 * 60 * 1000L

    /** One [ZoneInput] per zone in the campus file (Z1 to Z10), in zone order. */
    fun zoneInputs(
        groundLevel: Map<String, String>,
        zones: List<ZoneDoc>,
        reports: List<ReportDoc>,
        rain: RainReading?,
    ): List<ZoneInput> {
        val overrides = zones.associateBy { it.zoneId }
        val byZone = reports.groupBy { it.zoneId }
        return groundLevel.keys.sortedBy { it.drop(1).toIntOrNull() ?: 0 }.map { id ->
            ZoneInput(
                zoneId = id,
                groundLevel = groundLevel[id],
                staffOverride = overrides[id]?.staffOverride,
                staffOverrideUntilMillis = overrides[id]?.staffOverrideUntilMillis,
                reports = byZone[id].orEmpty().map { ReportInput(it.status, it.category, it.createdAtMillis, it.fixedAtMillis) },
                rainMmPerHour = rain?.mmNextHour,
                weatherFetchedAtMillis = rain?.fetchedAtMillis,
            )
        }
    }

    /** Pins for reports that are not fixed, are less than 6 hours old and have a position. Grey until confirmed. */
    fun pins(reports: List<ReportDoc>, now: Long): List<ReportPin> = reports
        .filter { r ->
            r.fixedAtMillis == null && !r.status.equals("fixed", ignoreCase = true) &&
                r.createdAtMillis != null && now - r.createdAtMillis < REPORT_WINDOW_MS &&
                r.lat != null && r.lng != null
        }
        .map { ReportPin(it.reportId, it.lat!!, it.lng!!, confirmed = it.status.equals("confirmed", ignoreCase = true)) }

    /**
     * The next [ZoneFeed] from the latest rows. While both listeners have server data, the data is
     * current now; when either falls back to the phone's cache (offline), the time stays at the last
     * moment it was current, so the screens' 15-minute timer turns the banner grey. 0 = never current.
     */
    fun feed(
        previous: ZoneFeed?,
        groundLevel: Map<String, String>,
        zones: Feed<ZoneDoc>,
        reports: Feed<ReportDoc>,
        rain: RainReading?,
        now: Long,
    ) = ZoneFeed(
        inputs = zoneInputs(groundLevel, zones.rows, reports.rows, rain),
        pins = pins(reports.rows, now),
        updatedAtMillis = if (zones.fromServer && reports.fromServer) now else previous?.updatedAtMillis ?: 0L,
        rainNextHourMm = rain?.mmNextHour,
    )
}

/** Listens to the `zones` and `reports` collections. Both are readable without signing in (zy's rules). */
class FirestoreZoneFeed(private val db: FirebaseFirestore, private val clock: () -> Long = System::currentTimeMillis) {

    val zones: Flow<Feed<ZoneDoc>> = listen("zones", { db.collection("zones") }) { it.toZoneDoc() }

    /** Only reports from the last 6 hours: older ones no longer change a zone, and it keeps the reads down. */
    val reports: Flow<Feed<ReportDoc>> = listen("reports", {
        db.collection("reports").whereGreaterThan("createdAt", Timestamp(Date(clock() - LiveZoneMapping.REPORT_WINDOW_MS)))
    }) { it.toReportDoc() }

    /** [query] runs when the screens start listening, so the 6-hour window starts then. */
    private fun <T> listen(name: String, query: () -> Query, toRow: (DocumentSnapshot) -> T): Flow<Feed<T>> =
        callbackFlow {
            var last = emptyList<T>()
            val registration = query().addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (snapshot != null) {
                    last = snapshot.documents.map(toRow)
                    trySend(Feed(last, fromServer = !snapshot.metadata.isFromCache))
                } else {
                    // The listener stops after an error, so the data is no longer current.
                    Log.w(TAG, "Listening to $name failed", error)
                    trySend(Feed(last, fromServer = false))
                }
            }
            awaitClose { registration.remove() }
        }

    companion object {
        fun DocumentSnapshot.toZoneDoc() = ZoneDoc(
            zoneId = id,
            staffOverride = get("staffOverride") as? String,
            staffOverrideUntilMillis = (get("staffOverrideUntil") as? Timestamp)?.toDate()?.time,
        )

        fun DocumentSnapshot.toReportDoc() = ReportDoc(
            reportId = id,
            zoneId = get("zoneId") as? String ?: "",
            category = get("category") as? String ?: "",
            status = get("status") as? String ?: "unconfirmed",
            createdAtMillis = (get("createdAt") as? Timestamp)?.toDate()?.time,
            fixedAtMillis = (get("fixedAt") as? Timestamp)?.toDate()?.time,
            lat = (get("lat") as? Number)?.toDouble(),
            lng = (get("lng") as? Number)?.toDouble(),
        )
    }
}

/** The campus rain forecast from Open-Meteo, read the same way as Ker Li's rain-warning job (robot/rain-logic.js). */
object OpenMeteo {
    /** Same campus point and request as robot/rain-warning.js, so the app and the robot see the same forecast. */
    const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast?latitude=3.1231&longitude=101.6529" +
        "&hourly=precipitation&precipitation_unit=mm&timezone=Asia%2FKuala_Lumpur&timeformat=unixtime&forecast_days=2"
    private const val HOUR_MS = 60 * 60 * 1000L
    private const val REFRESH_MS = 15 * 60 * 1000L
    private const val RETRY_MS = 60 * 1000L

    /**
     * The most rain (mm) in any hourly interval that overlaps the next 60 minutes. Each value is the
     * rain in the hour before its time, so an interval ending at `end` overlaps when now < end < now + 2 h
     * (at an exact hour the interval that just ended is left out). Null when there is no usable value.
     */
    fun mmNextHour(json: String, now: Long): Double? {
        val hourly = JsonParser.parseString(json).asJsonObject.getAsJsonObject("hourly") ?: return null
        val times = hourly.getAsJsonArray("time") ?: return null
        val mm = hourly.getAsJsonArray("precipitation") ?: return null
        return (0 until minOf(times.size(), mm.size()))
            .filter { i -> val end = times[i].asLong * 1000; end > now && end < now + 2 * HOUR_MS }
            .mapNotNull { i -> mm[i].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble }
            .filter { it.isFinite() && it >= 0 }
            .maxOrNull()
    }

    private fun download(): String {
        val connection = URL(FORECAST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) error("Open-Meteo HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Null at first, then a reading every 15 minutes. After a failed fetch the last reading stays, so
     * it turns stale after 15 minutes and Jingying's rules handle that; the next try is a minute later.
     */
    fun readings(clock: () -> Long = System::currentTimeMillis): Flow<RainReading?> = flow {
        emit(null)
        while (true) {
            val reading = runCatching { download().let { RainReading(mmNextHour(it, clock()), clock()) } }
                .onFailure { Log.w(TAG, "Rain forecast not available", it) }
                .getOrNull()
            if (reading != null) emit(reading)
            delay(if (reading != null) REFRESH_MS else RETRY_MS)
        }
    }.flowOn(Dispatchers.IO)
}

/** Points Firestore at a local emulator when the build asks for one (-Prainready.firestoreEmulator=127.0.0.1, with adb reverse tcp:8080 tcp:8080). */
object FirestoreSetup {
    private var done = false

    /** Call before Firestore is first used (MainActivity does). */
    fun useEmulatorIfAsked(host: String = BuildConfig.FIRESTORE_EMULATOR) {
        if (done || host.isBlank()) return
        done = true
        FirebaseFirestore.getInstance().useEmulator(host, 8080)
    }
}
