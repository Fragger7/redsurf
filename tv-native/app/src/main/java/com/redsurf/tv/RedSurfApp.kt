package com.redsurf.tv

import android.app.Application
import com.redsurf.tv.diag.CrashLog

/** Exists for one reason: install [CrashLog] before anything else runs - including WorkManager
 * workers that start with no Activity at all, which MainActivity.onCreate would never cover. */
class RedSurfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
