/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

        // TODO: filter by url simply temporary, should split clients for different retrofit
        if (originRequest.url.toString().endsWith("/auth/token")) {
            return chain.proceed(originRequest)
        }

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
