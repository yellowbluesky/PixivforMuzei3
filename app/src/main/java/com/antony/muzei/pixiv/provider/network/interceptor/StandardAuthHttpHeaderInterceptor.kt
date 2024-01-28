package com.antony.muzei.pixiv.provider.network.interceptor

import android.annotation.SuppressLint
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import com.antony.muzei.pixiv.PixivInstrumentation.Companion.INTENT_ACTION_ACCESS_TOKEN_MISSING
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.PixivProviderConst.APP_USER_AGENT
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import okhttp3.Interceptor
import okhttp3.Response
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Date

// This interceptor makes all outgoing requests to authenticated endpoints (OAuth, feeds that require user login)
// look just like the ones made with the official Pixiv app
// OAuth tokens are added in PixivAuthHeaderInterceptor
class StandardAuthHttpHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()

        @SuppressLint("SimpleDateFormat")
        val formattedTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val df = SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ssXXX")
            df.format(Date())
        } else {
            ""
        }
        val intermediateString = formattedTime + "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

        val md = MessageDigest.getInstance("MD5")
        val hash = BigInteger(1, md.digest(intermediateString.toByteArray())).toString(16).padStart(32, '0')

        val newRequest = originRequest.newBuilder()
            //.header("authorization", "Bearer $token")
            .header("user-agent", APP_USER_AGENT)
            .header("content-type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("accept-language", "en_US")
            .header("app-accept-language", "en")
            .header("app-os", "android")
            .header("app-os-version", "6.0")
            .header("app-version", "6.96.0")
            .header("x-client-time", formattedTime)
            .header("x-client-hash", hash)
            .build()

        return chain.proceed(newRequest)
    }
}
