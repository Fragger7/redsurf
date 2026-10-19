package com.redsurf.tv.diag

import android.content.Context
import com.redsurf.tv.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent crash log (PERFORMANCE_SALVAGE.md, 2026-10-19). The v0.41.1 foreground crash was
 * confirmed by `dumpsys activity exit-info` but its stack trace had already rotated out of the
 * device's crash buffer - nothing in the app kept one. Every uncaught exception, on any thread,
 * is appended here before the default handler kills the process. Read it from the Mac with
 * `adb shell cat /sdcard/Android/data/com.redsurf.tv/files/crash.log` (no `run-as` needed, the
 * release build isn't debuggable).
 */
object CrashLog {
    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 64 * 1024L

    fun install(context: Context) {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // Bounded: when it outgrows the cap, start over - the newest crash is the one
                // that matters, and this must never eat the device's storage.
                if (file.length() > MAX_BYTES) file.delete()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                file.appendText(
                    "=== $stamp ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) thread=${thread.name}\n" +
                        error.stackTraceToString() + "\n"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
