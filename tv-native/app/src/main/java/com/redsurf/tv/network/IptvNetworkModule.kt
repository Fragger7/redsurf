package com.redsurf.tv.network

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress

object IptvNetworkModule {

    private fun buildDoHClient(context: Context, useDoH: Boolean = true, provider: String = "Cloudflare"): OkHttpClient {
        val appCache = okhttp3.Cache(File(context.cacheDir, "okhttpcache"), 10 * 1024 * 1024)
        
        val bootstrapClient = OkHttpClient.Builder()
            .cache(appCache)
            .build()
            
        val dns = if (useDoH) {
            val (queryUrl, hosts) = when (provider) {
                "Google" -> Pair("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4"))
                "Quad9" -> Pair("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"))
                "AdGuard" -> Pair("https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15"))
                else -> Pair("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1"))
            }

            val inetAddresses = hosts.mapNotNull { 
                try { InetAddress.getByName(it) } catch (e: Exception) { null } 
            }

            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(queryUrl.toHttpUrl())
                .bootstrapDnsHosts(inetAddresses)
                .build()
        } else {
            okhttp3.Dns.SYSTEM
        }

        return OkHttpClient.Builder()
            .cache(appCache)
            .dns(dns)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun buildDataSourceFactory(context: Context, useDoH: Boolean = true, provider: String = "Cloudflare"): DataSource.Factory {
        val okHttpClient = buildDoHClient(context, useDoH, provider)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        okHttpDataSourceFactory.setUserAgent("RedSurf/1.0 (Android TV)")
        
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    }
}
