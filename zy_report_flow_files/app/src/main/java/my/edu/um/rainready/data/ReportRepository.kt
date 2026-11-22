package my.edu.um.rainready.data

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

class ReportRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val MAX_PHOTO_DIMENSION_PX = 800
        private const val MAX_JPEG_BYTES = 100 * 1024
        private const val DAILY_REPORT_LIMIT = 5
        private const val MIN_LAT = 3.108
        private const val MAX_LAT = 3.135
        private const val MIN_LNG = 101.643
        private const val MAX_LNG = 101.666
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
     */
    fun encodeCompressedJpegBase64(blurredBitmap: Bitmap): Triple<String, Int, Int> {
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

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return Triple(base64, scaled.width, scaled.height)
    }

    /**
     * FR3.1: Creates reports/{reportId}, reports_private/{reportId},
     * reports/{reportId}/confirmations/{uid}, and reports/{reportId}/photos/{p1..p3}.
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

        val now = Timestamp.now()
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
                "createdAt" to now,
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
                "createdAt" to now
            )
        )

        batch.set(
            reportRef.collection("confirmations").document(user.uid),
            mapOf(
                "userId" to user.uid,
                "createdAt" to now
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
                    "createdAt" to now
                )
            )
        }

        batch.commit().await()
        return Result.success(reportId)
    }

    /**
     * FR3.2: Atomic confirmation transaction (1 vote per UID; flips to 'confirmed' at >= 3).
     */
    suspend fun confirmReport(reportId: String): Result<Int> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with your UM email to confirm."))

        val reportRef = db.collection("reports").document(reportId)
        val confirmRef = reportRef.collection("confirmations").document(user.uid)

        return try {
            val newCount = db.runTransaction { tx ->
                val reportSnap = tx.get(reportRef)
                val confirmSnap = tx.get(confirmRef)
                if (confirmSnap.exists()) {
                    throw IllegalStateException("You have already confirmed this report.")
                }
                val currentCount = (reportSnap.getLong("confirmCount") ?: 1L).toInt()
                val updatedCount = currentCount + 1
                val currentStatus = reportSnap.getString("status") ?: "unconfirmed"
                val nextStatus = if (updatedCount >= 3 && currentStatus == "unconfirmed") {
                    "confirmed"
                } else {
                    currentStatus
                }

                tx.set(
                    confirmRef,
                    mapOf("userId" to user.uid, "createdAt" to Timestamp.now())
                )
                tx.update(
                    reportRef,
                    mapOf("confirmCount" to updatedCount, "status" to nextStatus)
                )
                updatedCount
            }.await()
            Result.success(newCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FR3.3: Atomic photo flag transaction (1 flag vote per student).
     */
    suspend fun flagReportPhoto(reportId: String, reason: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with your UM email to flag a photo."))

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
                        "createdAt" to Timestamp.now()
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
     * FR3.3: Allows the original reporter to delete their own report.
     */
    suspend fun deleteOwnReport(reportId: String): Result<Unit> {
        return try {
            val batch = db.batch()
            batch.delete(db.collection("reports").document(reportId))
            batch.delete(db.collection("reports_private").document(reportId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
