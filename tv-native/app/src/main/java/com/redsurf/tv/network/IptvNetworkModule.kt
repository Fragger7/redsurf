package com.redsurf.tv.network

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
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

    /**
     * PLAYER_ENGINEERING_BRIEF.md §4.5/§7 - the live-stream media path, deliberately tighter than
     * the default. A live stream that's gone this long without a byte is dead; before this, the
     * player's data source inherited the 15s default, so the UI sat frozen with no feedback for
     * 15 whole seconds before the stall watchdog even got a chance to act. This is
     * IPTV_DOMAIN_KNOWLEDGE.md §4's fast-fail hedging applied to the media path, where it had
     * never actually been applied.
     */
    private const val MEDIA_CONNECT_TIMEOUT_SECONDS = 5L
    private const val MEDIA_READ_TIMEOUT_SECONDS = 8L

    enum class DnsProvider {
        SYSTEM, CLOUDFLARE, GOOGLE
    }

    var currentDnsProvider = DnsProvider.SYSTEM
        set(value) {
            if (field != value) {
                field = value
                baseClient = null // force a rebuild against the new DNS provider
            }
        }

    /**
     * §6/§7: one lazily-created client every other client variant derives from via
     * `newBuilder()`, which shares the connection pool, dispatcher, and TLS session cache -
     * `getOkHttpClient()` used to build a brand-new client (plus a throwaway bootstrap client) on
     * every single call, which meant every playlist fetch, every update check, and every player
     * construction paid for a fresh connection pool and fresh TLS handshakes. Holds no
     * interceptors or timeouts of its own - those vary per caller (User-Agent, read timeout) and
     * are layered on by each `newBuilder()` derivation below, same as before.
     */
    private var baseClient: OkHttpClient? = null

    private fun getBaseClient(): OkHttpClient =
        baseClient ?: OkHttpClient.Builder()
            .dns(buildDoHDns(OkHttpClient.Builder().build(), currentDnsProvider))
            .build()
            .also { baseClient = it }

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

    // Pass custom User-Agent from Playlist (Feature 1). Derives from the shared [getBaseClient]
    // via `newBuilder()` (§6/§7) - this keeps its own connect/read timeouts and UA-injecting
    // interceptor (both genuinely per-caller), while still sharing the base client's connection
    // pool, dispatcher, and TLS session cache instead of building all three fresh every call.
    fun getOkHttpClient(
        playlistUserAgent: String? = null,
        readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
        connectTimeoutSeconds: Long = 15L,
    ): OkHttpClient {
        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent

        return getBaseClient().newBuilder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
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

    /** PLAYER_ENGINEERING_BRIEF.md §4.5/§7 - the live-stream media client specifically, with the
     * tighter fail-fast timeouts a dead live stream deserves (see [MEDIA_CONNECT_TIMEOUT_SECONDS]/
     * [MEDIA_READ_TIMEOUT_SECONDS]'s own doc comment). */
    private fun getMediaOkHttpClient(playlistUserAgent: String? = null): OkHttpClient =
        getOkHttpClient(playlistUserAgent, MEDIA_READ_TIMEOUT_SECONDS, MEDIA_CONNECT_TIMEOUT_SECONDS)

    /** Custom DataSource.Factory for ExoPlayer - the player's actual stream requests, not API
     * calls. Uses [getMediaOkHttpClient], not the general-purpose client, and [playlistUserAgent]
     * is threaded all the way from the playing channel's own playlist (§6/§9) - previously this
     * was called with no argument from the player, so a playlist configured with a custom
     * User-Agent got the *global* one for its actual stream requests while its API requests got
     * the right one, a 403 waiting to happen on exactly the providers a custom UA was added for.
     * [transferListener], when given, gets every raw `onBytesTransferred` call for this stream's
     * connection - see `PlayerController`'s own doc comment on why it needs this instead of
     * `DefaultBandwidthMeter`'s built-in estimate. */
    fun getDataSourceFactory(playlistUserAgent: String? = null, transferListener: TransferListener? = null): HttpDataSource.Factory {
        val finalUserAgent = playlistUserAgent?.takeIf { it.isNotBlank() } ?: globalUserAgent
        // Use OkHttpDataSource so ExoPlayer uses our DoH enabled client
        val factory = OkHttpDataSource.Factory(getMediaOkHttpClient(playlistUserAgent))
            .setUserAgent(finalUserAgent)
        if (transferListener != null) factory.setTransferListener(transferListener)
        return factory
    }
}
