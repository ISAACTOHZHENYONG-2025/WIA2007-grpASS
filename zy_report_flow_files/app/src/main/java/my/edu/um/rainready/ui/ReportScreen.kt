package my.edu.um.rainready.ui

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import my.edu.um.rainready.data.PhotoBlurProcessor
import my.edu.um.rainready.data.PlaceholderBlurProcessor
import my.edu.um.rainready.data.PreparedReportPhoto
import my.edu.um.rainready.data.ReportCategory
import my.edu.um.rainready.data.ReportRepository

/**
 * FR3.1 Report Screen (zy) with FR5.2 Face-Blur Hook (Chen Xi - PR #7).
 * - Displays blurred photo previews BEFORE upload.
 * - Blocks upload if face blur fails on any selected photo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    repository: ReportRepository = remember { ReportRepository() },
    photoBlurProcessor: PhotoBlurProcessor = PlaceholderBlurProcessor,
    onReportSubmitted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedZone by remember { mutableStateOf("Z6") }
    var selectedCategory by remember { mutableStateOf(ReportCategory.FLOOD) }
    var note by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("3.1203") }
    var lngText by remember { mutableStateOf("101.6543") }

    val preparedPhotos = remember { mutableStateListOf<PreparedReportPhoto>() }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var blurFailed by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || preparedPhotos.size >= 3) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessingPhoto = true
            blurFailed = false
            statusMessage = null
            try {
                val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                // FR5.2: Run face blur BEFORE showing preview or allowing upload
                val blurResult = photoBlurProcessor.blurFaces(rawBitmap)
                blurResult.onSuccess { blurredBitmap ->
                    val (base64, w, h) = repository.encodeCompressedJpegBase64(blurredBitmap)
                    preparedPhotos.add(
                        PreparedReportPhoto(
                            id = "p${preparedPhotos.size + 1}",
                            blurredBitmap = blurredBitmap,
                            imageBase64 = base64,
                            widthPx = w,
                            heightPx = h
                        )
                    )
                }.onFailure { err ->
                    blurFailed = true
                    statusMessage = "Upload blocked: face blur failed (${err.message ?: "unknown error"}). Please retake the photo."
                }
            } catch (e: Exception) {
                blurFailed = true
                statusMessage = "Could not load photo: ${e.message}"
            } finally {
                isProcessingPhoto = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Report a Campus Hazard", style = MaterialTheme.typography.headlineSmall)

        Text(text = "1. Zone (Z1–Z10)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..10).forEach { idx ->
                val zone = "Z$idx"
                FilterChip(
                    selected = selectedZone == zone,
                    onClick = { selectedZone = zone },
                    label = { Text(zone) }
                )
            }
        }

        Text(text = "2. Hazard Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.label) }
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 300) note = it },
            label = { Text("Short note (optional, max 300 chars)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lngText,
                onValueChange = { lngText = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f)
            )
        }

        Text(text = "3. Live Photos (1–3, faces blurred before upload)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !isProcessingPhoto && preparedPhotos.size < 3
            ) {
                Text(if (isProcessingPhoto) "Blurring..." else "Add Photo (${preparedPhotos.size}/3)")
            }
            if (preparedPhotos.isNotEmpty()) {
                OutlinedButton(onClick = {
                    preparedPhotos.clear()
                    blurFailed = false
                }) {
                    Text("Clear Photos")
                }
            }
        }

        // Blurred preview row shown BEFORE upload
        if (preparedPhotos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preparedPhotos.forEach { photo ->
                    Image(
                        bitmap = photo.blurredBitmap.asImageBitmap(),
                        contentDescription = "Blurred preview ${photo.id}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }

        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()
                if (lat == null || lng == null) {
                    statusMessage = "Enter valid numeric latitude and longitude."
                    return@Button
                }
                scope.launch {
                    isSubmitting = true
                    statusMessage = null
                    val result = repository.submitReport(
                        zoneId = selectedZone,
                        category = selectedCategory,
                        note = note,
                        lat = lat,
                        lng = lng,
                        photos = preparedPhotos.toList()
                    )
                    isSubmitting = false
                    result.onSuccess { reportId ->
                        preparedPhotos.clear()
                        note = ""
                        onReportSubmitted(reportId)
                    }.onFailure { err ->
                        statusMessage = err.message ?: "Failed to submit report."
                    }
                }
            },
            enabled = !isSubmitting && !isProcessingPhoto && !blurFailed && preparedPhotos.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Submit Report")
            }
        }
    }
}
