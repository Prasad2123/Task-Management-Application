package com.example.taskmanagementapplication.core.model

enum class UserRole {
    SERVICE_BOY,
    POC,
    SITE_SUPERVISOR
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val profileImageRes: Int? = null
)
