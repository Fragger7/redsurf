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
import java.net.UnknownHostException

object IptvNetworkModule {

    private fun buildDoHClient(context: Context, useDoH: Boolean = true): OkHttpClient {
        val appCache = okhttp3.Cache(File(context.cacheDir, "okhttpcache"), 10 * 1024 * 1024)
        
        val bootstrapClient = OkHttpClient.Builder()
            .cache(appCache)
            .build()
            
        // Setup Cloudflare DNS over HTTPS to bypass ISP level blocking
        val dns = if (useDoH) {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("8.8.8.8")
                )
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

    fun buildDataSourceFactory(context: Context, useDoH: Boolean = true): DataSource.Factory {
        val okHttpClient = buildDoHClient(context, useDoH)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        okHttpDataSourceFactory.setUserAgent("RedSurf/1.0 (Android TV)")
        
        // Wrap in DefaultDataSource for local file/asset fallback support
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    }
}
