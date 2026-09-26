package com.ssmts.mobile.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the authenticated session (JWT + identity) in SharedPreferences.
 * Profile data itself lives in the SQLite users table — see [DbHelper].
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        token: String,
        userId: String,
        name: String,
        email: String,
        role: String,
        expiresAtIso: String
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ROLE, role)
            .putString(KEY_EXPIRES_AT, expiresAtIso)
            .apply()
    }

    /** NIC doubles as the prosumer identity key; stored once /auth/me resolves it. */
    fun saveNic(nic: String) = prefs.edit().putString(KEY_NIC, nic).apply()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val name: String? get() = prefs.getString(KEY_NAME, null)
    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val role: String? get() = prefs.getString(KEY_ROLE, null)
    val nic: String? get() = prefs.getString(KEY_NIC, null)

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()
    val isProsumer: Boolean get() = role.equals(ROLE_PROSUMER, ignoreCase = true)
    val isOperator: Boolean get() = role.equals(ROLE_OPERATOR, ignoreCase = true)

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_NAME = "ssmts_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_NIC = "nic"
        private const val KEY_EXPIRES_AT = "expires_at"

        const val ROLE_PROSUMER = "Prosumer"
        const val ROLE_OPERATOR = "GridOperator"
    }
}
