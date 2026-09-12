package com.example.taskmanagementapplication.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapUtils {

    /**
     * Opens Google Maps directed to the specified address.
     * Tries:
     * 1. Google Maps app via geo URI with package com.google.android.apps.maps
     * 2. Fallback to Google Maps web URL (opens in browser or generic map viewer)
     * 3. Generic geo URI
     */
    fun openGoogleMaps(context: Context, address: String, label: String = "") {
        val query = if (label.isNotBlank()) "$label, $address" else address
        val encodedQuery = Uri.encode(query)

        // 1. Try Google Maps App directly
        val mapsAppUri = Uri.parse("geo:0,0?q=$encodedQuery")
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

        // 2. Fallback: Browser / Web Maps Intent
        val webMapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
        val webMapsIntent = Intent(Intent.ACTION_VIEW, webMapsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(webMapsIntent)
            return
        } catch (_: Exception) {}

        // 3. Fallback: Generic geo URI for any installed map application
        val genericGeoIntent = Intent(Intent.ACTION_VIEW, mapsAppUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(genericGeoIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No map application or browser found.", Toast.LENGTH_SHORT).show()
        }
    }
}
