package com.example.taskmanagementapplication.work.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.ui.PhotoThumbnailView
import java.io.File

enum class AddPhotoMode {
    OPTIONS,
    TAKE_PHOTO,
    GALLERY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPhotosBottomSheet(
    onDismiss: () -> Unit,
    onPhotosSelected: (List<WorkPhoto>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentMode by remember { mutableStateOf(AddPhotoMode.OPTIONS) }

    // State for Take Photo mode
    var takePhotoCategory by remember { mutableStateOf(PhotoCategory.SITE_INSPECTION) }
    var takePhotoTitle by remember { mutableStateOf("Site Inspection Photo") }
    var takePhotoCaption by remember { mutableStateOf("") }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // State for Gallery mode
    val selectedGalleryUris = remember { mutableStateListOf<Uri>() }
    var galleryCategory by remember { mutableStateOf(PhotoCategory.SITE_INSPECTION) }
    var gallerySharedCaption by remember { mutableStateOf("") }

    // Camera Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            capturedPhotoUri = pendingCameraUri
        }
    }

    // Permission Launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    // Gallery Picker Launcher
    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedGalleryUris.clear()
            selectedGalleryUris.addAll(uris)
            currentMode = AddPhotoMode.GALLERY
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val uri = createTempImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AddPhotoFlow"
        ) { mode ->
            when (mode) {
                // ── INITIAL OPTIONS ──
                AddPhotoMode.OPTIONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Add Work Photos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Capture field evidence with your camera or select photos from device storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Option 1: Take Photo
                        OptionCard(
                            icon = Icons.Default.CameraAlt,
                            title = "Take Photo",
                            subtitle = "Capture real-time field evidence using device camera",
                            onClick = {
                                takePhotoTitle = "${takePhotoCategory.displayName} #${(100..999).random()}"
                                currentMode = AddPhotoMode.TAKE_PHOTO
                                launchCamera()
                            },
                            contentDesc = "Take photo option"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Option 2: Choose from Gallery
                        OptionCard(
                            icon = Icons.Default.PhotoLibrary,
                            title = "Choose from Gallery",
                            subtitle = "Select one or multiple photos to upload as work evidence",
                            onClick = {
                                pickMultipleMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentDesc = "Choose from gallery option"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }

                // ── TAKE PHOTO ──
                AddPhotoMode.TAKE_PHOTO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { currentMode = AddPhotoMode.OPTIONS }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to options")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Camera Evidence",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Captured Photo or Viewfinder Preview
                        val previewPhoto = remember(capturedPhotoUri, takePhotoCategory, takePhotoTitle) {
                            WorkPhoto(
                                id = "PREV_${System.currentTimeMillis()}",
                                title = takePhotoTitle,
                                category = takePhotoCategory,
                                uploadedAt = "Just now",
                                uploadStatus = PhotoUploadStatus.UPLOADING,
                                gradientSeed = takePhotoCategory.ordinal + 1,
                                localUri = capturedPhotoUri?.toString()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            PhotoThumbnailView(photo = previewPhoto, modifier = Modifier.fillMaxSize())

                            // Status tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (capturedPhotoUri != null) Color(0xFF4CAF50) else Color.Red, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (capturedPhotoUri != null) "PHOTO CAPTURED" else "READY TO CAPTURE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // Retake / Open Camera Button Overlay
                            Button(
                                onClick = { launchCamera() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    if (capturedPhotoUri != null) Icons.Default.Refresh else Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (capturedPhotoUri != null) "Retake" else "Open Camera",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category Selector Chips
                        Text(
                            text = "Select Category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PhotoCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = takePhotoCategory == category,
                                    onClick = {
                                        takePhotoCategory = category
                                        takePhotoTitle = "${category.displayName} #${(100..999).random()}"
                                    },
                                    label = { Text(category.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryLight,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Optional Caption Note
                        OutlinedTextField(
                            value = takePhotoCaption,
                            onValueChange = { takePhotoCaption = it },
                            label = { Text("Add note / caption (optional)") },
                            placeholder = { Text("e.g., Before treatment, Damaged pipe...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Photo caption input" },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Capture & Upload Button
                        Button(
                            onClick = {
                                val photoToUpload = WorkPhoto(
                                    id = "P_${System.currentTimeMillis()}",
                                    title = takePhotoTitle,
                                    category = takePhotoCategory,
                                    uploadedAt = "Just now",
                                    uploadStatus = PhotoUploadStatus.UPLOADING,
                                    caption = takePhotoCaption.trim().takeIf { it.isNotBlank() },
                                    uploadProgress = 0.35f,
                                    gradientSeed = (1..6).random(),
                                    localUri = capturedPhotoUri?.toString()
                                )
                                onPhotosSelected(listOf(photoToUpload))
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Upload captured photo" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (capturedPhotoUri != null) "Upload Captured Photo" else "Capture & Upload Photo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // ── GALLERY SELECTION ──
                AddPhotoMode.GALLERY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { currentMode = AddPhotoMode.OPTIONS }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to options")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Selected Photos",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedGalleryUris.size} photo${if (selectedGalleryUris.size != 1) "s" else ""} ready to upload",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = {
                                    pickMultipleMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add more photos", tint = PrimaryLight)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Grid of selected photos
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            items(selectedGalleryUris) { uri ->
                                val itemPhoto = WorkPhoto(
                                    id = uri.toString(),
                                    title = "Evidence",
                                    category = galleryCategory,
                                    uploadedAt = "Gallery",
                                    uploadStatus = PhotoUploadStatus.UPLOADED,
                                    localUri = uri.toString()
                                )

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.5.dp,
                                            color = PrimaryLight,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    PhotoThumbnailView(photo = itemPhoto, modifier = Modifier.fillMaxSize())

                                    // Check badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category selection for the batch
                        Text(
                            text = "Photo Category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PhotoCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = galleryCategory == category,
                                    onClick = { galleryCategory = category },
                                    label = { Text(category.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryLight,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Optional shared caption note
                        OutlinedTextField(
                            value = gallerySharedCaption,
                            onValueChange = { gallerySharedCaption = it },
                            label = { Text("Add note for selected photos (optional)") },
                            placeholder = { Text("e.g., Perimeter evidence, After treatment...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Gallery caption input" },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Upload CTA Button
                        val selectedCount = selectedGalleryUris.size
                        Button(
                            onClick = {
                                val selectedPhotos = selectedGalleryUris.mapIndexed { index, uri ->
                                    WorkPhoto(
                                        id = "GAL_${System.currentTimeMillis()}_$index",
                                        title = "${galleryCategory.displayName} #${index + 1}",
                                        category = galleryCategory,
                                        uploadedAt = "Just now",
                                        uploadStatus = PhotoUploadStatus.UPLOADING,
                                        caption = gallerySharedCaption.trim().takeIf { it.isNotBlank() },
                                        uploadProgress = 0.35f,
                                        gradientSeed = index + 1,
                                        localUri = uri.toString()
                                    )
                                }
                                onPhotosSelected(selectedPhotos)
                                onDismiss()
                            },
                            enabled = selectedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Upload $selectedCount photos" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                        ) {
                            Text(
                                text = if (selectedCount > 0) "Upload $selectedCount Photo${if (selectedCount > 1) "s" else ""}" else "Select Photos to Upload",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    contentDesc: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryLight.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryLight,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "photos")
    photoDir.mkdirs()
    val file = File(photoDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
