package com.example.taskmanagementapplication.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanagementapplication.core.theme.PrimaryLight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A reliable, smooth swipe-to-confirm interaction using Jetpack Compose's draggable modifier.
 *
 * Features:
 * - Real drag gesture handling with nested scroll protection
 * - Animated progress fill track behind the thumb
 * - Spring snap-back if released prior to threshold
 * - Strict single-trigger execution guard
 * - Dynamic text alpha fade-out
 * - Polished success transformation
 */
@Composable
fun SwipeActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    completedLabel: String = "✓  Done!",
    trackColor: Color = PrimaryLight,
    thumbColor: Color = Color.White,
    completedIcon: ImageVector = Icons.Default.Check,
    threshold: Float = 0.65f,
    onProgressChanged: ((Float) -> Unit)? = null,
    onSwipeComplete: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 56.dp
    val paddingDp = 6.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - (paddingPx * 2)).coerceAtLeast(0f)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }

    val progress = if (maxDragPx > 0f) (dragOffset / maxDragPx).coerceIn(0f, 1f) else 0f

    val textAlpha = if (progress > 0.40f) 0f else (1f - (progress / 0.40f)).coerceIn(0f, 1f)

    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF2E7D32) else trackColor,
        animationSpec = tween(300),
        label = "bgColor"
    )

    // Reset when re-enabled
    LaunchedEffect(enabled) {
        if (enabled && !isCompleted) {
            dragOffset = 0f
            hasTriggered = false
        }
    }

    LaunchedEffect(progress) {
        onProgressChanged?.invoke(progress)
    }

    val draggableState = rememberDraggableState { delta ->
        if (enabled && !isCompleted && !hasTriggered && maxDragPx > 0f) {
            dragOffset = (dragOffset + delta).coerceIn(0f, maxDragPx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(bgColor.copy(alpha = if (enabled) 0.95f else 0.45f))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled && !isCompleted && !hasTriggered,
                onDragStopped = { _ ->
                    if (enabled && !isCompleted && !hasTriggered && maxDragPx > 0f) {
                        val currentProgress = (dragOffset / maxDragPx).coerceIn(0f, 1f)
                        if (currentProgress >= threshold) {
                            hasTriggered = true
                            scope.launch {
                                val anim = Animatable(dragOffset)
                                anim.animateTo(
                                    targetValue = maxDragPx,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                ) {
                                    dragOffset = value
                                }
                                isCompleted = true
                                onSwipeComplete()
                            }
                        } else {
                            scope.launch {
                                val anim = Animatable(dragOffset)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    dragOffset = value
                                }
                            }
                        }
                    }
                }
            )
            .semantics {
                contentDescription = if (isCompleted) completedLabel else label
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Active Progress Fill behind the thumb
        if (!isCompleted && dragOffset > 0f) {
            val fillWidthDp = with(density) { (dragOffset + thumbSizePx + paddingPx).toDp() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidthDp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.White.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }

        // Background label text
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!isCompleted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(textAlpha)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Text(
                    text = completedLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((dragOffset + paddingPx).roundToInt(), 0) }
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompleted) completedIcon else icon,
                contentDescription = if (isCompleted) "Completed" else "Swipe thumb",
                tint = if (isCompleted) Color(0xFF2E7D32) else trackColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
