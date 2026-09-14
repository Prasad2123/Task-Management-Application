package com.example.taskmanagementapplication.home.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanagementapplication.auth.viewmodel.AuthViewModel
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress
import com.example.taskmanagementapplication.core.ui.AvatarPlaceholder
import com.example.taskmanagementapplication.core.ui.NotificationBottomSheet
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.core.ui.StepState
import com.example.taskmanagementapplication.core.ui.WorkCard
import com.example.taskmanagementapplication.core.ui.WorkProgressStepper
import com.example.taskmanagementapplication.core.ui.WorkStep
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

private enum class ServiceBoyTab { HOME, WORK, PROFILE }

@Composable
fun ServiceBoyHomeScreen(
    authViewModel: AuthViewModel,
    onViewWork: () -> Unit,
    onOpenProfile: () -> Unit,
    onViewLocation: () -> Unit = {},
    workViewModel: WorkViewModel = viewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val predefinedWorks by workViewModel.predefinedWorks.collectAsStateWithLifecycle()
    val activeWorks = remember(predefinedWorks) {
        predefinedWorks.filter { it.status != WorkStatus.COMPLETED }
    }
    val completedWorks = remember(predefinedWorks) {
        predefinedWorks.filter { it.status == WorkStatus.COMPLETED }
    }
    val hasSelectedWork = work.backendId != null && activeWorks.any { it.backendId == work.backendId }
    val elapsedSeconds by workViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val notifications by workViewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotificationsCount = notifications.count { !it.isRead }
    var selectedTab by remember { mutableStateOf(ServiceBoyTab.HOME) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val totalCompleted = if (hasSelectedWork) workViewModel.getTotalCompletedCount(work) else 0
    val totalCount = if (hasSelectedWork) workViewModel.getTotalCount(work) else 0
    val checklistProgress = if (hasSelectedWork) workViewModel.getProgressFraction(work) else 0f
    val photosCount = if (hasSelectedWork) work.photos.size else 0

    // Progress steps based on current status
    val steps = listOf(
        WorkStep(
            label = "Start\nWork",
            state = when (work.status) {
                WorkStatus.NOT_STARTED -> StepState.ACTIVE
                else -> StepState.DONE
            }
        ),
        WorkStep(
            label = "In\nProgress",
            state = when (work.status) {
                WorkStatus.NOT_STARTED -> StepState.PENDING
                WorkStatus.IN_PROGRESS -> StepState.ACTIVE
                else -> StepState.DONE
            }
        ),
        WorkStep(
            label = "Review",
            state = when (work.status) {
                WorkStatus.NOT_STARTED, WorkStatus.IN_PROGRESS -> StepState.PENDING
                WorkStatus.WAITING_FOR_POC_REVIEW,
                WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW -> StepState.ACTIVE
                else -> StepState.DONE
            }
        ),
        WorkStep(
            label = "Complete",
            state = when (work.status) {
                WorkStatus.COMPLETED -> StepState.DONE
                WorkStatus.APPROVED -> StepState.ACTIVE
                else -> StepState.PENDING
            }
        )
    )

    val errorMessage by workViewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        workViewModel.loadMyWork()
        workViewModel.loadNotifications()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            workViewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == ServiceBoyTab.HOME,
                    onClick = { selectedTab = ServiceBoyTab.HOME },
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLight,
                        selectedTextColor = PrimaryLight,
                        indicatorColor = PrimaryLight.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == ServiceBoyTab.WORK,
                    onClick = { selectedTab = ServiceBoyTab.WORK; onViewWork() },
                    icon = { Icon(Icons.Default.Work, "Work") },
                    label = { Text("Work") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLight,
                        selectedTextColor = PrimaryLight,
                        indicatorColor = PrimaryLight.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == ServiceBoyTab.PROFILE,
                    onClick = {
                        selectedTab = ServiceBoyTab.PROFILE
                        onOpenProfile()
                    },
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLight,
                        selectedTextColor = PrimaryLight,
                        indicatorColor = PrimaryLight.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            // ── HEADER ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(listOf(PrimaryLight, Color(0xFF1565C0)))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Good Morning,",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = currentUser?.name?.split(" ")?.firstOrNull() ?: "Service Engineer",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Let's complete today's work.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Notification Bell
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(containerColor = AccentOrange) {
                                        Text(unreadNotificationsCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    AvatarPlaceholder(name = currentUser?.name ?: "Service Engineer", size = 56.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                // ── PREDEFINED WORKS SELECTOR (WHEN MULTIPLE ASSIGNED) ──
                if (activeWorks.size > 1) {
                    SectionHeader(title = "Your Predefined Works (${activeWorks.size})")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select a predefined work to inspect and begin:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            activeWorks.forEach { item ->
                                val isSelected = work.backendId == item.backendId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { workViewModel.selectWork(item) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) PrimaryLight.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    border = if (isSelected) BorderStroke(1.5.dp, PrimaryLight) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { item.companyName },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) PrimaryLight else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.companyName.ifBlank { item.address },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = when (item.status) {
                                                    WorkStatus.NOT_STARTED -> "Not Started"
                                                    WorkStatus.IN_PROGRESS, WorkStatus.WORK_STARTED -> "In Progress"
                                                    WorkStatus.REJECTED -> "Changes Needed"
                                                    WorkStatus.APPROVED -> "Approved"
                                                    else -> "Pending Review"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = when (item.status) {
                                                    WorkStatus.NOT_STARTED -> AccentOrange
                                                    WorkStatus.IN_PROGRESS, WorkStatus.WORK_STARTED -> StatusInProgress
                                                    WorkStatus.REJECTED -> ErrorRed
                                                    else -> PrimaryLight
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Active Selection",
                                                tint = PrimaryLight,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            OutlinedButton(
                                                onClick = { workViewModel.selectWork(item) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Select", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (hasSelectedWork) {
                    // ── TODAY'S COMPACT SUMMARY METRICS ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeMetricCard(
                            label = "WORK TIME",
                            value = workViewModel.formatElapsedTime(elapsedSeconds),
                            color = PrimaryLight,
                            modifier = Modifier.weight(1f)
                        )
                        HomeMetricCard(
                            label = "PHOTOS",
                            value = photosCount.toString(),
                            color = StatusInProgress,
                            modifier = Modifier.weight(1f)
                        )
                        HomeMetricCard(
                            label = "CHECKLIST",
                            value = "$totalCompleted/$totalCount",
                            color = if (totalCompleted == totalCount && totalCount > 0) StatusCompleted else AccentOrange,
                            modifier = Modifier.weight(1f)
                        )
                        HomeMetricCard(
                            label = "STATUS",
                            value = when (work.status) {
                                WorkStatus.NOT_STARTED -> "Not Started"
                                WorkStatus.IN_PROGRESS -> "In Progress"
                                WorkStatus.APPROVED -> "Approved"
                                WorkStatus.COMPLETED -> "Done"
                                WorkStatus.REJECTED -> "Changes"
                                else -> "Review"
                            },
                            color = when (work.status) {
                                WorkStatus.APPROVED, WorkStatus.COMPLETED -> StatusCompleted
                                WorkStatus.REJECTED -> ErrorRed
                                else -> PrimaryLight
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── WORK PROGRESS STEPPER ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Work Progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                // Status chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            when (work.status) {
                                                WorkStatus.NOT_STARTED -> AccentOrange.copy(alpha = 0.12f)
                                                WorkStatus.IN_PROGRESS -> StatusInProgress.copy(alpha = 0.12f)
                                                WorkStatus.COMPLETED -> StatusCompleted.copy(alpha = 0.12f)
                                                WorkStatus.REJECTED -> ErrorRed.copy(alpha = 0.12f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (work.status) {
                                            WorkStatus.NOT_STARTED -> "Not Started"
                                            WorkStatus.IN_PROGRESS -> "In Progress"
                                            WorkStatus.COMPLETED -> "Completed"
                                            WorkStatus.APPROVED -> "Approved"
                                            WorkStatus.REJECTED -> "Changes Needed"
                                            else -> "Pending Review"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (work.status) {
                                            WorkStatus.NOT_STARTED -> AccentOrange
                                            WorkStatus.IN_PROGRESS -> StatusInProgress
                                            WorkStatus.COMPLETED, WorkStatus.APPROVED -> StatusCompleted
                                            WorkStatus.REJECTED -> ErrorRed
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            WorkProgressStepper(steps = steps)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── TODAY'S WORK ──
                    val actionLabel = when (work.status) {
                        WorkStatus.NOT_STARTED -> "Start Work"
                        WorkStatus.WORK_STARTED, WorkStatus.IN_PROGRESS -> "Continue Work"
                        WorkStatus.WAITING_FOR_REVIEW,
                        WorkStatus.WAITING_FOR_POC_REVIEW,
                        WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW -> "View Review Status"
                        WorkStatus.APPROVED -> "Complete Work"
                        WorkStatus.COMPLETED -> "View Completed Work"
                        WorkStatus.REJECTED -> "Review Changes Required"
                    }

                    SectionHeader(title = if (activeWorks.size > 1) "Active Work Details" else "Today's Work")
                    WorkCard(
                        work = work,
                        onActionClick = onViewWork,
                        actionLabel = actionLabel,
                        progressFraction = checklistProgress
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── LOCATION CARD ──
                    SectionHeader(title = "Work Location")
                    LocationCard(
                        companyName = work.companyName,
                        address = work.address,
                        distance = work.distance,
                        onViewLocation = onViewLocation
                    )
                } else if (activeWorks.size > 1) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Work, contentDescription = null, tint = PrimaryLight)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Please select one of your predefined works above to inspect details and begin field work.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (activeWorks.isEmpty() && completedWorks.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = StatusCompleted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "All Predefined Works Completed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You have completed all assigned tasks for today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            completedWorks.forEach { comp ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(comp.companyName.ifBlank { comp.title }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(comp.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                workViewModel.selectWork(comp)
                                                onViewWork()
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("View Report", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Work, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Predefined Works Assigned", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Your predefined works will appear here once configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // ── NOTIFICATIONS BOTTOM SHEET ──
    if (showNotificationsSheet) {
        NotificationBottomSheet(
            notifications = notifications,
            onDismiss = { showNotificationsSheet = false },
            onNotificationClick = { notificationId ->
                workViewModel.markNotificationRead(notificationId)
                showNotificationsSheet = false
                onViewWork()
            },
            onMarkAllAsRead = {
                workViewModel.markAllNotificationsAsRead()
            }
        )
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LocationCard(
    companyName: String,
    address: String,
    distance: String,
    onViewLocation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewLocation() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryLight.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = PrimaryLight,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = companyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryLight.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "📍 $distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryLight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
