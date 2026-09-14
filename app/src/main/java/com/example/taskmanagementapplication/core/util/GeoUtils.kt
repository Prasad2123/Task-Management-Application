package com.example.taskmanagementapplication.core.util

import java.util.Locale

/**
 * Geographic calculation utility functions.
 * Computes great-circle distances using the Haversine formula on a spherical Earth (R = 6,371,000 m).
 * Pure Kotlin implementation ensures reliability in both Android runtime and JVM unit tests.
 */
object GeoUtils {

    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two GPS coordinates in meters.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)

        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Validates whether coordinate values are within legitimate WGS84 geographic bounds.
     */
    fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude.isNaN() || latitude.isInfinite() || longitude.isNaN() || longitude.isInfinite()) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Formats distance in meters into human-friendly string (e.g., "82 m" or "1.4 km").
     */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    /**
     * Formats GPS accuracy in meters (e.g., "±8 m").
     */
    fun formatAccuracy(accuracyMeters: Float?): String {
        return if (accuracyMeters != null) {
            String.format(Locale.getDefault(), "±%.0f m", accuracyMeters)
        } else {
            "Unknown"
        }
    }
}
