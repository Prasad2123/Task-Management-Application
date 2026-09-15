package com.example.taskmanagementapplication.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.taskmanagementapplication.data.local.TokenManager
import com.example.taskmanagementapplication.data.network.NetworkModule
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.theme.AccentOrange
import com.example.taskmanagementapplication.core.theme.ErrorRed
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import com.example.taskmanagementapplication.core.theme.StatusCompleted
import com.example.taskmanagementapplication.core.theme.StatusInProgress

@Composable
fun PhotoCard(
    photo: WorkPhoto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    isSelectable: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryLight else Color.Transparent,
        label = "photoSelectionBorder"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                if (isSelectable) onToggleSelect() else onClick()
            }
            .semantics {
                contentDescription = "Work photo ${photo.title}, status ${photo.uploadStatus}"
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Thumbnail container with aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
            ) {
                PhotoThumbnailView(
                    photo = photo,
                    modifier = Modifier.fillMaxSize()
                )

                // Top bar overlay: Category chip & Action menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = photo.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Three-dot menu / Delete icon
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .semantics { contentDescription = "More options for ${photo.title}" }
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Photo", color = ErrorRed) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                            if (photo.uploadStatus == PhotoUploadStatus.FAILED) {
                                DropdownMenuItem(
                                    text = { Text("Retry Upload") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryLight)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onRetry()
                                    }
                                )
                            }
                        }
                    }
                }

                // Selection checkmark overlay
                if (isSelectable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) PrimaryLight else Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Photo metadata footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Title
                Text(
                    text = photo.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Upload status badge & Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhotoStatusBadge(
                        status = photo.uploadStatus,
                        onRetry = onRetry
                    )

                    Text(
                        text = photo.uploadedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Optional caption note
                if (!photo.caption.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“${photo.caption}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoStatusBadge(
    status: PhotoUploadStatus,
    onRetry: () -> Unit = {}
) {
    when (status) {
        PhotoUploadStatus.UPLOADED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StatusCompleted.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusCompleted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Uploaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusCompleted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
        PhotoUploadStatus.UPLOADING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StatusInProgress.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = StatusInProgress
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Uploading...",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusInProgress,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }
        PhotoUploadStatus.FAILED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ErrorRed.copy(alpha = 0.12f))
                    .clickable { onRetry() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics { contentDescription = "Retry photo upload" }
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
        PhotoUploadStatus.PENDING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentOrange.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Procedural mock field evidence renderer.
 * Produces crisp, beautiful, authentic-looking field inspection photos
 * with category-tailored gradients, subtle technical viewfinder grid, and category iconography.
 */
@Composable
fun PhotoThumbnailView(
    photo: WorkPhoto,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val imageSource: Any? = photo.localUri ?: photo.remoteUrl

    if (imageSource != null) {
        val context = LocalContext.current
        val tokenManager = remember { TokenManager(context) }
        val imageLoader = remember(tokenManager) { NetworkModule.createImageLoader(context, tokenManager) }

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageSource)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = photo.title,
            contentScale = contentScale,
            modifier = modifier,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryLight
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Failed to load photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ProceduralPhotoThumbnail(
    photo: WorkPhoto,
    modifier: Modifier = Modifier
) {
    val (gradientColors, icon) = getPhotoVisuals(photo.category, photo.gradientSeed)

    Box(
        modifier = modifier
            .background(Brush.linearGradient(colors = gradientColors))
    ) {
        // Technical grid overlay & viewfinder
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridColor = Color.White.copy(alpha = 0.08f)
            val cornerColor = Color.White.copy(alpha = 0.28f)
            val cornerLen = 14.dp.toPx()
            val strokeW = 1.5.dp.toPx()

            // Viewfinder grid lines
            drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1f)

            // Viewfinder corners
            val pad = 8.dp.toPx()
            // Top-left
            drawLine(cornerColor, Offset(pad, pad), Offset(pad + cornerLen, pad), strokeW)
            drawLine(cornerColor, Offset(pad, pad), Offset(pad, pad + cornerLen), strokeW)
            // Top-right
            drawLine(cornerColor, Offset(w - pad, pad), Offset(w - pad - cornerLen, pad), strokeW)
            drawLine(cornerColor, Offset(w - pad, pad), Offset(w - pad, pad + cornerLen), strokeW)
            // Bottom-left
            drawLine(cornerColor, Offset(pad, h - pad), Offset(pad + cornerLen, h - pad), strokeW)
            drawLine(cornerColor, Offset(pad, h - pad), Offset(pad, h - pad - cornerLen), strokeW)
            // Bottom-right
            drawLine(cornerColor, Offset(w - pad, h - pad), Offset(w - pad - cornerLen, h - pad), strokeW)
            drawLine(cornerColor, Offset(w - pad, h - pad), Offset(w - pad, h - pad - cornerLen), strokeW)
        }

        // Center Icon badge with glassmorphism
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Lens watermark / evidence tag at bottom left
        Text(
            text = "EVIDENCE #${photo.id.takeLast(4)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

private fun getPhotoVisuals(
    category: PhotoCategory,
    seed: Int
): Pair<List<Color>, ImageVector> {
    return when (category) {
        PhotoCategory.SITE_INSPECTION -> Pair(
            listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
            Icons.Default.PhotoCamera
        )
        PhotoCategory.TREATMENT_APPLICATION -> Pair(
            listOf(Color(0xFF005C53), Color(0xFF042940)),
            Icons.Default.PestControl
        )
        PhotoCategory.EQUIPMENT_CHECK -> Pair(
            listOf(Color(0xFF2C3E50), Color(0xFF3498DB)),
            Icons.Default.Engineering
        )
        PhotoCategory.SAFETY_PPE -> Pair(
            listOf(Color(0xFF3A3D40), Color(0xFFD43F3A)),
            Icons.Default.Security
        )
        PhotoCategory.ADDITIONAL_WORK -> Pair(
            listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)),
            Icons.Default.Science
        )
        PhotoCategory.GENERAL -> {
            val palette = when (seed % 3) {
                1 -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                2 -> listOf(Color(0xFF141E30), Color(0xFF243B55))
                else -> listOf(Color(0xFF1F4037), Color(0xFF99F2C8))
            }
            Pair(palette, Icons.Default.PhotoCamera)
        }
    }
}
