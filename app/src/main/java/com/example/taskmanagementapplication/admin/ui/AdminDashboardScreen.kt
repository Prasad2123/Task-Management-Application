package com.example.taskmanagementapplication.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.auth.viewmodel.AuthViewModel
import com.example.taskmanagementapplication.core.model.Company
import com.example.taskmanagementapplication.core.model.MasterTask
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.*
import com.example.taskmanagementapplication.core.ui.AvatarPlaceholder
import com.example.taskmanagementapplication.core.util.DateTimeUtils
import com.example.taskmanagementapplication.core.util.MapUtils
import com.example.taskmanagementapplication.data.dto.UserProfileDto
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminFilterTab(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    IN_REVIEW("Under Review"),
    APPROVED("Approved"),
    COMPLETED("Completed")
}

/**
 * Admin Operational Monitoring & Work Management Dashboard.
 * Admin can create works by assigning common tasks from the master task list,
 * and monitors all service works across the company.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    authViewModel: AuthViewModel,
    workViewModel: WorkViewModel,
    onOpenProfile: () -> Unit,
    onViewWorkDetails: (Work) -> Unit,
    onViewPhotos: (Work) -> Unit,
    onViewReport: (Work) -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val predefinedWorks by workViewModel.predefinedWorks.collectAsStateWithLifecycle()
    val masterTasks by workViewModel.masterTasks.collectAsStateWithLifecycle()
    val availableUsers by workViewModel.availableUsers.collectAsStateWithLifecycle()
    val companies by workViewModel.companies.collectAsStateWithLifecycle()
    val isSubmitting by workViewModel.isSubmitting.collectAsStateWithLifecycle()
    val isLoading by workViewModel.isLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AdminFilterTab.ALL) }
    var showCreateWorkSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        workViewModel.loadMyWork()
        workViewModel.loadMasterTasks()
        workViewModel.loadUsers()
        workViewModel.loadCompanies()
    }

    val filteredWorks = remember(predefinedWorks, searchQuery, selectedTab) {
        predefinedWorks
            .sortedWith(
                compareByDescending<Work> { it.createdAt ?: "" }
                    .thenByDescending { it.backendId ?: (it.id.toLongOrNull() ?: 0L) }
            )
            .filter { work ->
                val matchesQuery = searchQuery.isBlank() ||
                        work.title.contains(searchQuery, ignoreCase = true) ||
                        work.companyName.contains(searchQuery, ignoreCase = true) ||
                        work.serviceBoyName.contains(searchQuery, ignoreCase = true)

                val matchesTab = when (selectedTab) {
                    AdminFilterTab.ALL -> true
                    AdminFilterTab.IN_PROGRESS -> work.status == WorkStatus.IN_PROGRESS || work.status == WorkStatus.WORK_STARTED
                    AdminFilterTab.IN_REVIEW -> work.status == WorkStatus.WAITING_FOR_POC_REVIEW ||
                            work.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW ||
                            work.status == WorkStatus.WAITING_FOR_REVIEW
                    AdminFilterTab.APPROVED -> work.status == WorkStatus.APPROVED
                    AdminFilterTab.COMPLETED -> work.status == WorkStatus.COMPLETED
                }

                matchesQuery && matchesTab
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Work Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Master Checklists & Field Operations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { workViewModel.loadMyWork() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                    }
                    IconButton(onClick = onOpenProfile) {
                        AvatarPlaceholder(name = currentUser?.name ?: "Admin", size = 34.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    workViewModel.loadMyWork()
                    workViewModel.loadMasterTasks()
                    workViewModel.loadUsers()
                    workViewModel.loadCompanies()
                    showCreateWorkSheet = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Work", fontWeight = FontWeight.Bold) },
                containerColor = PrimaryLight,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Checklist Rule Compliance Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLight.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryLight.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                tint = PrimaryLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Authoritative Master Checklist Control",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLight
                            )
                            Text(
                                text = "Admin selects predefined common tasks from the master task list to create each work's assigned checklist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Metric Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Works",
                        count = predefinedWorks.size.toString(),
                        icon = Icons.Default.Assignment,
                        accentColor = PrimaryLight
                    )
                    MetricSummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "In Progress",
                        count = predefinedWorks.count { it.status == WorkStatus.IN_PROGRESS || it.status == WorkStatus.WORK_STARTED }.toString(),
                        icon = Icons.Default.PlayArrow,
                        accentColor = StatusInProgress
                    )
                    MetricSummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Under Review",
                        count = predefinedWorks.count {
                            it.status == WorkStatus.WAITING_FOR_POC_REVIEW || it.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW
                        }.toString(),
                        icon = Icons.Default.HourglassTop,
                        accentColor = StatusWaitingReview
                    )
                    MetricSummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Completed",
                        count = predefinedWorks.count { it.status == WorkStatus.COMPLETED }.toString(),
                        icon = Icons.Default.CheckCircle,
                        accentColor = StatusCompleted
                    )
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by site, company, or engineer...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryLight,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Filter Tabs
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AdminFilterTab.values()) { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryLight.copy(alpha = 0.15f),
                                selectedLabelColor = PrimaryLight
                            )
                        )
                    }
                }
            }

            // Loading Indicator
            if (isLoading && predefinedWorks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryLight)
                    }
                }
            } else if (filteredWorks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No matching service works found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Works Cards
                items(filteredWorks, key = { it.id }) { work ->
                    AdminWorkCard(
                        work = work,
                        workViewModel = workViewModel,
                        onViewDetails = { onViewWorkDetails(work) },
                        onViewPhotos = { onViewPhotos(work) },
                        onViewReport = { onViewReport(work) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCreateWorkSheet) {
        CreateWorkBottomSheet(
            companies = companies,
            masterTasks = masterTasks,
            availableUsers = availableUsers,
            isSubmitting = isSubmitting,
            workViewModel = workViewModel,
            onDismiss = {
                showCreateWorkSheet = false
                workViewModel.loadMyWork()
                workViewModel.loadUsers()
            },
            onCreateWork = { companyName, address, latitude, longitude, googleMapsLink, serviceBoyId, pocId, supervisorId, masterTaskIds, scheduledDate ->
                workViewModel.createWorkWithChecklist(
                    companyName = companyName,
                    address = address,
                    serviceBoyId = serviceBoyId,
                    pocId = pocId,
                    supervisorId = supervisorId,
                    masterTaskIds = masterTaskIds,
                    scheduledDate = scheduledDate,
                    latitude = latitude,
                    longitude = longitude,
                    googleMapsLink = googleMapsLink
                ) {
                    showCreateWorkSheet = false
                    workViewModel.loadMyWork()
                    workViewModel.loadUsers()
                }
            }
        )
    }
}

@Composable
private fun MetricSummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    count: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AdminWorkCard(
    work: Work,
    workViewModel: WorkViewModel,
    onViewDetails: () -> Unit,
    onViewPhotos: () -> Unit,
    onViewReport: () -> Unit
) {
    val context = LocalContext.current
    val (statusLabel, statusColor) = when (work.status) {
        WorkStatus.NOT_STARTED -> "ASSIGNED" to PrimaryLight
        WorkStatus.WORK_STARTED, WorkStatus.IN_PROGRESS -> "IN PROGRESS" to StatusInProgress
        WorkStatus.WAITING_FOR_POC_REVIEW -> "POC REVIEW" to StatusWaitingReview
        WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, WorkStatus.WAITING_FOR_REVIEW -> "SUPERVISOR REVIEW" to AccentOrange
        WorkStatus.APPROVED -> "APPROVED" to StatusCompleted
        WorkStatus.COMPLETED -> "COMPLETED" to StatusCompleted
        WorkStatus.REJECTED -> "CHANGES REQUESTED" to ErrorRed
    }

    val serviceBoyStatus = workViewModel.getServiceBoyStatusForWork(work)
    val isTechnicianFree = work.serviceBoyId?.let { workViewModel.isServiceBoyFree(it) } ?: (work.status == WorkStatus.COMPLETED)
    val mapLink = work.googleMapsLink?.ifBlank { null }
        ?: if (work.latitude != null && work.longitude != null) "https://www.google.com/maps?q=${work.latitude},${work.longitude}" else null

    val assignedCount = work.checklist.count { !it.isAdditional }.let { if (it == 0 && work.checklist.isNotEmpty()) work.checklist.size else it }
    val completedCount = work.checklist.count { it.isCompleted }
    val additionalCount = work.checklist.count { it.isAdditional }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Work ID, Company Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Work #${work.backendId ?: work.id}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLight
                    )
                    Text(
                        text = work.companyName.ifBlank { work.title },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (work.companyName.isNotBlank() && work.title.isNotBlank() && work.title != work.companyName) {
                        Text(
                            text = work.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location Address
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = PrimaryLight
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = work.address.ifBlank { "Site location not specified" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }

            // Clickable Google Maps Link
            if (mapLink != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { MapUtils.openGoogleMapsUrl(context, mapLink) }
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = "Map Link",
                        modifier = Modifier.size(13.dp),
                        tint = PrimaryLight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Open in Google Maps",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Personnel Authorizations & Live Technician Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Service Boy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = work.serviceBoyName.ifBlank { "Assigned Engineer" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "• $serviceBoyStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTechnicianFree) StatusCompleted else AccentOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                AdminPersonnelItem(
                    label = "Site POC",
                    name = work.pocName.ifBlank { "Assigned POC" },
                    approvalStatus = when (work.pocApproved) {
                        true -> "Approved"
                        false -> "Rejected"
                        null -> "Pending"
                    }
                )

                AdminPersonnelItem(
                    label = "Site Supervisor",
                    name = work.supervisorName.ifBlank { "Assigned Supervisor" },
                    approvalStatus = when (work.supervisorApproved) {
                        true -> "Approved"
                        false -> "Rejected"
                        null -> "Pending"
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Task Progress & Evidence Badges (Assigned, Completed, Additional, Photos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tasks badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryLight.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(13.dp), tint = PrimaryLight)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completedCount/$assignedCount Tasks",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryLight,
                            fontSize = 10.sp
                        )
                    }
                }

                // Additional work badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (additionalCount > 0) AccentOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddBox,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (additionalCount > 0) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+$additionalCount Extra",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (additionalCount > 0) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                // Photos badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${work.photos.size} Photos",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timestamps: Created Date, Start Time, Field Completion, Final Completion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimestampItem(label = "Date", value = if (work.scheduledDate.isNotBlank()) DateTimeUtils.formatToIndiaDate(work.scheduledDate) else "Today")
                TimestampItem(label = "Start", value = work.startTime?.let { DateTimeUtils.formatToIndiaTimeOnly(it) } ?: "—")
                TimestampItem(label = "Field End", value = (work.submittedForReviewAt ?: work.endTime)?.let { DateTimeUtils.formatToIndiaTimeOnly(it) } ?: "—")
                TimestampItem(label = "Final End", value = (work.completedAt ?: if (work.status == WorkStatus.COMPLETED) work.endTime else null)?.let { DateTimeUtils.formatToIndiaTimeOnly(it) } ?: "—")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Read-Only Inspection Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inspect", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onViewPhotos,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photos", style = MaterialTheme.typography.labelMedium)
                }

                if (work.status == WorkStatus.COMPLETED || work.status == WorkStatus.APPROVED) {
                    Button(
                        onClick = onViewReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimestampItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AdminPersonnelItem(
    label: String,
    name: String,
    approvalStatus: String? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (approvalStatus != null) {
            val color = when (approvalStatus) {
                "Approved" -> StatusCompleted
                "Rejected" -> ErrorRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "• $approvalStatus",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWorkBottomSheet(
    companies: List<Company>,
    masterTasks: List<MasterTask>,
    availableUsers: List<UserProfileDto>,
    isSubmitting: Boolean,
    workViewModel: WorkViewModel,
    onDismiss: () -> Unit,
    onCreateWork: (
        companyName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        googleMapsLink: String,
        serviceBoyId: Long,
        pocId: Long,
        supervisorId: Long,
        masterTaskIds: List<Long>,
        scheduledDate: String
    ) -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var selectedCompany by remember { mutableStateOf<Company?>(null) }
    var companyDropdownExpanded by remember { mutableStateOf(false) }

    var address by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var googleMapsLink by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableStateOf(today) }

    val serviceBoys = remember(availableUsers) {
        availableUsers.filter { it.role.equals("SERVICE_BOY", ignoreCase = true) }
    }
    val pocs = remember(availableUsers) {
        availableUsers.filter { it.role.equals("POC", ignoreCase = true) }
    }
    val supervisors = remember(availableUsers) {
        availableUsers.filter { it.role.equals("SUPERVISOR", ignoreCase = true) }
    }

    // Technician selection: initially unselected as per requirement 9
    var selectedServiceBoyId by remember { mutableStateOf<Long?>(null) }
    var techDropdownExpanded by remember { mutableStateOf(false) }

    var selectedPocId by remember(pocs) {
        mutableStateOf(pocs.firstOrNull()?.id ?: 2L)
    }
    var selectedSupervisorId by remember(supervisors) {
        mutableStateOf(supervisors.firstOrNull()?.id ?: 3L)
    }

    // Master tasks selected for this work's assigned checklist
    var selectedMasterTaskIds by remember(masterTasks) {
        mutableStateOf(masterTasks.take(4).map { it.id }.toSet())
    }

    var companyError by remember { mutableStateOf(false) }
    var technicianError by remember { mutableStateOf(false) }

    val selectedServiceBoy = serviceBoys.find { it.id == selectedServiceBoyId }
    val isSelectedServiceBoyFree = selectedServiceBoyId != null && workViewModel.isServiceBoyFree(selectedServiceBoyId!!)
    val anyFreeServiceBoy = serviceBoys.any { workViewModel.isServiceBoyFree(it.id) }

    val selectedTechText = if (selectedServiceBoy != null) {
        val techName = selectedServiceBoy.name.ifBlank { selectedServiceBoy.email }
        val statusText = if (isSelectedServiceBoyFree) "FREE" else "BUSY"
        "$techName — $statusText"
    } else {
        ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Create New Work",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Assign specific checklist tasks from master list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── COMPANY NAME DROPDOWN (Required) ──
            Text("Company Name *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = companyDropdownExpanded,
                onExpandedChange = { companyDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCompany?.companyName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    placeholder = { Text("Select Company ▼") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = companyDropdownExpanded)
                    },
                    isError = companyError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = companyDropdownExpanded,
                    onDismissRequest = { companyDropdownExpanded = false }
                ) {
                    companies.forEach { comp ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = comp.companyName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedCompany?.id == comp.id) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedCompany = comp
                                address = comp.address
                                latitudeText = comp.latitude.toString()
                                longitudeText = comp.longitude.toString()
                                googleMapsLink = "https://www.google.com/maps?q=${comp.latitude},${comp.longitude}"
                                companyError = false
                                companyDropdownExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = if (selectedCompany?.id == comp.id) PrimaryLight else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }
            if (companyError) {
                Text("Company selection is required", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── COMPANY ADDRESS (AUTO-FILLED) ──
            Text("Company Address", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = address,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Select a company first") },
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    if (selectedCompany != null) {
                        Text("Testing address automatically populated", color = StatusCompleted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── COORDINATES (AUTO-ASSOCIATED WITH SELECTED COMPANY) ──
            Text("Coordinates", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Latitude") },
                    placeholder = { Text("17.5230403") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Longitude") },
                    placeholder = { Text("73.5378423") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── GOOGLE MAPS LINK (AUTO-GENERATED) ──
            Text("Google Maps Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = googleMapsLink,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Auto-generated from coordinates") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Scheduled Date
            Text("Scheduled Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = scheduledDate,
                onValueChange = { scheduledDate = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Personnel Assignment Section
            Text("Assign Personnel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            // Technician selection with FREE / BUSY status
            Text("Technician *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            if (!anyFreeServiceBoy && serviceBoys.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorRed.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No available Service Boy. All technicians are currently engaged in active works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            ExposedDropdownMenuBox(
                expanded = techDropdownExpanded,
                onExpandedChange = { techDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedTechText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    placeholder = { Text("Select Technician ▼") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = techDropdownExpanded)
                    },
                    leadingIcon = if (selectedServiceBoy != null) {
                        {
                            Icon(
                                imageVector = if (isSelectedServiceBoyFree) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isSelectedServiceBoyFree) StatusCompleted else ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    isError = technicianError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = techDropdownExpanded,
                    onDismissRequest = { techDropdownExpanded = false }
                ) {
                    if (serviceBoys.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No technicians available", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { techDropdownExpanded = false },
                            enabled = false
                        )
                    } else {
                        serviceBoys.forEach { tech ->
                            val isFree = workViewModel.isServiceBoyFree(tech.id)
                            val techName = tech.name.ifBlank { tech.email }

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = techName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (tech.id == selectedServiceBoyId) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isFree) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = tech.email,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isFree) StatusCompleted.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isFree) "FREE" else "BUSY",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFree) StatusCompleted else ErrorRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isFree) {
                                        selectedServiceBoyId = tech.id
                                        technicianError = false
                                        techDropdownExpanded = false
                                    }
                                },
                                enabled = isFree,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFree) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (isFree) StatusCompleted else ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            if (technicianError) {
                Text("Technician selection is required", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // POC selection
            Text("Person of Contact (POC)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            PersonnelSelectorRow(
                options = pocs.ifEmpty {
                    listOf(UserProfileDto(id = 2L, name = "Amit Sharma", email = "poc@demo.com", role = "POC"))
                },
                selectedId = selectedPocId,
                onSelect = { selectedPocId = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Supervisor selection
            Text("Site Supervisor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            PersonnelSelectorRow(
                options = supervisors.ifEmpty {
                    listOf(UserProfileDto(id = 3L, name = "Suresh Patil", email = "supervisor@demo.com", role = "SUPERVISOR"))
                },
                selectedId = selectedSupervisorId,
                onSelect = { selectedSupervisorId = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── ASSIGNED PREDEFINED TASKS CHECKLIST SELECTION ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assigned Predefined Tasks *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select tasks from the master list for this work",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedMasterTaskIds.isNotEmpty()) PrimaryLight.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${selectedMasterTaskIds.size} Selected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedMasterTaskIds.isNotEmpty()) PrimaryLight else ErrorRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Select / Clear buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedMasterTaskIds = masterTasks.map { it.id }.toSet()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Select All (${masterTasks.size})", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        selectedMasterTaskIds = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Clear All", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedMasterTaskIds.isEmpty()) {
                Text(
                    text = "Select at least one task for this work.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // List of master tasks
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    masterTasks.forEachIndexed { index, mt ->
                        val isChecked = selectedMasterTaskIds.contains(mt.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedMasterTaskIds = if (isChecked) {
                                        selectedMasterTaskIds - mt.id
                                    } else {
                                        selectedMasterTaskIds + mt.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedMasterTaskIds = if (checked) {
                                        selectedMasterTaskIds + mt.id
                                    } else {
                                        selectedMasterTaskIds - mt.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryLight)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mt.taskLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = mt.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        if (index < masterTasks.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Create Button
            val canSubmit = selectedCompany != null &&
                    selectedServiceBoyId != null &&
                    isSelectedServiceBoyFree &&
                    selectedMasterTaskIds.isNotEmpty() &&
                    !isSubmitting

            Button(
                onClick = {
                    if (selectedCompany == null) companyError = true
                    if (selectedServiceBoyId == null || !isSelectedServiceBoyFree) technicianError = true
                    if (canSubmit && selectedCompany != null && selectedServiceBoyId != null) {
                        val comp = selectedCompany!!
                        onCreateWork(
                            comp.companyName,
                            comp.address,
                            comp.latitude,
                            comp.longitude,
                            googleMapsLink,
                            selectedServiceBoyId!!,
                            selectedPocId,
                            selectedSupervisorId,
                            selectedMasterTaskIds.toList(),
                            scheduledDate
                        )
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Work...")
                } else {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE / ASSIGN WORK (${selectedMasterTaskIds.size} Tasks)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PersonnelSelectorRow(
    options: List<UserProfileDto>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { user ->
            val userId = user.id
            val isSelected = userId == selectedId
            val displayName = user.name.ifBlank { user.email }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(userId) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PrimaryLight else MaterialTheme.colorScheme.outlineVariant
                ),
                color = if (isSelected) PrimaryLight.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) PrimaryLight else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) PrimaryLight else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

