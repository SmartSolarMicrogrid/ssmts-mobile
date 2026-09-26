package com.ssmts.mobile.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite (no Room/ORM) local database for user management,
 * as required by the mobile spec: prosumers register with NIC as the
 * primary key, can edit profile data and request deactivation.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                $COL_NIC          TEXT PRIMARY KEY,
                $COL_FULL_NAME    TEXT NOT NULL,
                $COL_EMAIL        TEXT NOT NULL,
                $COL_PHONE        TEXT,
                $COL_ADDRESS      TEXT,
                $COL_ROLE         TEXT NOT NULL,
                $COL_STATUS       TEXT NOT NULL DEFAULT 'Active',
                $COL_SERVER_ID    TEXT,
                $COL_LAST_LOGIN   INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_users_email ON $TABLE_USERS($COL_EMAIL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // ── User management ────────────────────────────────────────────────

    /** Insert or replace a user record (NIC primary key). */
    fun upsertUser(user: LocalUser) {
        val values = ContentValues().apply {
            put(COL_NIC, user.nic)
            put(COL_FULL_NAME, user.fullName)
            put(COL_EMAIL, user.email)
            put(COL_PHONE, user.phone)
            put(COL_ADDRESS, user.address)
            put(COL_ROLE, user.role)
            put(COL_STATUS, user.status)
            put(COL_SERVER_ID, user.serverId)
            put(COL_LAST_LOGIN, user.lastLoginAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUser(nic: String): LocalUser? =
        writableDatabase.query(
            TABLE_USERS, null, "$COL_NIC = ?", arrayOf(nic), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toUser() else null }

    fun getUserByEmail(email: String): LocalUser? =
        writableDatabase.query(
            TABLE_USERS, null, "$COL_EMAIL = ? COLLATE NOCASE", arrayOf(email), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toUser() else null }

    fun getAllUsers(): List<LocalUser> =
        writableDatabase.query(
            TABLE_USERS, null, null, null, null, null, "$COL_LAST_LOGIN DESC"
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.toUser()) }
        }

    /** Update editable profile fields for a prosumer (name, phone, address). */
    fun updateProfile(nic: String, fullName: String, phone: String?, address: String?): Int {
        val values = ContentValues().apply {
            put(COL_FULL_NAME, fullName)
            put(COL_PHONE, phone)
            put(COL_ADDRESS, address)
        }
        return writableDatabase.update(TABLE_USERS, values, "$COL_NIC = ?", arrayOf(nic))
    }

    fun updateStatus(nic: String, status: String): Int {
        val values = ContentValues().apply { put(COL_STATUS, status) }
        return writableDatabase.update(TABLE_USERS, values, "$COL_NIC = ?", arrayOf(nic))
    }

    fun touchLastLogin(nic: String) {
        val values = ContentValues().apply { put(COL_LAST_LOGIN, System.currentTimeMillis()) }
        writableDatabase.update(TABLE_USERS, values, "$COL_NIC = ?", arrayOf(nic))
    }

    fun deleteUser(nic: String): Int =
        writableDatabase.delete(TABLE_USERS, "$COL_NIC = ?", arrayOf(nic))

    private fun Cursor.toUser() = LocalUser(
        nic = getString(getColumnIndexOrThrow(COL_NIC)),
        fullName = getString(getColumnIndexOrThrow(COL_FULL_NAME)),
        email = getString(getColumnIndexOrThrow(COL_EMAIL)),
        phone = getString(getColumnIndexOrThrow(COL_PHONE)),
        address = getString(getColumnIndexOrThrow(COL_ADDRESS)),
        role = getString(getColumnIndexOrThrow(COL_ROLE)),
        status = getString(getColumnIndexOrThrow(COL_STATUS)),
        serverId = getString(getColumnIndexOrThrow(COL_SERVER_ID)),
        lastLoginAt = getLong(getColumnIndexOrThrow(COL_LAST_LOGIN))
    )

    companion object {
        private const val DB_NAME = "ssmts.db"
        private const val DB_VERSION = 1

        const val TABLE_USERS = "users"
        const val COL_NIC = "nic"
        const val COL_FULL_NAME = "full_name"
        const val COL_EMAIL = "email"
        const val COL_PHONE = "phone"
        const val COL_ADDRESS = "address"
        const val COL_ROLE = "role"
        const val COL_STATUS = "status"
        const val COL_SERVER_ID = "server_id"
        const val COL_LAST_LOGIN = "last_login_at"
    }
}
