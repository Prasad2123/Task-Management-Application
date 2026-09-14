package com.example.taskmanagementapplication.data.network

import com.example.taskmanagementapplication.BuildConfig
import com.example.taskmanagementapplication.data.dto.ApiErrorDto
import com.example.taskmanagementapplication.data.local.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Creates and provides the Retrofit + OkHttp network stack.
 * BASE_URL is sourced from BuildConfig (set per build variant).
 */
object NetworkModule {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createImageLoader(context: android.content.Context, tokenManager: TokenManager): coil.ImageLoader {
        return coil.ImageLoader.Builder(context)
            .okHttpClient(createOkHttpClient(tokenManager))
            .crossfade(true)
            .build()
    }

    fun createApiService(tokenManager: TokenManager, context: android.content.Context? = null): ApiService {
        val okHttpClient = createOkHttpClient(tokenManager)
        val baseUrl = com.example.taskmanagementapplication.core.network.ServerConfig.getBaseUrl(context)

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(ApiService::class.java)
    }

    /**
     * Parse an API error response body into ApiErrorDto.
     */
    fun <T> parseError(response: Response<T>): ApiErrorDto {
        return try {
            val adapter = moshi.adapter(ApiErrorDto::class.java)
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                adapter.fromJson(errorBody) ?: ApiErrorDto(
                    status = response.code(),
                    message = response.message() ?: "Unknown error"
                )
            } else {
                ApiErrorDto(status = response.code(), message = response.message() ?: "Unknown error")
            }
        } catch (e: Exception) {
            ApiErrorDto(status = response.code(), message = "Server error")
        }
    }

    /**
     * Convert a Retrofit Response to NetworkResult.
     */
    fun <T> toNetworkResult(response: Response<T>): NetworkResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.Success(Unit as T)
            }
        } else {
            val errorDto = parseError(response)
            NetworkResult.Error(
                code = response.code(),
                message = errorDto.message,
                isAuthError = response.code() == 401 || response.code() == 403
            )
        }
    }
}
