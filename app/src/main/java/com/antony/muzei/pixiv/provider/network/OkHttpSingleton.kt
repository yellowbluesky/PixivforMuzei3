package com.antony.muzei.pixiv.provider.network

import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.conscrypt.Conscrypt
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.Security

object OkHttpSingleton {
    init {
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    private val ipv6Ready = NetworkInterface.getNetworkInterfaces().toList()
            .any { it.interfaceAddresses.any { i -> i.address.run { this is Inet6Address && !isLoopbackAddress && !isLinkLocalAddress && !isSiteLocalAddress } } }
    private val cloudFlareDoH = DnsOverHttps.Builder()
            .client(OkHttpClient())
            .url("https://muzeipixivsource.cloudflare-dns.com/dns-query".toHttpUrl())
            .post(true)
            .includeIPv6(ipv6Ready)
            .bootstrapDnsHosts(listOf("104.16.248.249", "104.16.249.249").map(InetAddress::getByName))
            .build()
    private val googleDoH = DnsOverHttps.Builder()
            .client(OkHttpClient())
            .url("https://dns.google/dns-query".toHttpUrl())
            .post(true)
            .includeIPv6(ipv6Ready)
            .bootstrapDnsHosts(listOf("8.8.8.8", "8.8.4.4").map(InetAddress::getByName))
            .build()
    private val HEDoH = DnsOverHttps.Builder()
            .client(OkHttpClient())
            .url("https://ordns.he.net/".toHttpUrl())
            .post(true)
            .includeIPv6(ipv6Ready)
            .bootstrapDnsHosts(InetAddress.getByName("74.82.42.42"))
            .build()
    private val dns = FallbackDns(cloudFlareDoH, HEDoH, googleDoH, Dns.SYSTEM)

    private fun OkHttpClient.Builder.logOnDebug(): OkHttpClient.Builder =
            this.apply {
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(NetworkTrafficLogInterceptor())
                }
            }

    private val OkHttpSingleton: OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
            .dns(dns)
            .logOnDebug()
            .build()

    fun getInstance(): OkHttpClient {
        return OkHttpSingleton
    }
}
