package com.example.taskmanagementapplication.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.StatusApproved
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress
import com.example.taskmanagementapplication.core.theme.StatusNotStarted
import com.example.taskmanagementapplication.core.theme.StatusRejected
import com.example.taskmanagementapplication.core.theme.StatusWaitingReview

/**
 * Reusable work status presentation card.
 *
 * Supports all work statuses:
 * - NOT STARTED
 * - WORK STARTED
 * - IN PROGRESS
 * - WAITING FOR REVIEW
 * - APPROVED
 * - REJECTED
 * - COMPLETED
 */
@Composable
fun WorkStatusCard(
    status: WorkStatus,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val config = when (status) {
        WorkStatus.NOT_STARTED -> StatusConfig(
            label = "NOT STARTED",
            icon = Icons.Default.Schedule,
            color = StatusNotStarted,
            description = "Work has not begun yet"
        )
        WorkStatus.WORK_STARTED -> StatusConfig(
            label = "WORK STARTED",
            icon = Icons.Default.PlayCircle,
            color = StatusInProgress,
            description = "Work session has initiated"
        )
        WorkStatus.IN_PROGRESS -> StatusConfig(
            label = "IN PROGRESS",
            icon = Icons.Default.HourglassTop,
            color = StatusInProgress,
            description = "Active work session in progress"
        )
        WorkStatus.WAITING_FOR_REVIEW,
        WorkStatus.WAITING_FOR_POC_REVIEW -> StatusConfig(
            label = "WAITING FOR REVIEW",
            icon = Icons.Default.PendingActions,
            color = StatusWaitingReview,
            description = "Pending POC review"
        )
        WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW -> StatusConfig(
            label = "WAITING FOR SUPERVISOR REVIEW",
            icon = Icons.Default.PendingActions,
            color = StatusWaitingReview,
            description = "Pending supervisor sign-off"
        )
        WorkStatus.APPROVED -> StatusConfig(
            label = "APPROVED",
            icon = Icons.Default.CheckCircle,
            color = StatusApproved,
            description = "Work verified and approved"
        )
        WorkStatus.REJECTED -> StatusConfig(
            label = "REJECTED",
            icon = Icons.Default.Cancel,
            color = StatusRejected,
            description = "Changes requested by reviewer"
        )
        WorkStatus.COMPLETED -> StatusConfig(
            label = "COMPLETED",
            icon = Icons.Default.TaskAlt,
            color = StatusCompleted,
            description = "Work completed successfully"
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(config.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = config.icon,
                    contentDescription = config.label,
                    tint = config.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = config.color
                )
                Text(
                    text = subtitle ?: config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class StatusConfig(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val description: String
)
