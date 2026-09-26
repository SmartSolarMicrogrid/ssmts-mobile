package com.ssmts.mobile.data.local

/**
 * A user record cached in the local SQLite database.
 * NIC is the primary key for prosumers; staff (operators) use their server user id.
 */
data class LocalUser(
    val nic: String,
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val address: String? = null,
    val role: String,
    val status: String = "Active",
    val serverId: String? = null,
    val lastLoginAt: Long = 0L
)
