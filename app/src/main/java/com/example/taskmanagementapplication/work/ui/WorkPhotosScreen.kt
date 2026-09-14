package com.example.taskmanagementapplication.work.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress
import com.example.taskmanagementapplication.core.ui.PhotoCard
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPhotosScreen(
    onBack: () -> Unit,
    onSubmitForReview: () -> Unit = {},
    workViewModel: WorkViewModel = viewModel()
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val photos = workViewModel.getPhotos(work)
    val totalCount = workViewModel.getTotalPhotosCount(work)
    val uploadedCount = workViewModel.getUploadedPhotosCount(work)
    val uploadingCount = workViewModel.getUploadingPhotosCount(work)
    val failedCount = workViewModel.getFailedPhotosCount(work)
    val pendingCount = workViewModel.getPendingPhotosCount(work)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Bottom sheet state
    var showAddPhotosSheet by remember { mutableStateOf(false) }

    // Viewer state
    var viewingPhotoIndex by remember { mutableStateOf<Int?>(null) }

    // Delete confirmation dialog
    var photoToDelete by remember { mutableStateOf<WorkPhoto?>(null) }

    // Submit for review confirmation dialog
    var showSubmitConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(work.backendId) {
        work.backendId?.let { workViewModel.loadPhotos(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                    ) {
                        Column {
                            Text("Work Photos", fontWeight = FontWeight.Bold)
                            Text(
                                text = "$totalCount Photo${if (totalCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // IN PROGRESS status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusInProgress.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusInProgress)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "IN PROGRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusInProgress,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back from Work Photos" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            work.backendId?.let {
                                workViewModel.loadPhotos(it)
                                scope.launch { snackbarHostState.showSnackbar("Photos refreshed") }
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Refresh photos" }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── EVIDENCE SUMMARY HEADER ──
            item(span = { GridItemSpan(2) }) {
                EvidenceSummaryHeader(
                    totalCount = totalCount,
                    uploadedCount = uploadedCount,
                    inProgressCount = uploadingCount + pendingCount,
                    failedCount = failedCount
                )
            }

            // ── PRIMARY ACTION: ADD PHOTOS ──
            item(span = { GridItemSpan(2) }) {
                Button(
                    onClick = { showAddPhotosSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Add photos button" },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+ Add Photos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // ── PHOTO GRID OR EMPTY STATE ──
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyPhotosState(
                        onAddPhotos = { showAddPhotosSheet = true }
                    )
                }
            } else {
                itemsIndexed(
                    items = photos,
                    key = { _, photo -> photo.id }
                ) { index, photo ->
                    PhotoCard(
                        photo = photo,
                        onClick = { viewingPhotoIndex = index },
                        onDelete = { photoToDelete = photo },
                        onRetry = {
                            workViewModel.retryPhotoUpload(photo.id)
                            scope.launch { snackbarHostState.showSnackbar("Retrying photo upload...") }
                        }
                    )
                }
            }

            // ── SUBMIT WORK FOR REVIEW CTA ──
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showSubmitConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Submit Work for Review button" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totalCount > 0) StatusCompleted else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (totalCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Submit Work for Review",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // ── ADD PHOTOS BOTTOM SHEET ──
    if (showAddPhotosSheet) {
        AddPhotosBottomSheet(
            onDismiss = { showAddPhotosSheet = false },
            onPhotosSelected = { selectedPhotos ->
                workViewModel.addPhotos(selectedPhotos)
                showAddPhotosSheet = false
                val count = selectedPhotos.size
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (count == 1) "1 photo added, uploading..." else "$count photos added, uploading..."
                    )
                }
            }
        )
    }

    // ── FULL SCREEN PHOTO VIEWER ──
    viewingPhotoIndex?.let { index ->
        PhotoViewerDialog(
            photos = photos,
            initialIndex = index,
            onDismiss = { viewingPhotoIndex = null },
            onDeletePhoto = { photoId ->
                workViewModel.deletePhoto(photoId)
                scope.launch { snackbarHostState.showSnackbar("Photo removed") }
            },
            onRetryPhoto = { photoId ->
                workViewModel.retryPhotoUpload(photoId)
                scope.launch { snackbarHostState.showSnackbar("Retrying photo upload...") }
            }
        )
    }

    // ── DELETE CONFIRMATION DIALOG ──
    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = {
                Text("Delete this photo?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to remove this work photo? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedTitle = photo.title
                        workViewModel.deletePhoto(photo.id)
                        photoToDelete = null
                        scope.launch { snackbarHostState.showSnackbar("Photo removed: \"$deletedTitle\"") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics { contentDescription = "Confirm delete photo" }
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { photoToDelete = null },
                    modifier = Modifier.semantics { contentDescription = "Cancel photo deletion" }
                ) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── SUBMIT FOR REVIEW CONFIRMATION DIALOG ──
    if (showSubmitConfirmation) {
        val totalChecklist = workViewModel.getTotalCount(work)
        val completedChecklist = workViewModel.getTotalCompletedCount(work)
        val hasIncompleteChecklist = completedChecklist < totalChecklist
        val hasNoPhotos = totalCount == 0

        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            icon = {
                Icon(
                    imageVector = if (hasNoPhotos || hasIncompleteChecklist) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasNoPhotos || hasIncompleteChecklist) AccentOrange else StatusCompleted,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Ready to submit?", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Make sure your checklist and work photos are complete before submitting for review.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (hasNoPhotos || hasIncompleteChecklist) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentOrange.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (hasNoPhotos) {
                                    Text(
                                        text = "• Warning: No photos added yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentOrange
                                    )
                                }
                                if (hasIncompleteChecklist) {
                                    Text(
                                        text = "• Warning: $completedChecklist of $totalChecklist checklist tasks completed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentOrange
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmation = false
                        workViewModel.submitWorkForReview()
                        onSubmitForReview()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                    modifier = Modifier.semantics { contentDescription = "Confirm review submission" }
                ) {
                    Text("SUBMIT FOR REVIEW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSubmitConfirmation = false },
                    modifier = Modifier.semantics { contentDescription = "Cancel submission" }
                ) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun EvidenceSummaryHeader(
    totalCount: Int,
    uploadedCount: Int,
    inProgressCount: Int,
    failedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Work Evidence",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Photos help verify the work completed at the site.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metric summary grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EvidenceMetricChip(
                    label = "Photos",
                    value = totalCount.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                EvidenceMetricChip(
                    label = "Uploaded",
                    value = uploadedCount.toString(),
                    color = StatusCompleted,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                EvidenceMetricChip(
                    label = "Pending",
                    value = inProgressCount.toString(),
                    color = if (inProgressCount > 0) StatusInProgress else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (failedCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    EvidenceMetricChip(
                        label = "Failed",
                        value = failedCount.toString(),
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceMetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EmptyPhotosState(
    onAddPhotos: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = PrimaryLight,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No work photos yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add photos while you work to keep a record of the work performed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddPhotos,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Add Photos", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
