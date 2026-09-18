package com.example.taskmanagementapplication.core.model

/**
 * Authoritative Company master entity for work assignment.
 * Stores company identification, testing address, and geographic coordinates.
 */
data class Company(
    val id: Long,
    val companyName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
