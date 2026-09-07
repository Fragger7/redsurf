package com.redsurf.tv.vod

import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class VodMovie(val id: String, val name: String, val cover: String, val rating: String, val streamExtension: String)
data class VodSeries(val id: String, val name: String, val cover: String, val rating: String)

/**
 * TiViMate Parity: True VOD and Series support.
 * M3U parsing is for Live TV. VOD requires hitting the actual Xtream Codes JSON API.
 */
object XtreamApi {
    
    suspend fun getMovies(serverUrl: String, user: String, pass: String, categoryId: String? = null): List<VodMovie> = withContext(Dispatchers.IO) {
        val action = if (categoryId == null) "get_vod_streams" else "get_vod_streams&category_id=\$categoryId"
        val url = "\$serverUrl/player_api.php?username=\$user&password=\$pass&action=\$action"
        
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient().newCall(request).execute()
        
        val movies = mutableListOf<VodMovie>()
        if (response.isSuccessful) {
            val jsonArray = JSONArray(response.body?.string() ?: "[]")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                movies.add(
                    VodMovie(
                        id = obj.optString("stream_id"),
                        name = obj.optString("name"),
                        cover = obj.optString("stream_icon"),
                        rating = obj.optString("rating", "0.0"),
                        streamExtension = obj.optString("container_extension", "mp4")
                    )
                )
            }
        }
        movies
    }

    suspend fun getSeries(serverUrl: String, user: String, pass: String): List<VodSeries> = withContext(Dispatchers.IO) {
        val url = "\$serverUrl/player_api.php?username=\$user&password=\$pass&action=get_series"
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient().newCall(request).execute()
        
        val series = mutableListOf<VodSeries>()
        if (response.isSuccessful) {
            val jsonArray = JSONArray(response.body?.string() ?: "[]")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                series.add(
                    VodSeries(
                        id = obj.optString("series_id"),
                        name = obj.optString("name"),
                        cover = obj.optString("cover"),
                        rating = obj.optString("rating", "0.0")
                    )
                )
            }
        }
        series
    }
}
