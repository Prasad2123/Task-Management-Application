package com.example.taskmanagementapplication.work.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusFailed
import com.example.taskmanagementapplication.core.theme.StatusPending
import com.example.taskmanagementapplication.core.ui.SwipeActionButton
import com.example.taskmanagementapplication.core.util.GeoUtils
import com.example.taskmanagementapplication.core.util.MapUtils
import com.example.taskmanagementapplication.work.viewmodel.LocationVerificationStatus
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartWorkScreen(
    onBack: () -> Unit,
    onWorkStarted: () -> Unit,
    workViewModel: WorkViewModel = viewModel()
) {
    val context = LocalContext.current
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val locationState by workViewModel.locationState.collectAsStateWithLifecycle()
    val isSubmitting by workViewModel.isSubmitting.collectAsStateWithLifecycle()
    val errorMessage by workViewModel.errorMessage.collectAsStateWithLifecycle()

    var showSuccess by remember { mutableStateOf(false) }

    // Permission launcher for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        workViewModel.refreshLocation()
    }

    // Acquire GPS location on launch
    LaunchedEffect(Unit) {
        workViewModel.refreshLocation()
    }

    // After success animation, navigate to work session
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1500L)
            onWorkStarted()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Start Work", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── BACKEND ERROR BANNER (IF REJECTED) ──
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusFailed.copy(alpha = 0.08f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(StatusFailed, StatusFailed)))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusFailed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusFailed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { workViewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusFailed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── LOCATION VERIFICATION CARD ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Radar Icon
                    LocationStatusRadar(status = locationState.status, isRefreshing = locationState.isRefreshing)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status Title & Message
                    when (locationState.status) {
                        LocationVerificationStatus.CHECKING_LOCATION -> {
                            Text(
                                text = "Acquiring GPS Location...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connecting to satellites for authoritative site verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        LocationVerificationStatus.PERMISSION_REQUIRED -> {
                            Text(
                                text = "Location Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusPending
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Field Service Management requires real GPS location to verify that you are on site before beginning work.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                            ) {
                                Icon(Icons.Default.LocationSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Permission")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Open App Settings", fontSize = 13.sp)
                            }
                        }
                        LocationVerificationStatus.GPS_DISABLED -> {
                            Text(
                                text = "GPS Services Disabled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusFailed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please enable location services on your device to verify your presence at the work site.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Location Settings")
                            }
                        }
                        LocationVerificationStatus.POOR_ACCURACY -> {
                            Text(
                                text = "Weak GPS Signal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusPending
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GPS accuracy is ${GeoUtils.formatAccuracy(locationState.accuracyMeters)}. Please step into an open area with a clear sky view and tap Refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        LocationVerificationStatus.TOO_FAR -> {
                            Text(
                                text = "Outside Permitted Work Site",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusFailed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You must be within ${GeoUtils.formatDistance(locationState.allowedRadiusMeters)} of the site to start work.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        LocationVerificationStatus.VERIFIED -> {
                            Text(
                                text = "You're at the Work Site",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusCompleted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GPS coordinates verified within allowed radius (${GeoUtils.formatDistance(locationState.allowedRadiusMeters)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        LocationVerificationStatus.LOCATION_UNAVAILABLE -> {
                            Text(
                                text = "Location Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = StatusFailed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = locationState.errorMessage ?: "Unable to establish GPS fix. Please ensure location is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Distance & Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LocationChip(
                            icon = Icons.Default.Straighten,
                            label = "Distance",
                            value = locationState.distanceFromWorkMeters?.let { GeoUtils.formatDistance(it) } ?: "---",
                            color = if (locationState.isInsideRadius) StatusCompleted else if (locationState.distanceFromWorkMeters != null) StatusFailed else PrimaryLight
                        )
                        LocationChip(
                            icon = Icons.Default.Adjust,
                            label = "Allowed Radius",
                            value = GeoUtils.formatDistance(locationState.allowedRadiusMeters),
                            color = PrimaryLight
                        )
                        LocationChip(
                            icon = Icons.Default.GpsFixed,
                            label = "Accuracy",
                            value = GeoUtils.formatAccuracy(locationState.accuracyMeters),
                            color = if ((locationState.accuracyMeters ?: 999f) <= 50f) StatusCompleted else StatusPending
                        )
                        LocationChip(
                            icon = if (locationState.status == LocationVerificationStatus.VERIFIED) Icons.Default.CheckCircle else Icons.Default.Shield,
                            label = "Status",
                            value = when (locationState.status) {
                                LocationVerificationStatus.VERIFIED -> "Verified"
                                LocationVerificationStatus.TOO_FAR -> "Too Far"
                                LocationVerificationStatus.CHECKING_LOCATION -> "Checking"
                                LocationVerificationStatus.PERMISSION_REQUIRED -> "Perm. Req."
                                LocationVerificationStatus.GPS_DISABLED -> "Off"
                                LocationVerificationStatus.POOR_ACCURACY -> "Weak"
                                LocationVerificationStatus.LOCATION_UNAVAILABLE -> "Error"
                            },
                            color = when (locationState.status) {
                                LocationVerificationStatus.VERIFIED -> StatusCompleted
                                LocationVerificationStatus.TOO_FAR, LocationVerificationStatus.GPS_DISABLED, LocationVerificationStatus.LOCATION_UNAVAILABLE -> StatusFailed
                                LocationVerificationStatus.POOR_ACCURACY, LocationVerificationStatus.PERMISSION_REQUIRED -> StatusPending
                                LocationVerificationStatus.CHECKING_LOCATION -> PrimaryLight
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Actions: Refresh Location + Open in Maps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { workViewModel.refreshLocation() },
                            enabled = !locationState.isRefreshing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (locationState.isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh GPS")
                        }

                        OutlinedButton(
                            onClick = {
                                MapUtils.openGoogleMaps(
                                    context = context,
                                    latitude = work.latitude,
                                    longitude = work.longitude,
                                    address = work.address,
                                    label = work.companyName
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Map")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── WORK DESTINATION CARD ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryLight.copy(alpha = 0.06f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Work, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = work.companyName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = work.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = work.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (work.latitude != null && work.longitude != null) {
                            Text(
                                text = "Authoritative GPS: ${String.format("%.4f, %.4f", work.latitude, work.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryLight,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── CHECKLIST PREVIEW ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "📋 Checklist Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    work.checklist.take(3).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryLight)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (work.checklist.size > 3) {
                        Text(
                            text = "+${work.checklist.size - 3} more items",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── SUCCESS OVERLAY (ONLY AFTER BACKEND CONFIRMATION) ──
            AnimatedVisibility(
                visible = showSuccess,
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(StatusCompleted.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusCompleted,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Work Started Successfully!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusCompleted
                    )
                    Text(
                        text = "Authoritative timestamp: ${work.startTime ?: "Recorded"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── SWIPE TO START OR BLOCKED BANNER ──
            if (!showSuccess) {
                if (locationState.status == LocationVerificationStatus.VERIFIED) {
                    Text(
                        text = if (isSubmitting) "Starting work and recording location..." else "Swipe right to begin your work session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SwipeActionButton(
                        label = if (isSubmitting) "STARTING WORK..." else "SWIPE TO START WORK",
                        completedLabel = "✓  Location Verified & Started!",
                        onSwipeComplete = {
                            workViewModel.startWork {
                                showSuccess = true
                            }
                        }
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Start Work is locked until you reach the verified work location (${GeoUtils.formatDistance(locationState.allowedRadiusMeters)}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun LocationStatusRadar(status: LocationVerificationStatus, isRefreshing: Boolean) {
    val baseColor = when (status) {
        LocationVerificationStatus.VERIFIED -> StatusCompleted
        LocationVerificationStatus.TOO_FAR, LocationVerificationStatus.GPS_DISABLED, LocationVerificationStatus.LOCATION_UNAVAILABLE -> StatusFailed
        LocationVerificationStatus.POOR_ACCURACY, LocationVerificationStatus.PERMISSION_REQUIRED -> StatusPending
        LocationVerificationStatus.CHECKING_LOCATION -> PrimaryLight
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = 0.16f))
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(baseColor),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else {
                val icon = when (status) {
                    LocationVerificationStatus.VERIFIED -> Icons.Default.Check
                    LocationVerificationStatus.TOO_FAR -> Icons.Default.WrongLocation
                    LocationVerificationStatus.PERMISSION_REQUIRED -> Icons.Default.LocationDisabled
                    LocationVerificationStatus.GPS_DISABLED -> Icons.Default.GpsOff
                    LocationVerificationStatus.POOR_ACCURACY -> Icons.Default.GpsNotFixed
                    else -> Icons.Default.MyLocation
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun LocationChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}
