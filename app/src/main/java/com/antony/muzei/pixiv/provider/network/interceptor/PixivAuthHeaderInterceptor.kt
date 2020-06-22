package com.antony.muzei.pixiv.provider.network.interceptor

import android.content.Intent
import android.text.TextUtils
import android.util.Log
import com.antony.muzei.pixiv.PixivInstrumentation.Companion.INTENT_ACTION_ACCESS_TOKEN_MISSING
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Pixiv auth request [Interceptor] that automatic append access token to headers
 *
 * Created by alvince on 2020/6/13
 *
 * @author alvince.zy@gmail.com
 */
class PixivAuthHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()
        val token = try {
            PixivMuzeiSupervisor.getAccessToken()
        } catch (ex: AccessTokenAcquisitionException) {
            Log.e("TRAFFIC", "Fail to retrieves access token on request ${originRequest.url}", ex)
            PixivMuzeiSupervisor.broadcastLocal(Intent(INTENT_ACTION_ACCESS_TOKEN_MISSING))
            ""
        }
        if (TextUtils.isEmpty(token)) {
            return chain.proceed(originRequest)
        }
        return originRequest.newBuilder().apply {
            addHeader("Authorization", "Bearer $token")
        }.let { builder ->
            chain.proceed(builder.build())
        }
    }

}
