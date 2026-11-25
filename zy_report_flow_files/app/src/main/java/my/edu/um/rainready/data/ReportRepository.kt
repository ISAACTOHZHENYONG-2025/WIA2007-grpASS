package my.edu.um.rainready.data

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

data class ConfirmationOutcome(
    val newConfirmCount: Int,
    val newStatus: String,
    val confirmedByStaff: Boolean
)

/**
 * Pure state-transition helper for FR3.2 confirmations (unit-testable offline).
 * Matches firestore.rules: blocks duplicate votes, fixed reports, and stale (>6h) reports.
 */
object ConfirmationRules {
    const val STALE_WINDOW_MS = 6L * 60 * 60 * 1000

    fun nextState(
        currentCount: Int,
        currentStatus: String,
        alreadyConfirmedByUser: Boolean,
        isStaff: Boolean,
        isStale: Boolean = false
    ): Result<Pair<Int, String>> {
        if (alreadyConfirmedByUser) {
            return Result.failure(IllegalStateException("You have already confirmed this report."))
        }
        if (currentStatus == "fixed") {
            return Result.failure(IllegalStateException("This report has already been marked fixed."))
        }
        if (isStale) {
            return Result.failure(IllegalStateException("This report is older than 6 hours and can no longer be confirmed."))
        }
        val updatedCount = currentCount + 1
        val nextStatus = when {
            isStaff -> "confirmed"
            updatedCount >= 3 && currentStatus == "unconfirmed" -> "confirmed"
            else -> currentStatus
        }
        return Result.success(updatedCount to nextStatus)
    }
}

class ReportRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val MAX_PHOTO_DIMENSION_PX = 800
        private const val MAX_JPEG_BYTES = 100 * 1024
        private const val DAILY_REPORT_LIMIT = 5

        // Computed from Emmanuel_zones-draft_2026-11-10.geojson + 100 m buffer (~0.00090 deg):
        // Exact GeoJSON: lat [3.11284, 3.13470], lng [101.63742, 101.66418] (includes Z3 / KK13 at 101.63868)
        const val MIN_LAT = 3.1119
        const val MAX_LAT = 3.1356
        const val MIN_LNG = 101.6365
        const val MAX_LNG = 101.6651

        private val VALID_ZONES = (1..10).map { "Z$it" }.toSet()
    }

    fun isUmEmail(email: String?): Boolean {
        val normalized = email?.trim()?.lowercase() ?: return false
        return normalized.endsWith("@siswa.um.edu.my") || normalized.endsWith("@um.edu.my")
    }

    fun isInsideUm(lat: Double, lng: Double): Boolean {
        return lat in MIN_LAT..MAX_LAT && lng in MIN_LNG..MAX_LNG
    }

    /**
     * Resizes a blurred bitmap to <= 800 px on the longest side and compresses
     * to JPEG <= 100 KB (EXIF stripped because Bitmap re-encode omits EXIF).
     * Fails explicitly if the compressed JPEG still exceeds 100 KB.
     */
    fun encodeCompressedJpegBase64(blurredBitmap: Bitmap): Result<Triple<String, Int, Int>> {
        val scale = MAX_PHOTO_DIMENSION_PX.toFloat() /
            max(blurredBitmap.width, blurredBitmap.height).coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                blurredBitmap,
                (blurredBitmap.width * scale).roundToInt().coerceAtLeast(1),
                (blurredBitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            blurredBitmap
        }

        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_JPEG_BYTES && quality >= 35)

        if (bytes.size > MAX_JPEG_BYTES) {
            return Result.failure(
                IllegalStateException("Photo still exceeds 100 KB after compression (${bytes.size / 1024} KB). Please retake.")
            )
        }

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return Result.success(Triple(base64, scaled.width, scaled.height))
    }

    /**
     * FR3.1: Creates reports/{reportId}, reports_private/{reportId},
     * reports/{reportId}/confirmations/{uid}, and reports/{reportId}/photos/{p1..p3}
     * using FieldValue.serverTimestamp() aligned with request.time in firestore.rules.
     */
    suspend fun submitReport(
        zoneId: String,
        category: ReportCategory,
        note: String,
        lat: Double,
        lng: Double,
        photos: List<PreparedReportPhoto>
    ): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in with your UM email first."))
        if (!user.isEmailVerified || !isUmEmail(user.email)) {
            return Result.failure(IllegalStateException("Only verified @siswa.um.edu.my or @um.edu.my emails can report."))
        }
        if (zoneId !in VALID_ZONES) {
            return Result.failure(IllegalArgumentException("Invalid zone ID: $zoneId"))
        }
        if (!isInsideUm(lat, lng)) {
            return Result.failure(IllegalArgumentException("Location is outside Universiti Malaya campus bounds."))
        }
        if (photos.isEmpty() || photos.size > 3) {
            return Result.failure(IllegalArgumentException("Attach 1 to 3 blurred photos before submitting."))
        }

        // Enforce Decision 2 (Option A): max 5 reports per person per day
        val oneDayAgo = Timestamp(Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000))
        val dailySnap = db.collection("reports_private")
            .whereEqualTo("createdBy", user.uid)
            .whereGreaterThanOrEqualTo("createdAt", oneDayAgo)
            .get()
            .await()
        if (dailySnap.size() >= DAILY_REPORT_LIMIT) {
            return Result.failure(IllegalStateException("Daily limit reached (5 reports per day). Delete an older report to free a slot."))
        }

        // Enforce 1 report per zone per hour
        val oneHourAgo = Timestamp(Date(System.currentTimeMillis() - 60L * 60 * 1000))
        val zoneSnap = db.collection("reports")
            .whereEqualTo("zoneId", zoneId)
            .whereGreaterThanOrEqualTo("createdAt", oneHourAgo)
            .get()
            .await()
        if (!zoneSnap.isEmpty) {
            return Result.failure(IllegalStateException("A report for $zoneId was already posted in the last hour. Tap 'Still flooded' to confirm it instead."))
        }

        val serverTime = FieldValue.serverTimestamp()
        val reportRef = db.collection("reports").document()
        val reportId = reportRef.id
        val roundedLat = (lat * 5000.0).roundToInt() / 5000.0
        val roundedLng = (lng * 5000.0).roundToInt() / 5000.0

        val batch = db.batch()

        batch.set(
            reportRef,
            mapOf(
                "reportId" to reportId,
                "zoneId" to zoneId,
                "category" to category.firestoreValue,
                "note" to note.trim().take(300),
                "lat" to roundedLat,
                "lng" to roundedLng,
                "createdAt" to serverTime,
                "status" to "unconfirmed",
                "confirmCount" to 1,
                "photoCount" to photos.size,
                "flagCount" to 0,
                "fixedAt" to null
            )
        )

        batch.set(
            db.collection("reports_private").document(reportId),
            mapOf(
                "reportId" to reportId,
                "createdBy" to user.uid,
                "reporterEmail" to (user.email ?: ""),
                "createdAt" to serverTime
            )
        )

        batch.set(
            reportRef.collection("confirmations").document(user.uid),
            mapOf(
                "userId" to user.uid,
                "createdAt" to serverTime
            )
        )

        photos.take(3).forEachIndexed { index, photo ->
            val photoId = "p${index + 1}"
            batch.set(
                reportRef.collection("photos").document(photoId),
                mapOf(
                    "photoId" to photoId,
                    "imageBase64" to photo.imageBase64,
                    "widthPx" to photo.widthPx,
                    "heightPx" to photo.heightPx,
                    "createdAt" to serverTime
                )
            )
        }

        batch.commit().await()
        return Result.success(reportId)
    }

    suspend fun hasUserConfirmed(reportId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val snap = db.collection("reports")
            .document(reportId)
            .collection("confirmations")
            .document(uid)
            .get()
            .await()
        return snap.exists()
    }

    /**
     * FR3.2: Safe atomic confirmation using a Firestore Transaction.
     * - Checks verified UM email and client-side UM campus bounds up front
     *   (keeping locked DATA_LAYOUT.md unchanged; documented limitation: client-side only).
     * - Blocks fixed reports and stale (>6h) reports (aligned with firestore.rules).
     * - Enforces 1 vote per UID and flips status to 'confirmed' at >= 3 (or immediately for staff).
     */
    suspend fun confirmReport(
        reportId: String,
        userLat: Double? = null,
        userLng: Double? = null
    ): Result<ConfirmationOutcome> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with your UM email to confirm."))
        if (!user.isEmailVerified || !isUmEmail(user.email)) {
            return Result.failure(IllegalStateException("Only verified @siswa.um.edu.my or @um.edu.my emails can confirm."))
        }
        if (userLat != null && userLng != null && !isInsideUm(userLat, userLng)) {
            return Result.failure(IllegalArgumentException("You must be inside Universiti Malaya campus to confirm a report."))
        }

        val isStaff = try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }

        val reportRef = db.collection("reports").document(reportId)
        val confirmRef = reportRef.collection("confirmations").document(user.uid)

        return try {
            val outcome = db.runTransaction { tx ->
                val reportSnap = tx.get(reportRef)
                if (!reportSnap.exists()) {
                    throw IllegalStateException("Report not found.")
                }
                val confirmSnap = tx.get(confirmRef)
                val currentCount = (reportSnap.getLong("confirmCount") ?: 1L).toInt()
                val currentStatus = reportSnap.getString("status") ?: "unconfirmed"
                val createdAt = reportSnap.getTimestamp("createdAt")
                val isStale = createdAt != null &&
                    (System.currentTimeMillis() - createdAt.toDate().time) > ConfirmationRules.STALE_WINDOW_MS

                val transition = ConfirmationRules.nextState(
                    currentCount = currentCount,
                    currentStatus = currentStatus,
                    alreadyConfirmedByUser = confirmSnap.exists(),
                    isStaff = isStaff,
                    isStale = isStale
                ).getOrThrow()

                val (updatedCount, nextStatus) = transition

                tx.set(
                    confirmRef,
                    mapOf("userId" to user.uid, "createdAt" to FieldValue.serverTimestamp())
                )
                tx.update(
                    reportRef,
                    mapOf("confirmCount" to updatedCount, "status" to nextStatus)
                )
                ConfirmationOutcome(
                    newConfirmCount = updatedCount,
                    newStatus = nextStatus,
                    confirmedByStaff = isStaff
                )
            }.await()
            Result.success(outcome)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FR3.3: Atomic photo flag transaction (1 flag vote per verified UM student).
     */
    suspend fun flagReportPhoto(reportId: String, reason: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with your UM email to flag a photo."))
        if (!user.isEmailVerified || !isUmEmail(user.email)) {
            return Result.failure(IllegalStateException("Only verified @siswa.um.edu.my or @um.edu.my emails can flag photos."))
        }

        val reportRef = db.collection("reports").document(reportId)
        val flagRef = reportRef.collection("flags").document(user.uid)

        return try {
            db.runTransaction { tx ->
                val reportSnap = tx.get(reportRef)
                if (tx.get(flagRef).exists()) {
                    throw IllegalStateException("You have already flagged this report.")
                }
                val nextFlagCount = (reportSnap.getLong("flagCount") ?: 0L).toInt() + 1
                tx.set(
                    flagRef,
                    mapOf(
                        "userId" to user.uid,
                        "reason" to reason,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(reportRef, mapOf("flagCount" to nextFlagCount))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FR3.3: Atomically deletes the report, its reports_private record, and all
     * subcollection docs (photos, confirmations, flags) in one WriteBatch so no
     * orphan docs remain and isReporter() holds across the entire batch.
     */
    suspend fun deleteOwnReport(reportId: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with your UM email to delete your report."))
        if (!user.isEmailVerified || !isUmEmail(user.email)) {
            return Result.failure(IllegalStateException("Only verified UM accounts can delete reports."))
        }

        return try {
            val reportRef = db.collection("reports").document(reportId)
            val photosSnap = reportRef.collection("photos").get().await()
            val confirmationsSnap = reportRef.collection("confirmations").get().await()
            val flagsSnap = reportRef.collection("flags").get().await()

            val batch = db.batch()
            photosSnap.documents.forEach { batch.delete(it.reference) }
            confirmationsSnap.documents.forEach { batch.delete(it.reference) }
            flagsSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(reportRef)
            batch.delete(db.collection("reports_private").document(reportId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
