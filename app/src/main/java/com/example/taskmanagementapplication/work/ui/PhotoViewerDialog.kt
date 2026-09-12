package com.example.taskmanagementapplication.work.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.ui.PhotoStatusBadge
import com.example.taskmanagementapplication.core.ui.PhotoThumbnailView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerDialog(
    photos: List<WorkPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDeletePhoto: (String) -> Unit = {},
    onRetryPhoto: (String) -> Unit = {},
    isReadOnly: Boolean = false
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, photos.size - 1))
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val safeIndex = currentIndex.coerceIn(0, photos.size - 1)
    val currentPhoto = photos[safeIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Photo ${safeIndex + 1} of ${photos.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Uploaded ${currentPhoto.uploadedAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close viewer",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (!isReadOnly) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.semantics { contentDescription = "Delete this photo" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete photo",
                                    tint = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // Large Main Photo Frame with Animated Transition
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentPhoto,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "photoViewerTransition"
                    ) { photo ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            PhotoThumbnailView(
                                photo = photo,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Left Navigation Arrow (Previous)
                    if (photos.size > 1) {
                        IconButton(
                            onClick = {
                                if (safeIndex > 0) currentIndex = safeIndex - 1
                            },
                            enabled = safeIndex > 0,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .size(44.dp)
                                .background(
                                    color = if (safeIndex > 0) Color.Black.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .semantics { contentDescription = "Previous photo" }
                        ) {
                            if (safeIndex > 0) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Right Navigation Arrow (Next)
                        IconButton(
                            onClick = {
                                if (safeIndex < photos.size - 1) currentIndex = safeIndex + 1
                            },
                            enabled = safeIndex < photos.size - 1,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(44.dp)
                                .background(
                                    color = if (safeIndex < photos.size - 1) Color.Black.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .semantics { contentDescription = "Next photo" }
                        ) {
                            if (safeIndex < photos.size - 1) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Metadata Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentPhoto.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = currentPhoto.category.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            PhotoStatusBadge(
                                status = currentPhoto.uploadStatus,
                                onRetry = { onRetryPhoto(currentPhoto.id) }
                            )
                        }

                        if (!currentPhoto.caption.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "“${currentPhoto.caption}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }

                        // Retry button if failed
                        if (currentPhoto.uploadStatus == PhotoUploadStatus.FAILED) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onRetryPhoto(currentPhoto.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Upload", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Delete Confirmation Dialog
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = {
                        Text("Delete this photo?", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text("Are you sure you want to remove this work photo? This action cannot be undone.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val idToDelete = currentPhoto.id
                                showDeleteConfirm = false
                                onDeletePhoto(idToDelete)
                                if (photos.size <= 1) {
                                    onDismiss()
                                } else if (safeIndex >= photos.size - 1) {
                                    currentIndex = (photos.size - 2).coerceAtLeast(0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.semantics { contentDescription = "Confirm delete work photo" }
                        ) {
                            Text("DELETE", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.semantics { contentDescription = "Cancel delete photo" }
                        ) {
                            Text("CANCEL")
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}
