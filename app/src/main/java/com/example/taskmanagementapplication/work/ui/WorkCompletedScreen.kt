package com.example.taskmanagementapplication.work.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.core.ui.TimelineItem
import com.example.taskmanagementapplication.core.util.DateTimeUtils
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

@Composable
fun WorkCompletedScreen(
    onBackToHome: () -> Unit,
    onViewReport: () -> Unit = {},
    workViewModel: WorkViewModel
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val elapsedSeconds by workViewModel.elapsedSeconds.collectAsStateWithLifecycle()

    val scaleAnim = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val totalCompleted = workViewModel.getTotalCompletedCount(work)
    val totalCount = workViewModel.getTotalCount(work)
    val additionalCount = workViewModel.getAdditionalTotalCount(work)
    val photosCount = workViewModel.getTotalPhotosCount(work)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // ── CELEBRATION ICON ──
            Box(
                modifier = Modifier
                    .scale(scaleAnim.value)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(StatusCompleted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "✓ WORK COMPLETED",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StatusCompleted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Great work, ${work.serviceBoyName}!\nAll site tasks and approvals have been verified.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── SESSION STATISTICS CARD ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    StatRow("Work", work.title, isHighlight = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Location", work.companyName)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Completed at", DateTimeUtils.formatToIndiaTime(work.completedAt))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Total Duration", DateTimeUtils.formatFieldDuration(work.startTime, work.completedAt).ifBlank { workViewModel.formatElapsedTime(elapsedSeconds) })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("POC Review", "✓ Approved (${work.pocName})", isSuccess = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Supervisor Review", "✓ Approved (${work.supervisorName})", isSuccess = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Photos Evidence", "$photosCount photos")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatRow("Checklist", "$totalCompleted / $totalCount completed")

                    if (additionalCount > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        StatRow("Additional Work", "$additionalCount item${if (additionalCount != 1) "s" else ""}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── AUDIT TIMELINE ──
            if (work.activityLog.isNotEmpty()) {
                SectionHeader(title = "Complete Activity Timeline")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                Spacer(modifier = Modifier.height(28.dp))
            }

            // ── VIEW FINAL WORK REPORT BUTTON ──
            Button(
                onClick = onViewReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "View final work report" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Final Work Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── BACK TO HOME BUTTON ──
            androidx.compose.material3.OutlinedButton(
                onClick = onBackToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Back to home screen" },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Home", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun StatRow(
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
