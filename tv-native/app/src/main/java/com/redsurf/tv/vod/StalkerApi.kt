package com.redsurf.tv.vod

import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Feature 5: Stalker type IPTV portal support
 * Stalker portals use MAC address authentication instead of standard usernames/passwords.
 */
object StalkerApi {

    // Helper to generate Stalker API headers
    private fun getStalkerRequest(url: String, macAddress: String, userAgent: String? = null): Request {
        val finalUserAgent = userAgent?.takeIf { it.isNotBlank() } ?: "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        return Request.Builder()
            .url(url)
            .header("User-Agent", finalUserAgent)
            .header("Cookie", "mac=$macAddress")
            .header("Authorization", "Bearer $macAddress") // Sometimes needed
            .header("X-User-Agent", "Model: MAG250; Link:")
            .build()
    }

    suspend fun getHandshake(portalUrl: String, macAddress: String, userAgent: String? = null): Boolean = withContext(Dispatchers.IO) {
        val url = "$portalUrl/server/load.php?type=stb&action=handshake"
        val request = getStalkerRequest(url, macAddress, userAgent)
        try {
            val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getProfile(portalUrl: String, macAddress: String, userAgent: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        val url = "$portalUrl/server/load.php?type=stb&action=get_profile"
        val request = getStalkerRequest(url, macAddress, userAgent)
        try {
            val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    return@withContext JSONObject(bodyStr).optJSONObject("js")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCategories(portalUrl: String, macAddress: String, type: String, userAgent: String? = null): List<XtreamCategory> = withContext(Dispatchers.IO) {
        // action=get_categories&type=itv (live), action=get_categories&type=vod
        val actionType = if (type == "live") "itv" else "vod"
        val url = "$portalUrl/server/load.php?type=$actionType&action=get_categories"
        val request = getStalkerRequest(url, macAddress, userAgent)
        
        val categories = mutableListOf<XtreamCategory>()
        try {
            val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val root = JSONObject(bodyStr)
                    val jsArray = root.optJSONArray("js")
                    if (jsArray != null) {
                        for (i in 0 until jsArray.length()) {
                            val obj = jsArray.getJSONObject(i)
                            categories.add(
                                XtreamCategory(
                                    id = obj.optString("id"),
                                    name = obj.optString("title"),
                                    parentId = "0"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        categories
    }

    suspend fun createLink(portalUrl: String, macAddress: String, cmd: String, userAgent: String? = null): String? = withContext(Dispatchers.IO) {
        // action=create_link&type=itv&cmd=$cmd
        val url = "$portalUrl/server/load.php?type=itv&action=create_link&cmd=$cmd"
        val request = getStalkerRequest(url, macAddress, userAgent)
        
        try {
            val response = IptvNetworkModule.getOkHttpClient(userAgent).newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val root = JSONObject(bodyStr)
                    return@withContext root.optJSONObject("js")?.optString("cmd")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
