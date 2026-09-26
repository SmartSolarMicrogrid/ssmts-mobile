package com.ssmts.mobile

import android.app.Application
import com.ssmts.mobile.data.local.DbHelper
import com.ssmts.mobile.data.local.SessionManager

/**
 * Application entry point — exposes app-wide singletons for the
 * local SQLite database and the auth session.
 */
class SsmtsApp : Application() {

    lateinit var db: DbHelper
        private set

    lateinit var session: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        db = DbHelper(this)
        session = SessionManager(this)
    }

    companion object {
        fun from(context: android.content.Context): SsmtsApp =
            context.applicationContext as SsmtsApp
    }
}
