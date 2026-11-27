package my.edu.um.rainready

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.data.ZoneRulesRepository
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.ZoneStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * The live data path against a local Firestore emulator: rows written the way the report flow writes
 * them (docs/DATA_LAYOUT.md) reach the map and the route verdict through the real listeners and
 * Jingying's rules. Skipped unless the emulator runs on the computer and the phone can reach it:
 *     firebase emulators:start --only firestore --project rainready-app
 *     adb reverse tcp:8080 tcp:8080   (the device's 127.0.0.1:8080 is then the computer's; emulator or USB phone)
 */
@RunWith(AndroidJUnit4::class)
class LiveDataEmulatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val campus by lazy { CampusData.load(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val HOST = "127.0.0.1" // the computer, through adb reverse tcp:8080 tcp:8080
        private const val PORT = 8080
        private const val PROJECT = "rainready-app"

        /** Its own Firebase app, so the test never touches the real project. */
        private val db: FirebaseFirestore by lazy {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val app = FirebaseApp.getApps(ctx).firstOrNull { it.name == "emulator-test" }
                ?: FirebaseApp.initializeApp(ctx, FirebaseApp.getInstance().options, "emulator-test")
            FirebaseFirestore.getInstance(app).apply {
                useEmulator(HOST, PORT)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }

        private fun emulator(method: String, path: String): Result<Int> = runCatching {
            val c = URL("http://$HOST:$PORT$path").openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            c.responseCode.also { c.disconnect() }
        }
    }

    @Before
    fun emptyEmulator() {
        val running = emulator("GET", "/")
        assumeTrue("Firestore emulator not reachable (adb reverse tcp:8080 tcp:8080?): ${running.exceptionOrNull()}", running.isSuccess)
        // Start every test with an empty database.
        assertEquals(200, emulator("DELETE", "/emulator/v1/projects/$PROJECT/databases/(default)/documents").getOrThrow())
    }

    @After
    fun stop() = scope.cancel()

    private fun liveRepo() = ZoneRulesRepository.live(campus, db, scope, rain = flowOf(null), tickMs = 1_000)

    /** A report as the report flow writes it (Chen Xi's and zy's ReportRepository). */
    private fun writeReport(id: String, zone: String, status: String, confirmCount: Int, lat: Double, lng: Double) {
        Tasks.await(
            db.collection("reports").document(id).set(
                mapOf(
                    "reportId" to id, "zoneId" to zone, "category" to "flood", "note" to "Water at the entrance",
                    "lat" to lat, "lng" to lng, "createdAt" to Timestamp.now(), "status" to status,
                    "confirmCount" to confirmCount, "photoCount" to 1, "flagCount" to 0, "fixedAt" to null,
                ),
            ),
        )
    }

    @Test
    fun aConfirmedReportTurnsTheZoneRedAndChangesTheVerdictUntilItIsFixed() = runBlocking<Unit> {
        val repo = liveRepo()
        val trip = SampleRouteChecker(campus, repo.map)
        withTimeout(20_000) { trip.check("KK8", "FSKTM").first { it?.verdict == Verdict.GO } }

        // Three people confirm a flood next to FSKTM (zone Z1).
        writeReport("R-test-1", "Z1", "confirmed", 3, 3.12800, 101.65080)
        val verdict = withTimeout(20_000) { trip.check("KK8", "FSKTM").first { it?.verdict == Verdict.NO_SAFE_ROUTE } }
        assertEquals("flood", verdict!!.causeCategory)
        val map = withTimeout(20_000) { repo.map.first { it.zoneStatus["Z1"] == ZoneStatus.FLOODED } }
        assertTrue(map.reports.single { it.id == "R-test-1" }.confirmed)
        assertTrue("connected, so the data is current", System.currentTimeMillis() - map.updatedAtMillis < 60_000)

        // Staff mark it fixed: the zone clears and the usual path is fine again.
        Tasks.await(
            db.collection("reports").document("R-test-1")
                .update(mapOf("status" to "fixed", "fixedAt" to Timestamp.now())),
        )
        withTimeout(20_000) { trip.check("KK8", "FSKTM").first { it?.verdict == Verdict.GO } }
        assertTrue(repo.map.first().reports.isEmpty())
    }

    @Test
    fun anUnconfirmedReportIsOnlyAGreyPin() = runBlocking<Unit> {
        val repo = liveRepo()
        writeReport("R-test-2", "Z1", "unconfirmed", 1, 3.12800, 101.65080)
        val map = withTimeout(20_000) { repo.map.first { m -> m.reports.any { it.id == "R-test-2" } } }
        assertEquals(false, map.reports.single().confirmed)
        assertEquals(ZoneStatus.CLEAR, map.zoneStatus["Z1"])
        assertEquals(Verdict.GO, SampleRouteChecker(campus, repo.map).check("KK8", "FSKTM").first()?.verdict)
    }

    @Test
    fun aStaffOverrideColoursTheZoneUntilItEnds() = runBlocking<Unit> {
        val repo = liveRepo()
        val until = Timestamp(Date(System.currentTimeMillis() + 60 * 60 * 1000L))
        Tasks.await(
            db.collection("zones").document("Z6")
                .set(mapOf("zoneId" to "Z6", "staffOverride" to "red", "staffOverrideUntil" to until, "updatedAt" to Timestamp.now())),
        )
        withTimeout(20_000) { repo.map.first { it.zoneStatus["Z6"] == ZoneStatus.FLOODED } }
        assertEquals(
            "Set by staff",
            withTimeout(20_000) { repo.home.first { h -> h.zones.any { it.id == "Z6" && it.status == ZoneStatus.FLOODED } } }
                .zones.first { it.id == "Z6" }.detail,
        )

        // Staff clear it.
        Tasks.await(db.collection("zones").document("Z6").update(mapOf("staffOverride" to "", "updatedAt" to Timestamp.now())))
        withTimeout(20_000) { repo.map.first { it.zoneStatus["Z6"] == ZoneStatus.CLEAR } }
    }
}
