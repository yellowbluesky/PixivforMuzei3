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
package com.antony.muzei.pixiv.provider.network

import com.antony.muzei.pixiv.PixivProviderConst.OAUTH_URL
import com.antony.muzei.pixiv.PixivProviderConst.PIXIV_API_HOST_URL
import com.antony.muzei.pixiv.PixivProviderConst.PIXIV_IMAGE_URL
import com.antony.muzei.pixiv.PixivProviderConst.PIXIV_RANKING_URL
import com.antony.muzei.pixiv.provider.network.interceptor.PixivAuthHeaderInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RestClient {
    private val okHttpClientAuthBuilder
        get() = OkHttpSingleton.getInstance().newBuilder()
            .apply {
                addNetworkInterceptor(PixivAuthHeaderInterceptor())
                //addInterceptor(CustomClientHeaderInterceptor())
            }

    // Used for acquiring Ranking JSON
    fun getRetrofitRankingInstance(): Retrofit {
        val okHttpClientRanking =
            OkHttpSingleton.getInstance().newBuilder() // Debug logging interceptor
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val originalHttpUrl = original.url
                    val url = originalHttpUrl.newBuilder()
                        .addQueryParameter("format", "json")
                        .build()
                    val request =
                        original.newBuilder() // Using the Android User-Agent returns a HTML of the ranking page, instead of the JSON I need
                            .header("Referer", PIXIV_RANKING_URL)
                            .url(url)
                            .build()
                    chain.proceed(request)
                })
                .build()
        return Retrofit.Builder()
            .client(okHttpClientRanking)
            .baseUrl(PIXIV_RANKING_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used for acquiring auth feed mode JSON
    fun getRetrofitAuthInstance(): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClientAuthBuilder.build())
            .baseUrl(PIXIV_API_HOST_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used to add artworks to your list of bookmarks
    fun getRetrofitBookmarkInstance(): Retrofit {
        return Retrofit.Builder()
            .client(OkHttpSingleton.getInstance())
            .baseUrl(PIXIV_API_HOST_URL)
            .build()
    }

    // Downloads images from any source
    fun getRetrofitImageInstance(): Retrofit {
        val imageHttpClient = OkHttpSingleton.getInstance().newBuilder()
            .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Referer", PIXIV_RANKING_URL)
                    .build()
                chain.proceed(request)
            })
            .build()
        return Retrofit.Builder()
            .client(imageHttpClient)
            .baseUrl(PIXIV_IMAGE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Used for getting an accessToken from a refresh token or username / password
    @JvmStatic
    fun getRetrofitOauthInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OAUTH_URL)
            .client(okHttpClientAuthBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
}
