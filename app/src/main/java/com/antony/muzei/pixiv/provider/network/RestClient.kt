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

import android.os.Build
import com.antony.muzei.pixiv.PixivProviderConst.PIXIV_HOST_URL
import com.antony.muzei.pixiv.provider.network.interceptor.PixivAuthHeaderInterceptor
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*

object RestClient {

    private const val HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

    private const val PIXIV_API_HOST = "https://app-api.pixiv.net"

    private val okHttpClientAuthBuilder = OkHttpSingleton.getInstance().newBuilder()
            .apply {
                addNetworkInterceptor(PixivAuthHeaderInterceptor())
                addInterceptor(CustomClientHeaderInterceptor())
            }

    // Used for acquiring Ranking JSON
    fun getRetrofitRankingInstance(bypass: Boolean): Retrofit {
        val okHttpClientRanking = OkHttpSingleton.getInstance().newBuilder() // Debug logging interceptor
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val originalHttpUrl = original.url
                    val url = originalHttpUrl.newBuilder()
                            .addQueryParameter("format", "json")
                            .build()
                    val request =
                            original.newBuilder() // Using the Android User-Agent returns a HTML of the ranking page, instead of the JSON I need
                                    .header(
                                            "User-Agent",
                                            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0"
                                    )
                                    .header("Referer", PIXIV_HOST_URL)
                                    .url(url)
                                    .build()
                    chain.proceed(request)
                })
                .build()
        return Retrofit.Builder()
                .client(okHttpClientRanking)
                .baseUrl(PIXIV_HOST_URL)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
    }

    // Used for acquiring auth feed mode JSON
    fun getRetrofitAuthInstance(bypass: Boolean): Retrofit {
        return Retrofit.Builder()
                .client(okHttpClientAuthBuilder.build())
                .baseUrl(PIXIV_API_HOST)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
    }

    // Downloads images from any source
    fun getRetrofitImageInstance(bypass: Boolean): Retrofit {
        val imageHttpClient = OkHttpSingleton.getInstance().newBuilder()
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                            .header(
                                    "User-Agent",
                                    "PixivAndroidApp/5.0.220 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")"
                            )
                            .header("Referer", PIXIV_HOST_URL)
                            .build()
                    chain.proceed(request)
                })
                .build()
        return Retrofit.Builder()
                .client(imageHttpClient)
                .baseUrl("https://i.pximg.net")
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
    }

    // Used for getting an accessToken from a refresh token or username / password
    @JvmStatic
    fun getRetrofitOauthInstance(bypass: Boolean): Retrofit {
        return Retrofit.Builder()
                .baseUrl("https://oauth.secure.pixiv.net")
                .client(okHttpClientAuthBuilder.build())
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
    }

    fun getRetrofitBookmarkInstance(bypass: Boolean): Retrofit {
        val okHttpClientBookmark = OkHttpSingleton.getInstance().newBuilder()
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header(
                                    "User-Agent",
                                    "PixivAndroidApp/5.0.220 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")"
                            )
                            .build()
                    chain.proceed(request)
                })
                .build()
        return Retrofit.Builder()
                .baseUrl(PIXIV_API_HOST)
                .client(okHttpClientBookmark)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
    }

    private fun getHashSecret(dateSecretConcat: String): String {
        try {
            val digestInstance = MessageDigest.getInstance("MD5")
            val messageDigest = digestInstance.digest(dateSecretConcat.toByteArray())
            val hexString = StringBuilder()
            // this loop is horrifically inefficient on CPU and memory
            // but is only executed once to acquire a new access token
            // i.e. at most once per hour for normal use case
            for (aMessageDigest in messageDigest) {
                val h = StringBuilder(Integer.toHexString(0xFF and aMessageDigest.toInt()))
                while (h.length < 2) {
                    h.insert(0, "0")
                }
                hexString.append(h)
            }
            return hexString.toString()
        } catch (ex: NoSuchAlgorithmException) {
            ex.printStackTrace()
        }
        // TODO replace this place holder
        return ""
    }

    /**
     * Custom app client request-header [Interceptor]
     */
    private class CustomClientHeaderInterceptor : Interceptor {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.getDefault())

        override fun intercept(chain: Interceptor.Chain): Response {
            // Suppressed because I'm supplying a format string, no locale is implied or used
            val rfc3339Date = dateFormat.format(Date())
            val dateSecretConcat = rfc3339Date + HASH_SECRET
            val hashSecret = getHashSecret(dateSecretConcat)
            val original = chain.request()
            val request = original.newBuilder()
                    .header(
                            "User-Agent",
                            "PixivAndroidApp/5.0.155 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")"
                    )
                    .header("App-OS", "Android")
                    .header("App-OS-Version", Build.VERSION.RELEASE)
                    .header("App-Version", "5.0.220") //.header("Accept-Language", Locale.getDefault().toString())
                    .header("X-Client-Time", rfc3339Date)
                    .header("X-Client-Hash", hashSecret)
                    .build()
            return chain.proceed(request)
        }
    }

}
