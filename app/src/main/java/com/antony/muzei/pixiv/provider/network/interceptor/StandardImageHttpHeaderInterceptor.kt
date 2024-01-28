package com.antony.muzei.pixiv.provider.network.interceptor

import com.antony.muzei.pixiv.PixivProviderConst
import com.antony.muzei.pixiv.PixivProviderConst.APP_USER_AGENT
import okhttp3.Interceptor
import okhttp3.Response

// This interceptor makes all outgoing requests to download images look just like the ones made with the official Pixiv app
class StandardImageHttpHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originRequest = chain.request()

        val newRequest = originRequest.newBuilder()
            .header("user-agent", APP_USER_AGENT)
            .header("Referer", PixivProviderConst.PIXIV_API_HOST_URL)
            .build()

        return chain.proceed(newRequest)
    }
}
