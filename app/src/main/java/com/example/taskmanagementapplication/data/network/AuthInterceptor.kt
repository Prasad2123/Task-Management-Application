package com.example.taskmanagementapplication.data.network

import com.example.taskmanagementapplication.BuildConfig
import com.example.taskmanagementapplication.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches Supabase apikey and JWT Bearer token to every request.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

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
            runBlocking {
                tokenManager.clearToken()
            }
            com.example.taskmanagementapplication.core.network.AuthEventBus.emitSessionExpired()
        }
        return response
    }
}
