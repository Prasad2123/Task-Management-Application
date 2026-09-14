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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.ui.SectionHeader
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalStatusScreen(
    onBack: () -> Unit,
    onContinueWork: () -> Unit,
    onProceedToComplete: () -> Unit,
    workViewModel: WorkViewModel
) {
    val work by workViewModel.work.collectAsStateWithLifecycle()
    val isReadyForCompletion = work.readyForCompletion || (work.pocApproved == true && work.supervisorApproved == true)
    val isApproved = work.status == WorkStatus.APPROVED || work.status == WorkStatus.COMPLETED || isReadyForCompletion
    val isRejected = work.status == WorkStatus.REJECTED || (work.pocApproved == false || work.supervisorApproved == false)

    LaunchedEffect(work.backendId) {
        work.backendId?.let { workViewModel.loadApprovals(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Approval Status", fontWeight = FontWeight.Bold) },
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

            // ── HERO BANNER ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isApproved -> StatusCompleted.copy(alpha = 0.12f)
                        isRejected -> ErrorRed.copy(alpha = 0.12f)
                        else -> PrimaryLight.copy(alpha = 0.12f)
                    }
                )
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
                            .background(
                                when {
                                    isApproved -> StatusCompleted
                                    isRejected -> ErrorRed
                                    else -> PrimaryLight
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isApproved -> Icons.Default.Verified
                                isRejected -> Icons.Default.WarningAmber
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            isReadyForCompletion -> "READY FOR COMPLETION"
                            isApproved -> "WORK APPROVED"
                            isRejected -> "CHANGES REQUIRED"
                            else -> "UNDER REVIEW"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isApproved -> StatusCompleted
                            isRejected -> ErrorRed
                            else -> PrimaryLight
                        },
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isReadyForCompletion -> "All approvals obtained! You are authorized to finalize work and depart site."
                            isApproved -> "Your work has been reviewed and approved by POC and Supervisor."
                            isRejected -> "Reviewers have requested revisions. Inspect feedback below and update work."
                            work.pocApproved == true -> "Approved by POC. Awaiting final Site Supervisor review."
                            else -> "Your work checklist and photos have been submitted for POC review."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── REJECTION REASON (IF REJECTED) ──
            if (isRejected) {
                val reason = work.supervisorRejectionReason ?: work.pocRejectionReason ?: "Changes requested."
                val reviewer = if (work.supervisorRejectionReason != null) "Site Supervisor (${work.supervisorName})" else "POC (${work.pocName})"

                SectionHeader(title = "Reviewer Feedback")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Feedback from $reviewer",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "“$reason”",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── APPROVAL STAGES ──
            SectionHeader(title = "Review Progress")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Stage 1: Submission
                    StatusRowItem(
                        title = "Work Submitted",
                        subtitle = "Submitted by ${work.serviceBoyName}",
                        status = "✓ Submitted",
                        time = work.submittedForReviewAt ?: "12:05 PM",
                        isSuccess = true,
                        isLast = false
                    )

                    // Stage 2: POC Review
                    StatusRowItem(
                        title = "POC Review",
                        subtitle = work.pocName,
                        status = when {
                            work.pocApproved == true -> "✓ Approved"
                            work.pocApproved == false -> "✕ Rejected"
                            else -> "In Review"
                        },
                        time = work.pocApprovalTime ?: if (work.pocApproved == false) "Rejected" else "Pending",
                        isSuccess = work.pocApproved == true,
                        isFailed = work.pocApproved == false,
                        isLast = false
                    )

                    // Stage 3: Supervisor Review
                    StatusRowItem(
                        title = "Supervisor Review",
                        subtitle = work.supervisorName,
                        status = when {
                            work.supervisorApproved == true -> "✓ Approved"
                            work.supervisorApproved == false -> "✕ Rejected"
                            work.pocApproved == true -> "Awaiting Review"
                            else -> "Pending POC"
                        },
                        time = work.supervisorApprovalTime ?: if (work.supervisorApproved == false) "Rejected" else "Pending",
                        isSuccess = work.supervisorApproved == true,
                        isFailed = work.supervisorApproved == false,
                        isLast = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── PRIMARY ACTION ──
            when {
                isApproved -> {
                    Button(
                        onClick = onProceedToComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .semantics { contentDescription = "Proceed to complete work" },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted)
                    ) {
                        Text("Proceed to Complete Work", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                isRejected -> {
                    Button(
                        onClick = {
                            workViewModel.continueWorkAfterRejection()
                            onContinueWork()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .semantics { contentDescription = "Continue work after rejection" },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue Work & Fix Issues", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                else -> {
                    Button(
                        onClick = onContinueWork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                    ) {
                        Text("Return to Work In Progress", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusRowItem(
    title: String,
    subtitle: String,
    status: String,
    time: String,
    isSuccess: Boolean = false,
    isFailed: Boolean = false,
    isLast: Boolean = false
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
                            isSuccess -> StatusCompleted.copy(alpha = 0.15f)
                            isFailed -> ErrorRed.copy(alpha = 0.15f)
                            else -> PrimaryLight.copy(alpha = 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isSuccess -> Icons.Default.Check
                        isFailed -> Icons.Default.Close
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when {
                        isSuccess -> StatusCompleted
                        isFailed -> ErrorRed
                        else -> PrimaryLight
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSuccess -> StatusCompleted
                        isFailed -> ErrorRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = "$subtitle • $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isLast) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
