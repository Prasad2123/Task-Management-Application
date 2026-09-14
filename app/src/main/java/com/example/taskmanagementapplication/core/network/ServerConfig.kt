package com.example.taskmanagementapplication.core.network

import android.content.Context
import android.content.SharedPreferences
import com.example.taskmanagementapplication.BuildConfig

/**
 * Dynamic server connection configuration.
 * Sourced from BuildConfig by default, but allows field testing on physical
 * devices over LAN/Wi-Fi by overriding the server host/IP dynamically in-app.
 */
object ServerConfig {
    private const val PREFS_NAME = "server_config_prefs"
    private const val KEY_BASE_URL = "custom_base_url"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the active base URL with trailing slash.
     */
    fun getBaseUrl(context: Context? = null): String {
        val prefs = context?.let { getPrefs(it) }
        val custom = prefs?.getString(KEY_BASE_URL, null)
        val raw = if (!custom.isNullOrBlank() && !custom.contains("localhost") && !custom.contains("10.0.2.2") && !custom.contains(":8080")) {
            custom.trim()
        } else {
            getDefaultBaseUrl()
        }
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    /**
     * Determines the appropriate default base URL.
     * Always points to the online Supabase instance.
     */
    fun getDefaultBaseUrl(): String {
        return BuildConfig.SUPABASE_URL
    }

    /**
     * Sets or clears a custom base URL.
     */
    fun setBaseUrl(context: Context, url: String?) {
        val prefs = getPrefs(context)
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_BASE_URL).apply()
        } else {
            val formatted = url.trim()
            val finalUrl = if (formatted.endsWith("/")) formatted else "$formatted/"
            prefs.edit().putString(KEY_BASE_URL, finalUrl).apply()
        }
    }

    fun isCustomUrlConfigured(context: Context): Boolean {
        return !getPrefs(context).getString(KEY_BASE_URL, null).isNullOrBlank()
    }
}
