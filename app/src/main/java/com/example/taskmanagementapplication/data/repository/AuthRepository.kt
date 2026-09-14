package com.example.taskmanagementapplication.data.repository

import com.example.taskmanagementapplication.core.model.User
import com.example.taskmanagementapplication.core.model.UserRole
import com.example.taskmanagementapplication.data.dto.LoginRequest
import com.example.taskmanagementapplication.data.local.TokenManager
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkModule
import com.example.taskmanagementapplication.data.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Supabase Authentication Repository.
 * Authenticates against Supabase Auth endpoint and retrieves public.users profile via RPC.
 */
class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): NetworkResult<User> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim()
                val response = apiService.login(LoginRequest(cleanEmail, password))
                if (response.isSuccessful) {
                    val authBody = response.body()!!
                    val accessToken = authBody.accessToken

                    // Temporarily store token so subsequent profile RPC carries Authorization header
                    tokenManager.saveToken(
                        token = accessToken,
                        userId = 1L,
                        name = "User",
                        email = cleanEmail,
                        role = "SERVICE_BOY",
                        phone = null
                    )

                    // Retrieve authoritative user profile from database via get_current_user_profile RPC
                    var resolvedId = 1L
                    var resolvedName = (authBody.user.userMetadata?.get("name") as? String) ?: "User"
                    var resolvedRole = (authBody.user.userMetadata?.get("role") as? String) ?: "SERVICE_BOY"
                    var resolvedPhone: String? = null

                    try {
                        val profileResp = apiService.getCurrentUserProfile()
                        if (profileResp.isSuccessful && profileResp.body() != null) {
                            val profile = profileResp.body()!!
                            resolvedId = profile.id
                            resolvedName = profile.name
                            resolvedRole = profile.role
                            resolvedPhone = profile.phone
                        }
                    } catch (e: Exception) {
                        // Fall back to metadata in auth token
                    }

                    // Save verified application profile into DataStore
                    tokenManager.saveToken(
                        token = accessToken,
                        userId = resolvedId,
                        name = resolvedName,
                        email = cleanEmail,
                        role = resolvedRole,
                        phone = resolvedPhone
                    )

                    val domainRole = when (resolvedRole) {
                        "POC" -> UserRole.POC
                        "SITE_SUPERVISOR", "SUPERVISOR" -> UserRole.SITE_SUPERVISOR
                        else -> UserRole.SERVICE_BOY
                    }

                    NetworkResult.Success(
                        User(
                            id = resolvedId.toString(),
                            name = resolvedName,
                            email = cleanEmail,
                            phone = resolvedPhone ?: "",
                            role = domainRole
                        )
                    )
                } else {
                    val error = NetworkModule.parseError(response)
                    val errorMsg = when (response.code()) {
                        400, 401 -> "Invalid email or password"
                        500 -> "Supabase Auth service error: " + (error.msg ?: error.message)
                        else -> error.msg ?: error.message
                    }
                    NetworkResult.Error(
                        code = response.code(),
                        message = errorMsg,
                        isAuthError = response.code() == 401 || response.code() == 400
                    )
                }
            } catch (e: IOException) {
                NetworkResult.Error(
                    message = "Unable to connect to Supabase. Please check your internet connection.",
                    isNetworkError = true
                )
            } catch (e: Exception) {
                NetworkResult.Error(message = e.message ?: "An unexpected error occurred during login")
            }
        }
    }

    suspend fun logout() {
        tokenManager.clearToken()
    }

    suspend fun isLoggedIn(): Boolean = tokenManager.getToken() != null
}
