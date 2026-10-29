package com.redsurf.tv.sports

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Adapts an [OkHttpClient] to the `(url) -> body?` function [EspnScheduleFetcher] takes. Returns
 * the body on a 2xx response, or null on any other status or an [IOException].
 *
 * Blocking: call it (and so the fetcher) off the main thread. Callers get a client from
 * `IptvNetworkModule.getOkHttpClient()`.
 */
fun espnFetch(client: OkHttpClient): (String) -> String? = { url ->
    try {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (_: IOException) {
        null
    }
}
