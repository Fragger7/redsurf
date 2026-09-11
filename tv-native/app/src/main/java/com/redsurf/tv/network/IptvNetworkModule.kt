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
 *
 * Anti-bot handling per docs/vision/IPTV_DOMAIN_KNOWLEDGE.md #2 and #4 (see
 * docs/plans/PHASE_1.md #2c) - measured against the user's real provider, which throttled a
 * VLC-identified request for 38s before responding.
 */
object IptvNetworkModule {

    // Whitelisted legacy player agent (IPTV_DOMAIN_KNOWLEDGE.md #2). VLC's identifier still drew
    // a 38s throttle from this provider; this one didn't.
    var globalUserAgent = "IPTVSmartersPro/1.1.1"

    /** Default read timeout for ordinary requests (update checks, etc). */
    private const val DEFAULT_READ_TIMEOUT_SECONDS = 15L

    /**
     * Playlist and API fetches (M3U downloads, Xtream player_api.php) can legitimately sit
     * silent for tens of seconds against a throttling provider - IPTV_DOMAIN_KNOWLEDGE.md #4's
     * bounded-timeout guidance, sized to what was actually observed (38s), not the default.
     */
    const val PLAYLIST_READ_TIMEOUT_SECONDS = 90L
    
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
    fun getOkHttpClient(
        playlistUserAgent: String? = null,
        readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
    ): OkHttpClient {
        val bootstrapClient = OkHttpClient.Builder().build()
        val dns = buildDoHDns(bootstrapClient, currentDnsProvider)

        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent

        return OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", finalUserAgent)
                    .header("Accept", "*/*")
                    // No Referer: a non-standard header no real player sends, and a giveaway
                    // that the request is programmatic (IPTV_DOMAIN_KNOWLEDGE.md #2).
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .build()
    }

    /** For playlist/API fetches specifically - see [PLAYLIST_READ_TIMEOUT_SECONDS]. */
    fun getPlaylistOkHttpClient(playlistUserAgent: String? = null): OkHttpClient =
        getOkHttpClient(playlistUserAgent, PLAYLIST_READ_TIMEOUT_SECONDS)

    // Custom DataSource.Factory for ExoPlayer
    fun getDataSourceFactory(playlistUserAgent: String? = null): HttpDataSource.Factory {
        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent
        // Use OkHttpDataSource so ExoPlayer uses our DoH enabled client
        return OkHttpDataSource.Factory(getOkHttpClient(playlistUserAgent))
            .setUserAgent(finalUserAgent)
    }
}
