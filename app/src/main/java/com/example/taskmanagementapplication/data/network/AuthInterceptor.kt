package com.example.taskmanagementapplication.data.network

import com.example.taskmanagementapplication.BuildConfig
import com.example.taskmanagementapplication.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * OkHttp interceptor that attaches Supabase apikey and JWT Bearer token to every request.
 * Automatically refreshes the session token on HTTP 401 using the stored refresh token.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isAuthEndpoint = original.url.encodedPath.contains("auth/v1")

        val token = runBlocking { tokenManager.getToken() }

        val builder = original.newBuilder()
            .header("apikey", BuildConfig.SUPABASE_KEY)

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        } else {
            builder.header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
        }

        val request = builder.build()
        val response = chain.proceed(request)

        if (response.code == 401 && !isAuthEndpoint) {
            val refreshed = synchronized(refreshLock) {
                val currentToken = runBlocking { tokenManager.getToken() }
                if (!currentToken.isNullOrBlank() && currentToken != token) {
                    true
                } else {
                    attemptTokenRefresh(chain)
                }
            }

            if (refreshed) {
                response.close()
                val newToken = runBlocking { tokenManager.getToken() }
                val retryRequest = original.newBuilder()
                    .header("apikey", BuildConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                runBlocking {
                    tokenManager.clearToken()
                }
                com.example.taskmanagementapplication.core.network.AuthEventBus.emitSessionExpired()
            }
        }
        return response
    }

    private fun attemptTokenRefresh(chain: Interceptor.Chain): Boolean {
        val refreshToken = runBlocking { tokenManager.getRefreshToken() } ?: return false
        return try {
            val refreshUrl = chain.request().url.newBuilder()
                .encodedPath("/auth/v1/token")
                .query("grant_type=refresh_token")
                .build()

            val jsonBody = JSONObject().apply {
                put("refresh_token", refreshToken)
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .header("apikey", BuildConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val refreshResponse = chain.proceed(refreshRequest)
            if (refreshResponse.isSuccessful) {
                val responseStr = refreshResponse.body?.string()
                refreshResponse.close()
                if (!responseStr.isNullOrBlank()) {
                    val jsonObj = JSONObject(responseStr)
                    val newAccessToken = if (jsonObj.has("access_token")) jsonObj.getString("access_token") else null
                    val newRefreshToken = if (jsonObj.has("refresh_token")) jsonObj.getString("refresh_token") else null
                    if (!newAccessToken.isNullOrBlank()) {
                        runBlocking {
                            tokenManager.updateTokens(newAccessToken, newRefreshToken)
                        }
                        return true
                    }
                }
            } else {
                refreshResponse.close()
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
