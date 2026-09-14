package com.example.taskmanagementapplication.core.location

/**
 * Constants governing GPS acquisition, accuracy thresholds, and geofence verification.
 */
object LocationConstants {

    /** Default allowed radius in meters around the work site if not specified by backend. */
    const val DEFAULT_ALLOWED_RADIUS_METERS = 150.0

    /**
     * Maximum acceptable GPS accuracy in meters.
     * If the reported accuracy is worse (e.g. ±120m), the app warns the user and requests a fresh fix.
     */
    const val MAX_ACCURACY_THRESHOLD_METERS = 100.0f

    /** Location request timeout in milliseconds. */
    const val LOCATION_TIMEOUT_MS = 15000L
}
