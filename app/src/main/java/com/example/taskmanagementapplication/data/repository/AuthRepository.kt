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
                    val refreshToken = authBody.refreshToken

                    // Save token temporarily so subsequent profile RPC carries Authorization header
                    tokenManager.updateTokens(accessToken, refreshToken)

                    // Retrieve authoritative user profile from database via get_current_user_profile RPC
                    val profileResp = try {
                        apiService.getCurrentUserProfile()
                    } catch (e: Exception) {
                        tokenManager.clearToken()
                        return@withContext NetworkResult.Error(
                            message = "Failed to retrieve user profile from database: ${e.message}"
                        )
                    }

                    if (!profileResp.isSuccessful || profileResp.body() == null) {
                        tokenManager.clearToken()
                        return@withContext NetworkResult.Error(
                            message = "User profile not found in database. Please ensure your account has been provisioned."
                        )
                    }

                    val profile = profileResp.body()!!
                    val resolvedRole = profile.role.trim().uppercase()

                    val domainRole = when (resolvedRole) {
                        "SERVICE_BOY" -> UserRole.SERVICE_BOY
                        "POC" -> UserRole.POC
                        "SITE_SUPERVISOR", "SUPERVISOR" -> UserRole.SITE_SUPERVISOR
                        "ADMIN" -> UserRole.ADMIN
                        else -> {
                            tokenManager.clearToken()
                            return@withContext NetworkResult.Error(
                                message = "Unrecognized user role in profile: ${profile.role}"
                            )
                        }
                    }

                    // Save verified application profile into DataStore
                    tokenManager.saveSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        userId = profile.id,
                        name = profile.name,
                        email = cleanEmail,
                        role = profile.role,
                        phone = profile.phone
                    )

                    NetworkResult.Success(
                        User(
                            id = profile.id.toString(),
                            name = profile.name,
                            email = cleanEmail,
                            phone = profile.phone ?: "",
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
