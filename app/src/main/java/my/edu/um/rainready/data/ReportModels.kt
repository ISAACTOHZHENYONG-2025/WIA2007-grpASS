package my.edu.um.rainready.data

import android.graphics.Bitmap
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import my.edu.um.rainready.faceblur.FaceBlurProcessor

enum class ReportCategory(val firestoreValue: String, val label: String) {
    FLOOD("flood", "Flood"),
    FALLEN_TREE("fallen tree", "Fallen tree"),
    BLOCKED_DRAIN("blocked drain", "Blocked drain")
}

data class ReportItem(
    val reportId: String = "",
    val zoneId: String = "",
    val category: String = ReportCategory.FLOOD.firestoreValue,
    val note: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val createdAt: Timestamp = Timestamp.now(),
    val status: String = "unconfirmed",
    val confirmCount: Int = 1,
    val photoCount: Int = 0,
    val flagCount: Int = 0,
    val fixedAt: Timestamp? = null
)

data class PreparedReportPhoto(
    val id: String,
    val blurredBitmap: Bitmap,
    val imageBase64: String,
    val widthPx: Int,
    val heightPx: Int
)

/**
 * Hook for Chen Xi (PR #7 - FR5.2 Face Blur).
 * Implement this interface using on-device ML Kit Face Detection + blur,
 * returning Result.failure(...) if face blur fails so upload is blocked.
 */
fun interface PhotoBlurProcessor {
    suspend fun blurFaces(sourceBitmap: Bitmap): Result<Bitmap>
}

object MlKitPhotoBlurProcessor : PhotoBlurProcessor {
    override suspend fun blurFaces(sourceBitmap: Bitmap): Result<Bitmap> {
        val processor = FaceBlurProcessor()
        return try {
            Result.success(processor.process(sourceBitmap).await().bitmap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            processor.close()
        }
    }
}
