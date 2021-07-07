package com.antony.muzei.pixiv.provider.network

import android.annotation.SuppressLint
import android.util.Log
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.PixivMuzei
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

object OkHttpSingleton {
    private const val LOG_TAG = "OkHttpSingleton"
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
                .apply {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(PixivMuzei.context?.applicationContext)
                    val enableNetworkBypass = prefs.getBoolean("pref_enableNetworkBypass", false)
                    Log.d(LOG_TAG,"network bypass was $enableNetworkBypass")
                    if (enableNetworkBypass) {
                        sslSocketFactory(RubySSLSocketFactory(), x509TrustManager)
                        dns(RubyHttpDns.getInstance())
                    }
                }
                //.hostnameVerifier { _, _ -> true }
                .logOnDebug()
                .build()
        }
        return instance as OkHttpClient
    }

    fun refreshInstance(){
        // Through set it to null, the OkHttpClient will be create again and apply with new preference when `getInstance` was invoked.
        instance = null
        Log.d(LOG_TAG,"set OkHttp instance to null")
    }
}
