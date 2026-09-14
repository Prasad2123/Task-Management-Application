package com.example.taskmanagementapplication.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "photo.jpg"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val displayName = it.getString(nameIndex)
                        if (!displayName.isNullOrBlank()) {
                            name = displayName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to last path segment or default
            uri.lastPathSegment?.let { name = it }
        }
        return name
    }

    fun getMimeType(context: Context, uri: Uri): String {
        val type = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        return type ?: when {
            uri.toString().endsWith(".png", true) -> "image/png"
            uri.toString().endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    fun createMultipartPart(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val mimeType = getMimeType(context, uri)
            val fileName = getFileName(context, uri)
            val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(partName, fileName, requestFile)
        } catch (e: Exception) {
            null
        }
    }

    fun createTextRequestBody(value: String): RequestBody {
        return value.toRequestBody("text/plain".toMediaTypeOrNull())
    }
}
