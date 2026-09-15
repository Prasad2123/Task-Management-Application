package com.example.taskmanagementapplication.work.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Schedule
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.ui.PhotoThumbnailView
import com.example.taskmanagementapplication.core.ui.SecondaryButton
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.core.ui.SwipeActionButton
import com.example.taskmanagementapplication.core.util.DateTimeUtils
import com.example.taskmanagementapplication.core.ui.TimelineItem
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkInProgressScreen(
    onBack: () -> Unit,
    onViewDetails: () -> Unit,
    onViewChecklist: () -> Unit = {},
    onAddPhotos: () -> Unit,
    onNavigateToCompleteAndSubmit: () -> Unit = {},
    onSubmitForReview: () -> Unit = {},
    onViewApprovalStatus: () -> Unit = {},
    onProceedToComplete: () -> Unit = {},
    workViewModel: WorkViewModel = viewModel()
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val elapsedSeconds by workViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    var showAdditionalWorkSheet by remember { mutableStateOf(false) }
    var showAddPhotoSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val predefinedItems = workViewModel.getPredefinedItems(work)
    val additionalItems = workViewModel.getAdditionalItems(work)
    val totalCompleted = workViewModel.getTotalCompletedCount(work)
    val totalCount = workViewModel.getTotalCount(work)
    val checklistProgress = workViewModel.getProgressFraction(work)

    val animatedProgress by animateFloatAsState(
        targetValue = checklistProgress,
        animationSpec = tween(500),
        label = "checklistProgress"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Work In Progress", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusInProgress)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IN PROGRESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusInProgress,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── TIMER CARD ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryLight
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Work Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = workViewModel.formatElapsedTime(elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (work.startTime != null) {
                        Text(
                            text = "Started at ${DateTimeUtils.formatToIndiaTime(work.startTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location row in timer card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = work.companyName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = work.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── CHECKLIST ──
            // ── CHECKLIST SUMMARY ──
            SectionHeader(title = "Work Checklist")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Progress row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$totalCompleted of $totalCount completed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(checklistProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                checklistProgress == 1f -> StatusCompleted
                                checklistProgress > 0f -> StatusInProgress
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            checklistProgress == 1f -> StatusCompleted
                            else -> PrimaryLight
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preview top 3 standard items
                    predefinedItems.take(3).forEachIndexed { index, item ->
                        ChecklistItemRow(
                            item = item,
                            onToggle = { workViewModel.toggleChecklistItem(item.id) }
                        )
                        if (index < predefinedItems.take(3).size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    if (predefinedItems.size > 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "+${predefinedItems.size - 3} more standard tasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onViewChecklist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Full Checklist", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ADDITIONAL WORK ──
            SectionHeader(title = "Additional Work")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (additionalItems.isEmpty()) {
                        Text(
                            text = "No additional work added",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAdditionalWorkSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryLight.copy(alpha = 0.1f),
                                contentColor = PrimaryLight
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Additional Work", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            additionalItems.take(2).forEach { item ->
                                ChecklistItemRow(
                                    item = item,
                                    onToggle = { workViewModel.toggleChecklistItem(item.id) }
                                )
                            }
                            if (additionalItems.size > 2) {
                                Text(
                                    text = "+${additionalItems.size - 2} more additional items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onViewChecklist) {
                                    Text("Manage All (${additionalItems.size})", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { showAdditionalWorkSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryLight.copy(alpha = 0.1f),
                                        contentColor = PrimaryLight
                                    )
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Work", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── WORK PHOTOS (CONCISE SUMMARY) ──
            val photos = workViewModel.getPhotos(work)
            val photoCount = photos.size
            val uploadedCount = workViewModel.getUploadedPhotosCount(work)
            val uploadingCount = workViewModel.getUploadingPhotosCount(work)
            val failedCount = workViewModel.getFailedPhotosCount(work)

            SectionHeader(title = "Work Photos")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddPhotos() },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📷 WORK PHOTOS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLight,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (photoCount == 0) "No photos added" else "$photoCount photos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$uploadedCount uploaded • $uploadingCount uploading • $failedCount failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (photoCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryLight.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "$photoCount Photos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (photos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No work evidence photos captured yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // 3 Compact Thumbnail Previews
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            photos.take(3).forEach { photo ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onAddPhotos() }
                                ) {
                                    PhotoThumbnailView(photo = photo, modifier = Modifier.fillMaxSize())

                                    // Upload status indicator dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (photo.uploadStatus) {
                                                PhotoUploadStatus.UPLOADED -> Icons.Default.Check
                                                PhotoUploadStatus.FAILED -> Icons.Default.Close
                                                else -> Icons.Default.Timer
                                            },
                                            contentDescription = null,
                                            tint = when (photo.uploadStatus) {
                                                PhotoUploadStatus.UPLOADED -> StatusCompleted
                                                PhotoUploadStatus.FAILED -> ErrorRed
                                                else -> StatusInProgress
                                            },
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                            // Empty slots if fewer than 3
                            for (i in photos.take(3).size until 3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showAddPhotoSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryLight.copy(alpha = 0.12f),
                                contentColor = PrimaryLight
                            )
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Photos", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onAddPhotos,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View All Photos", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── APPROVAL STATUS SECTION (FOR SERVICE BOY) ──
            SectionHeader(title = "Approval Status")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewApprovalStatus() },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        work.status == WorkStatus.APPROVED -> StatusCompleted.copy(alpha = 0.10f)
                        work.status == WorkStatus.REJECTED -> ErrorRed.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // POC status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (work.pocApproved) {
                                            true -> StatusCompleted.copy(alpha = 0.15f)
                                            false -> ErrorRed.copy(alpha = 0.15f)
                                            null -> PrimaryLight.copy(alpha = 0.10f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (work.pocApproved) {
                                        true -> Icons.Default.Check
                                        false -> Icons.Default.Close
                                        null -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = when (work.pocApproved) {
                                        true -> StatusCompleted
                                        false -> ErrorRed
                                        null -> PrimaryLight
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("POC Review", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(work.pocName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            text = when (work.pocApproved) {
                                true -> "✓ Approved"
                                false -> "✕ Rejected"
                                null -> if (workViewModel.isWaitingForReview(work)) "In Review" else "Pending"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (work.pocApproved) {
                                true -> StatusCompleted
                                false -> ErrorRed
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Supervisor status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (work.supervisorApproved) {
                                            true -> StatusCompleted.copy(alpha = 0.15f)
                                            false -> ErrorRed.copy(alpha = 0.15f)
                                            null -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (work.supervisorApproved) {
                                        true -> Icons.Default.Check
                                        false -> Icons.Default.Close
                                        null -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = when (work.supervisorApproved) {
                                        true -> StatusCompleted
                                        false -> ErrorRed
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Supervisor Review", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(work.supervisorName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            text = when (work.supervisorApproved) {
                                true -> "✓ Approved"
                                false -> "✕ Rejected"
                                null -> if (work.pocApproved == true) "Awaiting Review" else "Waiting for POC"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (work.supervisorApproved) {
                                true -> StatusCompleted
                                false -> ErrorRed
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ACTIVITY TIMELINE ──
            if (work.activityLog.isNotEmpty()) {
                SectionHeader(title = "Activity")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        work.activityLog.forEachIndexed { index, event ->
                            TimelineItem(
                                description = event.description,
                                timestamp = event.timestamp,
                                isLast = index == work.activityLog.size - 1,
                                isDone = event.isDone
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── BOTTOM ACTIONS BASED ON WORKFLOW STATE ──
            when {
                work.status == WorkStatus.APPROVED -> {
                    Button(
                        onClick = onProceedToComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PROCEED TO COMPLETE WORK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                work.status == WorkStatus.REJECTED -> {
                    Button(
                        onClick = onViewApprovalStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIEW REVISION FEEDBACK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                workViewModel.isWaitingForReview(work) -> {
                    Button(
                        onClick = onViewApprovalStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                    ) {
                        Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIEW APPROVAL STATUS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                else -> {
                    // ── SWIPE TO COMPLETE WORK ──
                    SwipeActionButton(
                        label = "SWIPE TO COMPLETE WORK",
                        completedLabel = "✓ TIMER STOPPED",
                        trackColor = StatusCompleted,
                        onSwipeComplete = {
                            workViewModel.stopTimer()
                            onNavigateToCompleteAndSubmit()
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Button(
                onClick = onAddPhotos,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryLight.copy(alpha = 0.12f),
                    contentColor = PrimaryLight
                )
            ) {
                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Work Photos ($photoCount)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            SecondaryButton(
                text = "View Work Details",
                onClick = onViewDetails
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── ADDITIONAL WORK BOTTOM SHEET ──
    if (showAdditionalWorkSheet) {
        AddEditAdditionalWorkBottomSheet(
            onDismiss = { showAdditionalWorkSheet = false },
            onSave = { title, description ->
                workViewModel.addAdditionalWork(title, description)
                showAdditionalWorkSheet = false
            }
        )
    }

    // ── ADD PHOTOS BOTTOM SHEET ──
    if (showAddPhotoSheet) {
        AddPhotosBottomSheet(
            onDismiss = { showAddPhotoSheet = false },
            onPhotosSelected = { newPhotos ->
                workViewModel.addPhotos(newPhotos)
                showAddPhotoSheet = false
            }
        )
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit
) {
    val checkColor by animateColorAsState(
        targetValue = if (item.isCompleted) StatusCompleted else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "checkColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (item.isCompleted)
            MaterialTheme.colorScheme.onSurfaceVariant
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "textColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = if (item.isCompleted) "Completed" else "Not completed",
            tint = checkColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                color = textColor,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.isCompleted) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = StatusCompleted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
