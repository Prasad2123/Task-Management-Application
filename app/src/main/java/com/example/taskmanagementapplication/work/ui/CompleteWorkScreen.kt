package com.example.taskmanagementapplication.work.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.core.ui.SwipeActionButton
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteWorkScreen(
    onBack: () -> Unit,
    onWorkCompleted: () -> Unit,
    workViewModel: WorkViewModel
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val elapsedSeconds by workViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val totalCompleted = workViewModel.getTotalCompletedCount(work)
    val totalCount = workViewModel.getTotalCount(work)
    val additionalCount = workViewModel.getAdditionalTotalCount(work)
    val photosCount = workViewModel.getTotalPhotosCount(work)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Complete Work", fontWeight = FontWeight.Bold) },
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
            Spacer(modifier = Modifier.height(10.dp))

            // ── HERO APPROVAL BANNER ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = StatusCompleted.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(StatusCompleted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "WORK APPROVED",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusCompleted,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your work evidence has been fully approved by the POC and Site Supervisor. You can now complete the job and leave the site.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── FINAL EVIDENCE SUMMARY ──
            SectionHeader(title = "Final Evidence Summary")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SummaryMetricRow(
                        label = "Work Session Duration",
                        value = workViewModel.formatElapsedTime(elapsedSeconds),
                        isHighlight = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SummaryMetricRow(
                        label = "Checklist Tasks Completed",
                        value = "$totalCompleted / $totalCount tasks"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SummaryMetricRow(
                        label = "Additional Work Logged",
                        value = "$additionalCount item${if (additionalCount != 1) "s" else ""}"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SummaryMetricRow(
                        label = "Work Photos Evidence",
                        value = "$photosCount photo${if (photosCount != 1) "s" else ""}"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SummaryMetricRow(
                        label = "POC Approval",
                        value = "✓ Approved (${work.pocName})",
                        isSuccess = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SummaryMetricRow(
                        label = "Supervisor Approval",
                        value = "✓ Approved (${work.supervisorName})",
                        isSuccess = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── SWIPE TO COMPLETE WORK ──
            SwipeActionButton(
                label = "SWIPE TO COMPLETE WORK",
                completedLabel = "✓  WORK COMPLETED!",
                trackColor = StatusCompleted,
                onSwipeComplete = {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    workViewModel.completeWork()
                    onWorkCompleted()
                }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SummaryMetricRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    isSuccess: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isSuccess -> StatusCompleted
                isHighlight -> PrimaryLight
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
