package com.example.data

import java.util.Date

/**
 * User Model representing the "users" collection schema in Cloud Firestore.
 */
data class FirestoreUser(
    val username: String = "",
    val password: String = "",
    val fullName: String = "",
    val role: String = "Viewer",
    val firmAccess: String = "",
    val isActive: Boolean = true,
    val lastLogin: Date? = null,
    val createdAt: Date? = null
)
