package com.example.taskmanagementapplication.data.local

import android.content.Context
import com.example.taskmanagementapplication.core.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Local offline snapshot data model.
 */
data class CachedWorkSnapshot(
    val work: Work,
    val additionalWork: List<AdditionalWorkItem> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * WorkLocalCache — persists safe read-only snapshots of the work, checklist,
 * photos, and additional work for offline viewing.
 */
class WorkLocalCache(
    private val context: Context? = null,
    private val baseDir: File? = null
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(CachedWorkSnapshot::class.java)

    private val cacheFile: File
        get() = File(baseDir ?: context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), "cached_work_snapshot.json")

    @Synchronized
    fun saveSnapshot(snapshot: CachedWorkSnapshot) {
        try {
            val json = adapter.toJson(snapshot)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            // Non-fatal logging for cache failure
        }
    }

    @Synchronized
    fun loadSnapshot(): CachedWorkSnapshot? {
        return try {
            if (!cacheFile.exists()) return null
            val json = cacheFile.readText()
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun getLastSyncedAt(): Long? {
        return loadSnapshot()?.lastSyncedAt
    }

    @Synchronized
    fun clear() {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
