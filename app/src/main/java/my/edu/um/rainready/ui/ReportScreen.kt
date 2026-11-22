package my.edu.um.rainready.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.edu.um.rainready.data.MlKitPhotoBlurProcessor
import my.edu.um.rainready.data.PhotoBlurProcessor
import my.edu.um.rainready.data.PreparedReportPhoto
import my.edu.um.rainready.data.ReportCategory
import my.edu.um.rainready.data.ReportRepository

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    repository: ReportRepository = remember { ReportRepository() },
    photoBlurProcessor: PhotoBlurProcessor = MlKitPhotoBlurProcessor,
    onReportSubmitted: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    var location by remember { mutableStateOf<ReportLocation?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(ReportCategory.FLOOD) }
    var note by remember { mutableStateOf("") }

    val preparedPhotos = remember { mutableStateListOf<PreparedReportPhoto>() }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var blurFailed by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun captureLocation() {
        if (isLocating || isSubmitting) return

        isLocating = true
        location = null
        locationMessage = null
        scope.launch {
            try {
                location = currentReportLocation(context, repository)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                locationMessage = e.message ?: "Could not get your location. Try again."
            } finally {
                isLocating = false
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            captureLocation()
        } else {
            locationMessage = "Allow precise location to report a hazard."
        }
    }

    fun addPhotos(uris: List<Uri>, releasePermission: () -> Unit = {}) {
        if (uris.isEmpty() || isProcessingPhoto || isSubmitting || preparedPhotos.size >= 3) {
            releasePermission()
            return
        }

        isProcessingPhoto = true
        blurFailed = false
        statusMessage = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val remaining = 3 - preparedPhotos.size
                for (uri in uris.take(remaining)) {
                    val rawBitmap = withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    }

                    val blurredBitmap = photoBlurProcessor.blurFaces(rawBitmap).getOrThrow()
                    val (base64, width, height) = withContext(Dispatchers.Default) {
                        repository.encodeCompressedJpegBase64(blurredBitmap)
                    }
                    preparedPhotos.add(
                        PreparedReportPhoto(
                            id = "p${preparedPhotos.size + 1}",
                            blurredBitmap = blurredBitmap,
                            imageBase64 = base64,
                            widthPx = width,
                            heightPx = height,
                        ),
                    )
                }
                if (uris.size > remaining) {
                    statusMessage = "Only up to 3 photos can be attached."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                blurFailed = true
                statusMessage = "Could not prepare the photo. Try another photo or clear the selection."
            } finally {
                releasePermission()
                isProcessingPhoto = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { addPhotos(listOf(it)) }
    }

    fun receivePhotoDrop(event: DragAndDropEvent): Boolean {
        if (isProcessingPhoto || isSubmitting || preparedPhotos.size >= 3) return false

        val host = activity ?: return false
        val androidEvent = event.toAndroidDragEvent()
        val clip = androidEvent.clipData ?: return false
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isEmpty()) return false

        val permission = host.requestDragAndDropPermissions(androidEvent)
        addPhotos(uris) { permission?.release() }
        return true
    }

    val receiveDrop by rememberUpdatedState(::receivePhotoDrop)
    val photoDropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean = receiveDrop(event)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, enabled = !isSubmitting) {
                Text("Back")
            }
            Text(
                text = "Report a Campus Hazard",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Text("1. Location", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                val precisePermission = Manifest.permission.ACCESS_FINE_LOCATION
                if (ContextCompat.checkSelfPermission(context, precisePermission) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    captureLocation()
                } else {
                    locationPermission.launch(
                        arrayOf(precisePermission, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
            },
            enabled = !isLocating && !isSubmitting,
        ) {
            Text(
                when {
                    isLocating -> "Getting location..."
                    location != null -> "Refresh my location"
                    else -> "Use my location"
                },
            )
        }
        if (location != null) {
            Text("Location captured", style = MaterialTheme.typography.bodySmall)
        }
        locationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("2. Hazard Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.label) },
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 300) note = it },
            label = { Text("Short note (optional, max 300 chars)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "3. Photos (1-3, faces blurred before upload)",
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = { imagePicker.launch("image/*") },
            enabled = !isProcessingPhoto && !isSubmitting && preparedPhotos.size < 3,
            modifier = Modifier
                .fillMaxWidth()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        !isProcessingPhoto && !isSubmitting && preparedPhotos.size < 3 &&
                                event.toAndroidDragEvent().clipDescription?.hasMimeType("image/*") == true
                    },
                    target = photoDropTarget,
                ),
        ) {
            Text(if (isProcessingPhoto) "Blurring..." else "Add or drop photos (${preparedPhotos.size}/3)")
        }

        if (preparedPhotos.isNotEmpty() || blurFailed) {
            OutlinedButton(
                onClick = {
                    preparedPhotos.clear()
                    blurFailed = false
                    statusMessage = null
                },
                enabled = !isProcessingPhoto && !isSubmitting,
            ) {
                Text("Clear Photos")
            }
        }

        if (preparedPhotos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preparedPhotos.forEach { photo ->
                    Image(
                        bitmap = photo.blurredBitmap.asImageBitmap(),
                        contentDescription = "Blurred preview ${photo.id}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }

        statusMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val captured = location ?: return@Button
                isSubmitting = true
                statusMessage = null
                scope.launch {
                    try {
                        val reportId = repository.submitReport(
                            zoneId = captured.zoneId,
                            category = selectedCategory,
                            note = note,
                            lat = captured.latitude,
                            lng = captured.longitude,
                            photos = preparedPhotos.toList(),
                        ).getOrThrow()

                        preparedPhotos.clear()
                        note = ""
                        location = null
                        onReportSubmitted(reportId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        statusMessage = e.message ?: "Failed to submit report."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = location != null && !isLocating && !isSubmitting &&
                    !isProcessingPhoto && !blurFailed && preparedPhotos.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Submit Report")
            }
        }
    }
}