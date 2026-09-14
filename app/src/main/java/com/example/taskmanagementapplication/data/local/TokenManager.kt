package com.example.taskmanagementapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Manages JWT token and user session info using DataStore.
 * Never stores the password or password hash.
 */
class TokenManager(private val context: Context) {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_USER_ID = longPreferencesKey("user_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
    }

    suspend fun saveToken(
        token: String,
        userId: Long,
        name: String,
        email: String,
        role: String,
        phone: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = token
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_ROLE] = role
            phone?.let { prefs[KEY_USER_PHONE] = it }
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[KEY_ACCESS_TOKEN]
    }

    suspend fun getUserId(): Long? {
        return context.dataStore.data.first()[KEY_USER_ID]
    }

    suspend fun getUserRole(): String? {
        return context.dataStore.data.first()[KEY_USER_ROLE]
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val userIdFlow: Flow<Long?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ROLE] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val userPhoneFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_PHONE] }
}
