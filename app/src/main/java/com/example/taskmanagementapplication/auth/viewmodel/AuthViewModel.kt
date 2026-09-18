package com.example.taskmanagementapplication.auth.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanagementapplication.core.model.User
import com.example.taskmanagementapplication.core.model.UserRole
import com.example.taskmanagementapplication.data.local.TokenManager
import com.example.taskmanagementapplication.data.network.NetworkModule
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application.applicationContext)
    private val apiService = NetworkModule.createApiService(tokenManager, application.applicationContext)
    private val authRepository = AuthRepository(apiService, tokenManager)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Please enter email and password.")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            when (val result = authRepository.login(email, password)) {
                is NetworkResult.Success -> {
                    _currentUser.value = result.data
                    _loginState.value = LoginState.Success(result.data)
                }
                is NetworkResult.Error -> {
                    val message = when {
                        result.isNetworkError -> "Unable to connect to server. Please check your connection."
                        result.code == 401 -> "Invalid email or password."
                        result.code == 403 -> "Your account has been deactivated."
                        else -> result.message.ifBlank { "Login failed. Please try again." }
                    }
                    _loginState.value = LoginState.Error(message)
                }
                is NetworkResult.Loading -> { /* handled above */ }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _currentUser.value = null
            _loginState.value = LoginState.Idle
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    suspend fun checkAndRestoreSession(): User? {
        val restoredUser = authRepository.restoreSession()
        if (restoredUser != null) {
            _currentUser.value = restoredUser
            _loginState.value = LoginState.Success(restoredUser)
        }
        return restoredUser
    }

    /**
     * Creates a User from persisted token data (for auto-login restore).
     * Used to restore session without re-login.
     */
    fun restoreSessionIfNeeded() {
        viewModelScope.launch {
            checkAndRestoreSession()
        }
    }
}
