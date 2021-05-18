package com.antony.muzei.pixiv.provider.network

import android.annotation.SuppressLint
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
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

    private var instance: OkHttpClient? = null

    fun getInstance(): OkHttpClient {
        if (instance == null) {
            instance = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .sslSocketFactory(RubySSLSocketFactory(), x509TrustManager)
                .dns(RubyHttpDns.getInstance())
                //.hostnameVerifier { _, _ -> true }
                .logOnDebug()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }
        return instance as OkHttpClient
    }
}
