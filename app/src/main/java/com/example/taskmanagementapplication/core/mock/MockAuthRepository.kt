package com.example.taskmanagementapplication.core.mock

import com.example.taskmanagementapplication.core.model.User
import com.example.taskmanagementapplication.core.model.UserRole

object MockAuthRepository {

    private val users = listOf(
        User(
            id = "1",
            name = "Rahul Patil",
            email = "service@demo.com",
            phone = "+91 98765 43210",
            role = UserRole.SERVICE_BOY
        ),
        User(
            id = "2",
            name = "Amit Sharma",
            email = "poc@demo.com",
            phone = "+91 87654 32109",
            role = UserRole.POC
        ),
        User(
            id = "3",
            name = "Suresh Patil",
            email = "supervisor@demo.com",
            phone = "+91 76543 21098",
            role = UserRole.SITE_SUPERVISOR
        )
    )

    /**
     * Simulate a login check.
     * Returns the matched User or null if credentials are invalid.
     */
    fun login(email: String, password: String): User? {
        if (password != "password") return null
        return users.find { it.email.equals(email.trim(), ignoreCase = true) }
    }
}
