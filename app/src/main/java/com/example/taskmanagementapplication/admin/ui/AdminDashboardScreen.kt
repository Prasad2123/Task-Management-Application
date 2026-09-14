package com.example.taskmanagementapplication.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.auth.viewmodel.AuthViewModel
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.*
import com.example.taskmanagementapplication.core.ui.AvatarPlaceholder
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

private enum class AdminFilterTab(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    IN_REVIEW("Under Review"),
    APPROVED("Approved"),
    COMPLETED("Completed")
}

/**
 * Admin Operational Monitoring Dashboard.
 * Strictly read-only monitoring across all predefined service works.
 * NO task creation, assignment, or dispatch logic is permitted.
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
    val isLoading by workViewModel.isLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AdminFilterTab.ALL) }

    LaunchedEffect(Unit) {
        workViewModel.loadMyWork()
    }

    val filteredWorks = remember(predefinedWorks, searchQuery, selectedTab) {
        predefinedWorks.filter { work ->
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
                            text = "Admin Monitoring",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Operational Overview (Read-Only)",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Read-Only Compliance Notice
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
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = PrimaryLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Authoritative Read-Only Mode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryLight
                            )
                            Text(
                                text = "Service works are predefined and assigned automatically. Admin monitoring is strictly observation-only.",
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
    val (statusLabel, statusColor) = when (work.status) {
        WorkStatus.NOT_STARTED -> "ASSIGNED" to PrimaryLight
        WorkStatus.WORK_STARTED, WorkStatus.IN_PROGRESS -> "IN PROGRESS" to StatusInProgress
        WorkStatus.WAITING_FOR_POC_REVIEW -> "POC REVIEW" to StatusWaitingReview
        WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, WorkStatus.WAITING_FOR_REVIEW -> "SUPERVISOR REVIEW" to AccentOrange
        WorkStatus.APPROVED -> "APPROVED" to StatusCompleted
        WorkStatus.COMPLETED -> "COMPLETED" to StatusCompleted
        WorkStatus.REJECTED -> "CHANGES REQUESTED" to ErrorRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = work.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = work.companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Personnel Authorizations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AdminPersonnelItem(
                    label = "Service Engineer",
                    name = work.serviceBoyName.ifBlank { "Assigned Engineer" }
                )
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

            // GPS & Execution metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (work.latitude != null) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (work.latitude != null && work.longitude != null) {
                            "${"%.4f".format(work.latitude)}, ${"%.4f".format(work.longitude)} (150m)"
                        } else {
                            "Site Geofence (150m)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${work.photos.size} Photos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
