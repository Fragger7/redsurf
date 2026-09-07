package com.redsurf.tv.backup

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class BackupManager(private val context: Context) {

    fun backupDatabase(): Boolean {
        return try {
            val dbFile = context.getDatabasePath("redsurf_tv_database")
            val backupDir = context.getExternalFilesDir(null)
            if (backupDir != null && !backupDir.exists()) {
                backupDir.mkdirs()
            }
            val backupFile = File(backupDir, "redsurf_backup.db")
            
            if (dbFile.exists()) {
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("BackupManager", "Database backed up successfully to \${backupFile.absolutePath}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Backup failed", e)
            false
        }
    }

    fun restoreDatabase(): Boolean {
        return try {
            val dbFile = context.getDatabasePath("redsurf_tv_database")
            val backupFile = File(context.getExternalFilesDir(null), "redsurf_backup.db")
            
            if (backupFile.exists()) {
                FileInputStream(backupFile).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("BackupManager", "Database restored successfully")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Restore failed", e)
            false
        }
    }
}
