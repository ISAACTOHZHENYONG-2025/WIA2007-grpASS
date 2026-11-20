
package my.edu.um.rainready.faceblur

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min

data class FaceBlurResult(
    val bitmap: Bitmap,
    val facesDetected: Int
)

class FaceBlurProcessor {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(
            FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
        )
        .build()

    private val detector = FaceDetection.getClient(options)

    fun process(bitmap: Bitmap): Task<FaceBlurResult> {
        val result = TaskCompletionSource<FaceBlurResult>()

        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    try {
                        val output = bitmap.copy(
                            Bitmap.Config.ARGB_8888,
                            true
                        ) ?: throw IllegalStateException(
                            "Could not create editable image"
                        )

                        val canvas = Canvas(output)

                        for (face in faces) {
                            val bounds = face.boundingBox

                            val left = max(0, bounds.left)
                            val top = max(0, bounds.top)
                            val right = min(bitmap.width, bounds.right)
                            val bottom = min(bitmap.height, bounds.bottom)

                            if (right <= left || bottom <= top) {
                                throw IllegalStateException(
                                    "Invalid face boundary"
                                )
                            }

                            pixelateFace(
                                bitmap,
                                canvas,
                                Rect(left, top, right, bottom)
                            )
                        }

                        result.setResult(
                            FaceBlurResult(output, faces.size)
                        )
                    } catch (error: Exception) {
                        result.setException(error)
                    }
                }
                .addOnFailureListener { error ->
                    result.setException(error)
                }

        } catch (error: Exception) {
            result.setException(error)
        }

        return result.task
    }

    private fun pixelateFace(
        source: Bitmap,
        canvas: Canvas,
        area: Rect
    ) {
        val width = area.width()
        val height = area.height()

        val cropped = Bitmap.createBitmap(
            source,
            area.left,
            area.top,
            width,
            height
        )

        val pixelWidth = max(1, width / 16)
        val pixelHeight = max(1, height / 16)

        val small = Bitmap.createScaledBitmap(
            cropped,
            pixelWidth,
            pixelHeight,
            false
        )

        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }

        canvas.drawBitmap(
            small,
            null,
            area,
            paint
        )

        small.recycle()
        cropped.recycle()
    }

    fun close() {
        detector.close()
    }
}
