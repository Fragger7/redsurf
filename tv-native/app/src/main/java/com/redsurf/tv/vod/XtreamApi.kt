package com.redsurf.tv.vod

import android.util.JsonReader
import android.util.JsonToken
import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader

data class XtreamCategory(val id: String, val name: String, val parentId: String)
data class VodMovie(val id: String, val name: String, val cover: String, val rating: String, val streamExtension: String)
data class VodSeries(val id: String, val name: String, val cover: String, val rating: String)

/** Decision 9's Video info screen (redesigned 2026-09-17, user request: "how about can you...
 * show something like 1/1 active connections"). Both values come straight from the panel's own
 * account object - no scraping, no guessing, and no new credential storage: every Xtream-imported
 * channel's `streamId` already IS its full playback URL (`.../live/user/pass/id.ts`,
 * `MainViewModel.loadXtreamCodes`), so the caller parses server/user/pass back out of whichever
 * channel is currently playing instead of this app persisting the password a second time. */
data class XtreamUserInfo(val activeConnections: Int?, val maxConnections: Int?)

/** One live channel from Xtream's get_live_streams, with its category name already resolved. */
data class XtreamLiveStream(
    val streamId: String,
    val name: String,
    val streamIcon: String,
    val groupName: String,
    val epgChannelId: String,
    val num: Int,
)

/**
 * TiViMate Parity: True VOD and Series support.
 * M3U parsing is for Live TV. VOD requires hitting the actual Xtream Codes JSON API.
 */
object XtreamApi {

    /** Every Xtream-imported channel's `streamId` already IS its full playback URL
     * (`.../live/user/pass/id.ts`, see [XtreamUserInfo]'s own doc comment) - this pulls
     * server/user/pass back out of it. Shared by the Connections badge (`PlayerScreen.kt`) and
     * `PHASE_3.md`'s per-playlist EPG sync (`EpgSyncWorker.kt`), rather than each re-deriving
     * credentials its own way. */
    fun parseXtreamCredentials(streamUrl: String): Triple<String, String, String>? {
        val match = Regex("^(https?://[^/]+)/live/([^/]+)/([^/]+)/").find(streamUrl) ?: return null
        val (server, user, pass) = match.destructured
        return Triple(server, user, pass)
    }

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

    /** `player_api.php` with no `action` returns the account's own `user_info` object - every
     * real Xtream panel supports this (it's the same call VLC/TiviMate use to show "connections
     * used"). Null on any failure (network, non-JSON body, missing object) - the caller omits the
     * badge entirely rather than showing a placeholder, same convention as [com.redsurf.tv.player.StreamInfo]. */
    suspend fun getUserInfo(serverUrl: String, user: String, pass: String, userAgent: String? = null): XtreamUserInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$serverUrl/player_api.php?username=$user&password=$pass"
            val request = Request.Builder().url(url).build()
            val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val userInfo = JSONObject(bodyStr).optJSONObject("user_info") ?: return@withContext null
            XtreamUserInfo(
                activeConnections = userInfo.optString("active_cons").toIntOrNull(),
                maxConnections = userInfo.optString("max_connections").toIntOrNull(),
            )
        }.getOrNull()
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

    /**
     * Streams get_live_streams with android.util.JsonReader rather than materializing the
     * response (PHASE_1.md #2c - the user's real provider returns ~28K objects here; the M3U
     * equivalent is 327 MB and 1.23M entries, which is why Xtream providers use this API instead
     * of the M3U path at all). Invokes [onBatch] every [batchSize] streams, never holding more
     * than one batch in memory.
     *
     * android.util.JsonReader.nextString() is deliberately used for every field, including
     * numeric-looking ones (stream_id, category_id): it documents lenient coercion of NUMBER
     * tokens to their string form, which matters here because different Xtream panels send these
     * as either JSON strings or JSON numbers.
     */
    suspend fun getLiveStreams(
        serverUrl: String,
        user: String,
        pass: String,
        userAgent: String? = null,
        batchSize: Int = 500,
        onBatch: suspend (List<XtreamLiveStream>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val categories = getCategories(serverUrl, user, pass, "live", userAgent)
            .associate { it.id to it.name }

        val client = IptvNetworkModule.getPlaylistOkHttpClient(userAgent)
        val url = "$serverUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Xtream get_live_streams failed: HTTP ${response.code}")
        }
        val body = response.body ?: throw IOException("Xtream get_live_streams: empty response")

        body.byteStream().use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                val batch = mutableListOf<XtreamLiveStream>()
                // Per-group, not global - same class of bug as M3uParser/MainViewModel's
                // liveIndex (found live, 2026-09-12): only used as a fallback when the provider's
                // own "num" is missing/unparseable, but a single running counter would still
                // number that channel by its position across every category, not within its own,
                // which is what next/prev-in-group's query actually sorts by.
                val groupFallbackCounters = mutableMapOf<String, Int>()

                while (reader.hasNext()) {
                    reader.beginObject()
                    var streamId = ""
                    var name = ""
                    var streamIcon = ""
                    var categoryId = ""
                    var epgChannelId = ""
                    var num: Int? = null

                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id" -> streamId = reader.nextFlexibleString()
                            "name" -> name = reader.nextFlexibleString()
                            "stream_icon" -> streamIcon = reader.nextFlexibleString()
                            "category_id" -> categoryId = reader.nextFlexibleString()
                            "epg_channel_id" -> epgChannelId = reader.nextFlexibleString()
                            "num" -> num = reader.nextFlexibleString().toIntOrNull()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (streamId.isNotEmpty()) {
                        val groupName = categories[categoryId] ?: "Uncategorized"
                        val fallbackNum = groupFallbackCounters.getOrDefault(groupName, 0)
                        groupFallbackCounters[groupName] = fallbackNum + 1
                        batch.add(
                            XtreamLiveStream(
                                streamId = streamId,
                                name = name.ifEmpty { "Unknown Channel" },
                                streamIcon = streamIcon,
                                groupName = groupName,
                                epgChannelId = epgChannelId,
                                num = num ?: fallbackNum,
                            )
                        )
                    }

                    if (batch.size >= batchSize) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    onBatch(batch.toList())
                }
                reader.endArray()
            }
        }
    }
}

/** Reads the next value as a string, coercing NULL to "" - see [XtreamApi.getLiveStreams]. */
private fun JsonReader.nextFlexibleString(): String =
    if (peek() == JsonToken.NULL) {
        nextNull()
        ""
    } else {
        nextString()
    }
