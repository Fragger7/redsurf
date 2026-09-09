package com.redsurf.tv.network

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * World-class IPTV apps must spoof User-Agents and handle aggressive HTTP redirects.
 * Adds DNS-over-HTTPS (DoH) support to bypass ISP level DNS blocking of IPTV panels.
 */
object IptvNetworkModule {
    
    var globalUserAgent = "VLC/3.0.18 LibVLC/3.0.18"
    
    enum class DnsProvider {
        SYSTEM, CLOUDFLARE, GOOGLE
    }
    var currentDnsProvider = DnsProvider.SYSTEM

    private fun buildDoHDns(bootstrapClient: OkHttpClient, provider: DnsProvider): Dns {
        if (provider == DnsProvider.SYSTEM) return Dns.SYSTEM
        
        val url = when (provider) {
            DnsProvider.CLOUDFLARE -> "https://cloudflare-dns.com/dns-query"
            DnsProvider.GOOGLE -> "https://dns.google/dns-query"
            else -> "https://cloudflare-dns.com/dns-query"
        }.toHttpUrl()

        // Bootstrap DNS to resolve the DoH provider itself
        val bootstrapDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return when (hostname) {
                    "cloudflare-dns.com" -> listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                    "dns.google" -> listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                    else -> Dns.SYSTEM.lookup(hostname)
                }
            }
        }
        val dohClient = bootstrapClient.newBuilder().dns(bootstrapDns).build()
        
        return DnsOverHttps.Builder().client(dohClient).url(url).build()
    }

    // Pass custom User-Agent from Playlist (Feature 1)
    fun getOkHttpClient(playlistUserAgent: String? = null): OkHttpClient {
        val bootstrapClient = OkHttpClient.Builder().build()
        val dns = buildDoHDns(bootstrapClient, currentDnsProvider)
        
        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent

        return OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", finalUserAgent)
                    .header("Accept", "*/*")
                    .header("Referer", originalRequest.url.host) 
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .build()
    }

    // Custom DataSource.Factory for ExoPlayer
    fun getDataSourceFactory(playlistUserAgent: String? = null): HttpDataSource.Factory {
        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent
        // Use OkHttpDataSource so ExoPlayer uses our DoH enabled client
        return OkHttpDataSource.Factory(getOkHttpClient(playlistUserAgent))
            .setUserAgent(finalUserAgent)
    }
}
