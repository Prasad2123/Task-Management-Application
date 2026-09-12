package com.example.taskmanagementapplication.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress

data class WorkStep(
    val label: String,
    val state: StepState
)

enum class StepState { DONE, ACTIVE, PENDING }

@Composable
fun WorkProgressStepper(
    steps: List<WorkStep>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { index, step ->
            // Step node + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val circleColor by animateColorAsState(
                    targetValue = when (step.state) {
                        StepState.DONE -> StatusCompleted
                        StepState.ACTIVE -> PrimaryLight
                        StepState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(400),
                    label = "stepColor"
                )
                val contentColor = when (step.state) {
                    StepState.DONE -> Color.White
                    StepState.ACTIVE -> Color.White
                    StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                // Connector row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left connector
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (index == 0) Color.Transparent
                                else if (step.state != StepState.PENDING) StatusCompleted
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )

                    // Circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(circleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (step.state == StepState.DONE) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(contentColor)
                            )
                        }
                    }

                    // Right connector
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (index == steps.size - 1) Color.Transparent
                                else if (step.state == StepState.DONE) StatusCompleted
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (step.state) {
                        StepState.DONE -> StatusCompleted
                        StepState.ACTIVE -> PrimaryLight
                        StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (step.state == StepState.ACTIVE) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
