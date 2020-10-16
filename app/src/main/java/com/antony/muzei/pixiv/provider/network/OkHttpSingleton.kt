package com.antony.muzei.pixiv.provider.network

import android.annotation.SuppressLint
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

object OkHttpSingleton {
    private val x509TrustManager: X509TrustManager = object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, s: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, s: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    private fun OkHttpClient.Builder.logOnDebug(): OkHttpClient.Builder =
            this.apply {
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(NetworkTrafficLogInterceptor())
                }
            }

    private val OkHttpSingleton: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .sslSocketFactory(RubySSLSocketFactory(), x509TrustManager)
            .hostnameVerifier { _: String?, _: SSLSession? -> true }
            .dns(RubyHttpDns())
            .logOnDebug()
            .build()

    fun getInstance(): OkHttpClient {
        return OkHttpSingleton
    }
}
