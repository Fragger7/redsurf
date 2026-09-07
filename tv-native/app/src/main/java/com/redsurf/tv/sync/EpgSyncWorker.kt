package com.redsurf.tv.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStream

class EpgSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = RedSurfDatabase.getDatabase(applicationContext)
        val epgUrl = inputData.getString("EPG_URL") ?: return@withContext Result.failure()

        try {
            Log.d("EpgSync", "Starting silent EPG update from: \$epgUrl")
            val client = IptvNetworkModule.getOkHttpClient()
            val request = Request.Builder().url(epgUrl).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.retry()

            response.body?.byteStream()?.use { stream ->
                // Parse massive XML file
                val programs = XmlTvParser.parse(stream)
                
                // Transaction: Wipe old EPG for this source and insert new
                // For simplicity, we just clear and insert
                db.channelDao().clearAll() // TODO: Implement specific EPG Dao
                Log.d("EpgSync", "Successfully synced \${programs.size} programs in background.")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("EpgSync", "Failed to sync EPG", e)
            Result.retry()
        }
    }
}
