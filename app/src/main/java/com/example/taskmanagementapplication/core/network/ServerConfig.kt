package com.example.taskmanagementapplication.core.network

import android.content.Context
import com.example.taskmanagementapplication.BuildConfig

/**
 * Authoritative server connection configuration.
 * Strictly locked to online Supabase HTTPS backend via BuildConfig.SUPABASE_URL.
 */
object ServerConfig {

    /**
     * Returns the active base URL with trailing slash.
     * Guaranteed pure online Supabase HTTPS endpoint.
     */
    fun getBaseUrl(context: Context? = null): String {
        val raw = BuildConfig.SUPABASE_URL.trim()
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    fun getDefaultBaseUrl(): String {
        return BuildConfig.SUPABASE_URL
    }

    fun setBaseUrl(context: Context, url: String?) {
        // No-op in pure Supabase online architecture
    }

    fun isCustomUrlConfigured(context: Context): Boolean {
        return false
    }
}
