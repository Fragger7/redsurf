package com.redsurf.tv.network

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * World-class IPTV apps must spoof User-Agents and handle aggressive HTTP redirects.
 * Many cheap IPTV panels block default ExoPlayer user-agents or require specific referers.
 */
object IptvNetworkModule {
    
    // Default to mimicking a standard browser or VLC to avoid provider 403 blocks
    var currentUserAgent = "VLC/3.0.18 LibVLC/3.0.18"

    fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", currentUserAgent)
                    .header("Accept", "*/*")
                    // Some providers block empty referers on TS files
                    .header("Referer", originalRequest.url.host) 
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .build()
    }

    // Custom DataSource.Factory for ExoPlayer
    fun getDataSourceFactory(): HttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(currentUserAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to currentUserAgent,
                    "Accept" to "*/*"
                )
            )
    }
}
