package com.example.taskmanagementapplication.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages persistent app-private local storage for captured work photos.
 * Ensures the Service Boy's device maintains an offline, permanent copy of
 * every photo taken during on-site execution.
 */
object LocalPhotoManager {

    private const val DIR_WORK_PHOTOS = "work_photos"

    /**
     * Returns the dedicated app-private directory for a specific work order.
     */
    fun getWorkPhotosDir(context: Context, workId: Long): File {
        val dir = File(context.filesDir, "$DIR_WORK_PHOTOS/work_$workId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves photo from a source Uri (camera or gallery) into app-private persistent storage.
     */
    fun savePhoto(context: Context, workId: Long, sourceUri: Uri, clientPhotoId: String): File? {
        return try {
            val dir = getWorkPhotosDir(context, workId)
            val sanitizedId = clientPhotoId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetFile = File(dir, "photo_${sanitizedId}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) targetFile else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves raw photo byte array into app-private persistent storage.
     */
    fun savePhotoBytes(context: Context, workId: Long, bytes: ByteArray, photoIdentifier: String): File? {
        return try {
            val dir = getWorkPhotosDir(context, workId)
            val sanitizedId = photoIdentifier.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetFile = File(dir, "photo_${sanitizedId}.jpg")

            FileOutputStream(targetFile).use { output ->
                output.write(bytes)
            }
            if (targetFile.exists() && targetFile.length() > 0) targetFile else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the local persistent photo file for a given work photo if it exists on device.
     */
    fun getLocalPhotoFile(
        context: Context,
        workId: Long,
        photoId: String? = null,
        backendId: Long? = null,
        storageReference: String? = null,
        localUri: String? = null
    ): File? {
        // 1. If localUri is already a valid absolute file path
        if (!localUri.isNullOrBlank()) {
            try {
                val fileFromUri = if (localUri.startsWith("file://")) {
                    File(Uri.parse(localUri).path ?: "")
                } else {
                    File(localUri)
                }
                if (fileFromUri.exists() && fileFromUri.length() > 0) {
                    return fileFromUri
                }
            } catch (_: Exception) {}
        }

        val dir = getWorkPhotosDir(context, workId)
        if (!dir.exists()) return null

        // 2. Check by clientPhotoId / photoId
        if (!photoId.isNullOrBlank()) {
            val sanitizedId = photoId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val candidate = File(dir, "photo_${sanitizedId}.jpg")
            if (candidate.exists() && candidate.length() > 0) return candidate
        }

        // 3. Check by backendId
        if (backendId != null) {
            val candidate = File(dir, "photo_${backendId}.jpg")
            if (candidate.exists() && candidate.length() > 0) return candidate
        }

        // 4. Check by storageReference filename
        if (!storageReference.isNullOrBlank()) {
            val fileName = storageReference.substringAfterLast("/")
            val candidate = File(dir, fileName)
            if (candidate.exists() && candidate.length() > 0) return candidate
        }

        // 5. Fallback: if only 1 photo in directory and we have a photo request
        val files = dir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()
        if (files.size == 1 && (photoId != null || backendId != null)) {
            return files.first()
        }

        return null
    }

    /**
     * Retrieves all persistent local photos for a work order.
     */
    fun getAllLocalPhotos(context: Context, workId: Long): List<File> {
        val dir = getWorkPhotosDir(context, workId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()
    }
}
