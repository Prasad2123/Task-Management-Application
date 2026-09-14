package com.example.taskmanagementapplication.data.network

/**
 * Sealed class wrapping API results.
 * Used throughout the repository and ViewModel layers.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(
        val code: Int = 0,
        val message: String,
        val isNetworkError: Boolean = false,
        val isAuthError: Boolean = false
    ) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
