package com.example.taskmanagementapplication.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    ONLINE,
    OFFLINE,
    CHECKING,
    UNKNOWN
}

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
    val networkStatus: StateFlow<NetworkStatus>
}

class DefaultNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkStatus = MutableStateFlow(
        if (_isOnline.value) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
    )
    override val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    _networkStatus.value = NetworkStatus.ONLINE
                }

                override fun onLost(network: Network) {
                    val stillConnected = checkInitialConnectivity()
                    _isOnline.value = stillConnected
                    _networkStatus.value = if (stillConnected) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isOnline.value = hasInternet
                    _networkStatus.value = if (hasInternet) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
                }
            })
        } catch (e: Exception) {
            // In unit tests or mock environments where connectivityManager isn't available
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
