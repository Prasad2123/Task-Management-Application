package com.example.taskmanagementapplication.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapUtils {

    /**
     * Opens Google Maps directed to authoritative work coordinates when available,
     * or falls back to the formatted text address.
     */
    fun openGoogleMaps(
        context: Context,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String,
        label: String = ""
    ) {
        val hasCoords = latitude != null && longitude != null && !latitude.isNaN() && !longitude.isNaN()

        val displayLabel = if (label.isNotBlank()) label else address
        val encodedLabel = Uri.encode(displayLabel)

        // 1. Construct URIs based on whether coordinates or text address are used
        val mapsAppUri = if (hasCoords) {
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
        } else {
            val query = if (label.isNotBlank()) "$label, $address" else address
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        }

        val webMapsUri = if (hasCoords) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        } else {
            val query = if (label.isNotBlank()) "$label, $address" else address
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        }

        // 2. Try Google Maps App directly
        val mapsAppIntent = Intent(Intent.ACTION_VIEW, mapsAppUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            if (mapsAppIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsAppIntent)
                return
            }
        } catch (_: Exception) {}

        // 3. Fallback: Browser / Web Maps Intent
        val webMapsIntent = Intent(Intent.ACTION_VIEW, webMapsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(webMapsIntent)
            return
        } catch (_: Exception) {}

        // 4. Fallback: Generic geo URI for any installed map application
        val genericGeoIntent = Intent(Intent.ACTION_VIEW, mapsAppUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(genericGeoIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No map application or browser found.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Backward-compatible overload for address-only calls.
     */
    fun openGoogleMaps(context: Context, address: String, label: String = "") {
        openGoogleMaps(context, null, null, address, label)
    }
}
