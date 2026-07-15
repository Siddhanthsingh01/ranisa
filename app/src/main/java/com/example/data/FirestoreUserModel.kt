package com.example.data

import java.util.Date

/**
 * User Model representing the "users" collection schema in Cloud Firestore.
 */
data class FirestoreUser(
    val fullName: String = "",
    val email: String = "",
    val active: Boolean = true,
    val assignedFirms: List<String> = emptyList(),
    val createdAt: Date? = null,
    val lastLogin: Date? = null
) {
    val firmAccess: String
        get() = assignedFirms.joinToString(", ")
}
