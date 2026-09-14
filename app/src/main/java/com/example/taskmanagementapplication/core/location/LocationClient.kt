package com.example.taskmanagementapplication.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of attempting to acquire device GPS coordinates.
 */
sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long
    ) : LocationResult()

    object PermissionDenied : LocationResult()
    object GpsDisabled : LocationResult()
    data class Error(val message: String) : LocationResult()
}

/**
 * Testable abstraction for device location acquisition.
 */
interface LocationClient {
    suspend fun getCurrentLocation(): LocationResult
}

/**
 * Production implementation using Google Play Services FusedLocationProviderClient
 * with fallback to Android LocationManager.
 */
class DefaultLocationClient(
    private val context: Context
) : LocationClient {

    private val fusedClient by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCurrentLocation(): LocationResult = withContext(Dispatchers.IO) {
        // 1. Check runtime permissions
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@withContext LocationResult.PermissionDenied
        }

        // 2. Check if device location services (GPS or Network) are enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

        if (!isGpsEnabled && !isNetworkEnabled) {
            return@withContext LocationResult.GpsDisabled
        }

        // 3. Try Google FusedLocationProviderClient
        try {
            val client = fusedClient
            if (client != null) {
                val cancellationTokenSource = CancellationTokenSource()
                val location: Location? = withTimeoutOrNull(LocationConstants.LOCATION_TIMEOUT_MS) {
                    client.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).awaitTask()
                }

                if (location != null) {
                    return@withContext LocationResult.Success(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        timestamp = location.time
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext LocationResult.PermissionDenied
        } catch (e: Exception) {
            // Fall through to LocationManager fallback
        }

        // 4. Fallback to LocationManager last known location
        try {
            if (locationManager != null) {
                val gpsLoc = if (isGpsEnabled) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                val netLoc = if (isNetworkEnabled) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

                val bestLocation = when {
                    gpsLoc != null && netLoc != null -> if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                    gpsLoc != null -> gpsLoc
                    else -> netLoc
                }

                if (bestLocation != null) {
                    return@withContext LocationResult.Success(
                        latitude = bestLocation.latitude,
                        longitude = bestLocation.longitude,
                        accuracyMeters = bestLocation.accuracy,
                        timestamp = bestLocation.time
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext LocationResult.PermissionDenied
        } catch (e: Exception) {
            return@withContext LocationResult.Error("Unable to acquire location: ${e.message}")
        }

        LocationResult.Error("Location fix timed out. Please ensure you have a clear view of the sky and try again.")
    }
}

/**
 * Await extension for Google Play Services Tasks using suspendCancellableCoroutine.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
