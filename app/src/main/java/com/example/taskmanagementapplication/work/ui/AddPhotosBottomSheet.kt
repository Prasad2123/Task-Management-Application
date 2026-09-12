package com.example.taskmanagementapplication.work.ui

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.ui.PhotoThumbnailView

enum class AddPhotoMode {
    OPTIONS,
    TAKE_PHOTO,
    GALLERY
}

private data class MockGalleryItem(
    val id: String,
    val title: String,
    val category: PhotoCategory,
    val seed: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPhotosBottomSheet(
    onDismiss: () -> Unit,
    onPhotosSelected: (List<WorkPhoto>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentMode by remember { mutableStateOf(AddPhotoMode.OPTIONS) }

    // State for Take Photo mode
    var takePhotoCategory by remember { mutableStateOf(PhotoCategory.SITE_INSPECTION) }
    var takePhotoTitle by remember { mutableStateOf("Site Inspection Photo") }
    var takePhotoCaption by remember { mutableStateOf("") }

    // State for Gallery mode
    val mockGallery = remember {
        listOf(
            MockGalleryItem("G1", "Main Entryway Scan", PhotoCategory.SITE_INSPECTION, 1),
            MockGalleryItem("G2", "Sprayer Pressure Gauge", PhotoCategory.EQUIPMENT_CHECK, 2),
            MockGalleryItem("G3", "Chemical Treatment Zone", PhotoCategory.TREATMENT_APPLICATION, 3),
            MockGalleryItem("G4", "Safety Mask & Gloves", PhotoCategory.SAFETY_PPE, 4),
            MockGalleryItem("G5", "Sealed Pipe Fitting", PhotoCategory.ADDITIONAL_WORK, 5),
            MockGalleryItem("G6", "Perimeter Fence Bait", PhotoCategory.GENERAL, 6)
        )
    }
    val selectedGalleryIds = remember { mutableStateListOf<String>() }
    var gallerySharedCaption by remember { mutableStateOf("") }

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
                            text = "Capture field evidence or choose existing photos from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Option 1: Take Photo
                        OptionCard(
                            icon = Icons.Default.CameraAlt,
                            title = "Take Photo",
                            subtitle = "Capture a real-time evidence photo with camera simulator",
                            onClick = {
                                takePhotoTitle = "Site Evidence #${(100..999).random()}"
                                currentMode = AddPhotoMode.TAKE_PHOTO
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
                                selectedGalleryIds.clear()
                                currentMode = AddPhotoMode.GALLERY
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

                // ── TAKE PHOTO SIMULATOR ──
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
                                text = "Take Work Photo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Mock Viewfinder Preview
                        val previewPhoto = remember(takePhotoCategory, takePhotoTitle) {
                            WorkPhoto(
                                id = "PREV_${System.currentTimeMillis()}",
                                title = takePhotoTitle,
                                category = takePhotoCategory,
                                uploadedAt = "Just now",
                                uploadStatus = PhotoUploadStatus.UPLOADING,
                                gradientSeed = takePhotoCategory.ordinal + 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            PhotoThumbnailView(photo = previewPhoto, modifier = Modifier.fillMaxSize())

                            // Camera simulator tag
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
                                            .background(Color.Red, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE VIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
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

                        // Capture Button
                        Button(
                            onClick = {
                                val newPhoto = WorkPhoto(
                                    id = "P_${System.currentTimeMillis()}",
                                    title = takePhotoTitle,
                                    category = takePhotoCategory,
                                    uploadedAt = "Just now",
                                    uploadStatus = PhotoUploadStatus.UPLOADING,
                                    caption = takePhotoCaption.trim().takeIf { it.isNotBlank() },
                                    uploadProgress = 0.4f,
                                    gradientSeed = (1..6).random()
                                )
                                onPhotosSelected(listOf(newPhoto))
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Capture and upload photo" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture & Upload Photo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // ── GALLERY MULTI-SELECTION SIMULATOR ──
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
                                    text = "Choose from Gallery",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedGalleryIds.isEmpty()) "Tap photos to select multiple" else "${selectedGalleryIds.size} of ${mockGallery.size} selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedGalleryIds.isNotEmpty()) PrimaryLight else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selectedGalleryIds.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Grid of selectable mock photos
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            items(mockGallery) { item ->
                                val isSelected = selectedGalleryIds.contains(item.id)
                                val itemPhoto = WorkPhoto(
                                    id = item.id,
                                    title = item.title,
                                    category = item.category,
                                    uploadedAt = "Gallery",
                                    uploadStatus = PhotoUploadStatus.UPLOADED,
                                    gradientSeed = item.seed
                                )

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) PrimaryLight else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (isSelected) {
                                                selectedGalleryIds.remove(item.id)
                                            } else {
                                                selectedGalleryIds.add(item.id)
                                            }
                                        }
                                        .semantics { contentDescription = "Select ${item.title}" }
                                ) {
                                    PhotoThumbnailView(photo = itemPhoto, modifier = Modifier.fillMaxSize())

                                    // Selection check badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) PrimaryLight else Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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

                        Spacer(modifier = Modifier.height(18.dp))

                        // Add X Photos CTA Button
                        val selectedCount = selectedGalleryIds.size
                        Button(
                            onClick = {
                                val selectedPhotos = mockGallery
                                    .filter { it.id in selectedGalleryIds }
                                    .mapIndexed { index, item ->
                                        WorkPhoto(
                                            id = "GAL_${System.currentTimeMillis()}_$index",
                                            title = item.title,
                                            category = item.category,
                                            uploadedAt = "Just now",
                                            uploadStatus = PhotoUploadStatus.UPLOADING,
                                            caption = gallerySharedCaption.trim().takeIf { it.isNotBlank() },
                                            uploadProgress = 0.45f,
                                            gradientSeed = item.seed
                                        )
                                    }
                                onPhotosSelected(selectedPhotos)
                                onDismiss()
                            },
                            enabled = selectedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Add $selectedCount photos" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                        ) {
                            Text(
                                text = if (selectedCount > 0) "Add $selectedCount Photo${if (selectedCount > 1) "s" else ""}" else "Select Photos to Add",
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
