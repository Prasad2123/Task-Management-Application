package com.example.taskmanagementapplication.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanagementapplication.core.network.NetworkStatus
import kotlinx.coroutines.delay

/**
 * Global Network Status Bar.
 * Shows a subtle persistent amber banner when OFFLINE ("You're offline — viewing cached data"),
 * and a brief green banner when connection is restored ("Back online — syncing...").
 */
@Composable
fun NetworkStatusBar(
    networkStatus: NetworkStatus,
    lastSyncedText: String? = null,
    modifier: Modifier = Modifier
) {
    var previousStatus by remember { mutableStateOf<NetworkStatus?>(null) }
    var showBackOnlineBanner by remember { mutableStateOf(false) }

    LaunchedEffect(networkStatus) {
        if (previousStatus == NetworkStatus.OFFLINE && networkStatus == NetworkStatus.ONLINE) {
            showBackOnlineBanner = true
            delay(3000)
            showBackOnlineBanner = false
        }
        previousStatus = networkStatus
    }

    val isOffline = networkStatus == NetworkStatus.OFFLINE

    AnimatedVisibility(
        visible = isOffline || showBackOnlineBanner,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        if (isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE65100)) // Amber / Deep Orange
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Offline",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val message = if (!lastSyncedText.isNullOrBlank()) {
                    "You're offline — viewing cached data (Last synced: $lastSyncedText)"
                } else {
                    "You're offline — viewing cached data"
                }
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (showBackOnlineBanner) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E7D32)) // Forest Green
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Back online",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Back online — connected to server",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
