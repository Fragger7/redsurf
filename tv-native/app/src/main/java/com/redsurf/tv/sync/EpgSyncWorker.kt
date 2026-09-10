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

class EpgSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = RedSurfDatabase.getDatabase(applicationContext)
        val epgUrl = inputData.getString("EPG_URL") ?: return@withContext Result.failure()

        try {
            Log.d("EpgSyncWorker", "Starting silent EPG update from: $epgUrl")
            val client = IptvNetworkModule.getOkHttpClient()
            val request = Request.Builder().url(epgUrl).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.retry()

            response.body?.byteStream()?.use { stream ->
                // Transaction: Wipe old EPG completely before streaming in new data
                db.epgDao().clearAll()
                
                // Stream parse and batch insert directly to disk
                XmlTvParser.parseAndInsert(stream, db.epgDao())
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("EpgSyncWorker", "Failed to sync EPG", e)
            Result.retry()
        }
    }
}
