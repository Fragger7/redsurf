package com.redsurf.tv.vod

import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

data class XtreamCategory(val id: String, val name: String, val parentId: String)
data class VodMovie(val id: String, val name: String, val cover: String, val rating: String, val streamExtension: String)
data class VodSeries(val id: String, val name: String, val cover: String, val rating: String)

/**
 * TiViMate Parity: True VOD and Series support.
 * M3U parsing is for Live TV. VOD requires hitting the actual Xtream Codes JSON API.
 */
object XtreamApi {
    
    // Feature 2: Fetching Categories (Live, VOD, Series)
    suspend fun getCategories(serverUrl: String, user: String, pass: String, type: String, userAgent: String? = null): List<XtreamCategory> = withContext(Dispatchers.IO) {
        val action = when (type) {
            "live" -> "get_live_categories"
            "vod" -> "get_vod_categories"
            "series" -> "get_series_categories"
            else -> "get_vod_categories"
        }
        val url = "$serverUrl/player_api.php?username=$user&password=$pass&action=$action"
        
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
        
        val categories = mutableListOf<XtreamCategory>()
        if (response.isSuccessful) {
            val bodyStr = response.body?.string()
            if (!bodyStr.isNullOrBlank() && bodyStr.startsWith("[")) {
                val jsonArray = JSONArray(bodyStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    categories.add(
                        XtreamCategory(
                            id = obj.optString("category_id"),
                            name = obj.optString("category_name"),
                            parentId = obj.optString("parent_id", "0")
                        )
                    )
                }
            }
        }
        categories
    }

    suspend fun getMovies(serverUrl: String, user: String, pass: String, categoryId: String? = null, userAgent: String? = null): List<VodMovie> = withContext(Dispatchers.IO) {
        val action = if (categoryId == null) "get_vod_streams" else "get_vod_streams&category_id=$categoryId"
        val url = "$serverUrl/player_api.php?username=$user&password=$pass&action=$action"
        
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
        
        val movies = mutableListOf<VodMovie>()
        if (response.isSuccessful) {
            val bodyStr = response.body?.string()
            if (!bodyStr.isNullOrBlank() && bodyStr.startsWith("[")) {
                val jsonArray = JSONArray(bodyStr)
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
        }
        movies
    }

    suspend fun getSeries(serverUrl: String, user: String, pass: String, categoryId: String? = null, userAgent: String? = null): List<VodSeries> = withContext(Dispatchers.IO) {
        val action = if (categoryId == null) "get_series" else "get_series&category_id=$categoryId"
        val url = "$serverUrl/player_api.php?username=$user&password=$pass&action=$action"
        
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
        
        val series = mutableListOf<VodSeries>()
        if (response.isSuccessful) {
            val bodyStr = response.body?.string()
            if (!bodyStr.isNullOrBlank() && bodyStr.startsWith("[")) {
                val jsonArray = JSONArray(bodyStr)
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
        }
        series
    }
}
