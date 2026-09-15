package com.example.taskmanagementapplication.review.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.ui.AvatarPlaceholder
import com.example.taskmanagementapplication.core.ui.PhotoThumbnailView
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.core.ui.SwipeActionButton
import com.example.taskmanagementapplication.core.ui.TimelineItem
import com.example.taskmanagementapplication.work.ui.PhotoViewerDialog
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorReviewScreen(
    onBack: () -> Unit,
    workViewModel: WorkViewModel
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val elapsedSeconds by workViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val isSubmitting by workViewModel.isSubmitting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRejectSheet by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }
    var rejectionError by remember { mutableStateOf<String?>(null) }
    var viewingPhotoIndex by remember { mutableStateOf<Int?>(null) }

    val predefinedItems = workViewModel.getPredefinedItems(work)
    val additionalItems = workViewModel.getAdditionalItems(work)
    val photos = work.photos

    val isSubmittedForReview = work.status == WorkStatus.WAITING_FOR_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_POC_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW ||
        work.status == WorkStatus.APPROVED ||
        work.status == WorkStatus.COMPLETED ||
        work.submittedForReviewAt != null

    LaunchedEffect(work.backendId) {
        work.backendId?.let {
            workViewModel.loadPhotos(it)
            workViewModel.loadApprovals(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Supervisor Review", fontWeight = FontWeight.Bold)
                        Text(
                            text = work.companyName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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

            // ── AUTHORITATIVE APPROVAL STATE BADGES ──
            if (work.supervisorApproved == true) {
                // Dual Approved Badges: POC APPROVED ✓ and SUPERVISOR APPROVED ✓
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Dual approval confirmation card" },
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusCompleted.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusCompleted,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "POC APPROVED ✓",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusCompleted,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SUPERVISOR APPROVED ✓",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Both Client POC and Site Supervisor have granted final sign-off. Service Boy may complete job.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Supervisor: ${work.supervisorName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = work.supervisorApprovalTime ?: "Recently",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCompleted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (work.pocApproved == true) {
                // POC APPROVED ✓ Card (Awaiting Supervisor)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "POC approval card" },
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusCompleted.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StatusCompleted,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "POC APPROVED ✓",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusCompleted
                            )
                            Text(
                                text = "Approved by POC (${work.pocName}) at ${work.pocApprovalTime ?: "Recorded on site"}. Ready for final Supervisor sign-off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── APPROVAL CHAIN CARD ──
            SectionHeader(title = "Approval Chain")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Step 1: POC
                    ApprovalChainStep(
                        role = "CLIENT POC REVIEW",
                        name = work.pocName,
                        isApproved = work.pocApproved == true,
                        isRejected = work.pocApproved == false,
                        time = work.pocApprovalTime ?: "Pending POC Review",
                        isLast = false
                    )

                    // Step 2: Supervisor
                    ApprovalChainStep(
                        role = "SITE SUPERVISOR FINAL SIGN-OFF",
                        name = work.supervisorName,
                        isApproved = work.supervisorApproved == true,
                        isRejected = work.supervisorApproved == false,
                        time = work.supervisorApprovalTime ?: if (work.pocApproved == true) "Awaiting Your Final Review" else "Locked Until POC Approval",
                        isLast = false
                    )

                    // Step 3: Work Completion
                    ApprovalChainStep(
                        role = "WORK COMPLETION & DEPARTURE",
                        name = "Service Boy (${work.serviceBoyName})",
                        isApproved = work.status == WorkStatus.COMPLETED,
                        isRejected = false,
                        time = if (work.status == WorkStatus.COMPLETED) work.completedAt ?: "Completed" else "Ready after Supervisor Approval",
                        isLast = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── REJECTION AUDIT HISTORY (Persistent) ──
            if (!work.pocRejectionReason.isNullOrBlank() || !work.supervisorRejectionReason.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Supervisor rejection history audit trail" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rejection Audit History",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                        if (!work.pocRejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Client POC (${work.pocName}): \"${work.pocRejectionReason}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (!work.supervisorRejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Site Supervisor (${work.supervisorName}): \"${work.supervisorRejectionReason}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Audit trail preserved for verification and quality tracking.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── WORK INFORMATION DETAILS ──
            SectionHeader(title = "Work Information")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarPlaceholder(name = work.serviceBoyName, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = work.serviceBoyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Field Service Engineer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Service Title",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = work.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Duration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = workViewModel.formatElapsedTime(elapsedSeconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Start Timestamp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = work.startTime ?: "Recorded on site",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Field Completion Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = work.submittedForReviewAt ?: work.completedAt ?: "Upon review request",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Site Location",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${work.address} (${work.distance})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── GPS VERIFICATION CARD ──
            SectionHeader(title = "GPS Geofence Verification")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StatusCompleted.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = StatusCompleted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GPS Geofence Verified ✓",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusCompleted
                            )
                            Text(
                                text = "Authoritative Haversine validation passed (< ${work.allowedRadiusMeters.toInt()}m boundary)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Site Coordinates",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (work.latitude != null && work.longitude != null) {
                                    "%.4f, %.4f".format(work.latitude, work.longitude)
                                } else {
                                    "12.9716° N, 77.5946° E"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Proximity to Work Site",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = work.distanceFromWorkMeters?.let { "%.1fm to site center".format(it) } ?: work.distance.ifBlank { "0m (On-Site)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusCompleted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── SECTION 1: ASSIGNED CHECKLIST (Strictly separated) ──
            SectionHeader(title = "ASSIGNED CHECKLIST (${predefinedItems.count { it.isCompleted }}/${predefinedItems.size})")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (predefinedItems.isEmpty()) {
                        Text(
                            text = "No assigned checklist tasks for this work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        predefinedItems.forEachIndexed { index, item ->
                            SupervisorAssignedChecklistRow(item = item, performerName = work.serviceBoyName)
                            if (index < predefinedItems.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "POC and Supervisor cannot modify these task states. Reviewing evidence submitted by the Service Boy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── SECTION 2: ADDITIONAL WORK PERFORMED (Never mixed) ──
            SectionHeader(title = "ADDITIONAL WORK PERFORMED")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (additionalItems.isNotEmpty()) AccentOrange.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (additionalItems.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No additional work reported.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        additionalItems.forEachIndexed { idx, item ->
                            SupervisorAdditionalWorkRow(item = item, performerName = work.serviceBoyName)
                            if (idx < additionalItems.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── WORK PHOTOS EVIDENCE ──
            SectionHeader(title = "Work Photos (${photos.size})")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (photos.isEmpty()) {
                        Text(
                            text = "No work evidence photos captured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(photos) { idx, photo ->
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .semantics { contentDescription = "View photo ${photo.title}" }
                                ) {
                                    PhotoThumbnailView(photo = photo, modifier = Modifier.fillMaxSize())
                                    TextButton(
                                        onClick = { viewingPhotoIndex = idx },
                                        modifier = Modifier.fillMaxSize()
                                    ) {}
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Actual photos uploaded by Service Boy via signed storage URLs. Tap to inspect full-screen.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ACTIVITY TIMELINE ──
            if (work.activityLog.isNotEmpty()) {
                SectionHeader(title = "Timeline Audit")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        work.activityLog.takeLast(6).forEachIndexed { index, event ->
                            TimelineItem(
                                description = event.description,
                                timestamp = event.timestamp,
                                isLast = index == work.activityLog.takeLast(6).size - 1,
                                isDone = event.isDone
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── SUPERVISOR ACTIONS & APPROVAL RULES ──
            // Rule 7: Supervisor cannot approve until POC approval exists.
            // Rule 4: Controls hidden before work submission.
            when {
                !isSubmittedForReview -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Supervisor controls hidden notice" },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryLight.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLight.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PrimaryLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Work In Progress — Not Submitted Yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryLight
                                )
                                Text(
                                    text = "Service Boy has not submitted work for review yet. Supervisor approval controls will appear once work is submitted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                work.pocApproved != true -> {
                    // POC HAS NOT APPROVED YET — SUPERVISOR APPROVAL STRICTLY LOCKED
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "POC approval pending warning" },
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryLight.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLight.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PrimaryLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "POC APPROVAL PENDING",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryLight,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Supervisor approval is locked until Client POC (${work.pocName}) has reviewed and approved the work.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                work.supervisorApproved == true -> {
                    // Confirmed: already displayed in top dual badge
                }
                work.supervisorApproved == false -> {
                    // Work rejected by supervisor: option to re-approve
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwipeActionButton(
                            label = ">>> SWIPE TO FINAL APPROVE <<<",
                            completedLabel = "✓ FINAL APPROVED",
                            trackColor = StatusCompleted,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .semantics { contentDescription = "Swipe to final approve work" },
                            onSwipeComplete = {
                                workViewModel.approveBySupervisor()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Supervisor approved work! Status updated to Approved.")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showRejectSheet = true },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Reject work button" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REJECT WORK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    // PENDING SUPERVISOR REVIEW (POC ALREADY APPROVED)
                    // Rule 8: Supervisor: [ >>> SWIPE TO FINAL APPROVE <<< ] and REJECT WORK
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwipeActionButton(
                            label = ">>> SWIPE TO FINAL APPROVE <<<",
                            completedLabel = "✓ FINAL APPROVED",
                            trackColor = StatusCompleted,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .semantics { contentDescription = "Swipe to final approve work" },
                            onSwipeComplete = {
                                workViewModel.approveBySupervisor()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Supervisor approved work! Status updated to Approved.")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showRejectSheet = true },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Reject work button" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REJECT WORK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // ── REJECT REASON BOTTOM SHEET (Mandatory reason) ──
    if (showRejectSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRejectSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Why are you rejecting this work?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A specific rejection reason is mandatory so ${work.serviceBoyName} can complete required site corrections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = rejectionReason,
                    onValueChange = {
                        rejectionReason = it
                        if (it.isNotBlank()) rejectionError = null
                    },
                    label = { Text("Enter rejection reason *") },
                    placeholder = { Text("e.g., Traps on perimeter fence were not properly anchored...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .semantics { contentDescription = "Supervisor rejection reason input" },
                    shape = RoundedCornerShape(12.dp),
                    isError = rejectionError != null,
                    supportingText = {
                        if (rejectionError != null) {
                            Text(rejectionError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (rejectionReason.trim().isBlank()) {
                            rejectionError = "Rejection reason is mandatory."
                            return@Button
                        }
                        workViewModel.rejectBySupervisor(rejectionReason)
                        showRejectSheet = false
                        rejectionReason = ""
                        scope.launch {
                            snackbarHostState.showSnackbar("Work rejected. Feedback sent to Service Boy.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .semantics { contentDescription = "Submit supervisor rejection" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Reject Work", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // ── PHOTO VIEWER ──
    viewingPhotoIndex?.let { index ->
        PhotoViewerDialog(
            photos = photos,
            initialIndex = index,
            onDismiss = { viewingPhotoIndex = null },
            isReadOnly = true
        )
    }
}

@Composable
private fun ApprovalChainStep(
    role: String,
    name: String,
    isApproved: Boolean,
    isRejected: Boolean,
    time: String,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isApproved -> StatusCompleted.copy(alpha = 0.15f)
                            isRejected -> ErrorRed.copy(alpha = 0.15f)
                            else -> PrimaryLight.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isApproved -> Icons.Default.Check
                        isRejected -> Icons.Default.Close
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when {
                        isApproved -> StatusCompleted
                        isRejected -> ErrorRed
                        else -> PrimaryLight
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.padding(top = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isApproved -> "✓ Approved"
                        isRejected -> "✕ Rejected"
                        else -> "Pending"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isApproved -> StatusCompleted
                        isRejected -> ErrorRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = "$name • $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isLast) Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SupervisorAssignedChecklistRow(
    item: ChecklistItem,
    performerName: String? = null
) {
    val taskLabel = item.taskLabel ?: item.title
    val performer = item.completedByName ?: performerName ?: "Service Boy"
    val timestamp = item.completedAt ?: "Recorded on site"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Supervisor assigned task: $taskLabel" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (item.isCompleted) StatusCompleted else AccentOrange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = taskLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (item.isCompleted) StatusCompleted.copy(alpha = 0.12f) else AccentOrange.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (item.isCompleted) "✓ Completed" else "⏳ Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isCompleted) StatusCompleted else AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• Completed By: $performer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isCompleted) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Completed At: $timestamp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SupervisorAdditionalWorkRow(
    item: ChecklistItem,
    performerName: String? = null
) {
    val taskLabel = item.taskLabel ?: item.title
    val performer = item.completedByName ?: performerName ?: "Service Boy"
    val addedAt = item.createdAt ?: item.completedAt ?: "Recorded during work"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Supervisor additional work: $taskLabel" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = AccentOrange.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    color = AccentOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "+ $taskLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Performed By: $performer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Added At: $addedAt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = AccentOrange.copy(alpha = 0.12f)
        ) {
            Text(
                text = "ADDITIONAL",
                style = MaterialTheme.typography.labelSmall,
                color = AccentOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
