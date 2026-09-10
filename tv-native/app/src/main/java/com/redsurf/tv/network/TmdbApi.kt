package com.redsurf.tv.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object TmdbApi {
    private val client = OkHttpClient()

    // Real API call to TMDB
    fun fetchPoster(query: String, apiKey: String): String? {
        if (apiKey.isEmpty()) return null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: "")
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val path = results.getJSONObject(0).optString("poster_path", "")
                    if (path.isNotEmpty()) "https://image.tmdb.org/t/p/w500$path" else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
