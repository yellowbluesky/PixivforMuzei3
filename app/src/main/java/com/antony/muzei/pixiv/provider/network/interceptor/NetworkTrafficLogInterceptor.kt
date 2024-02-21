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

import android.util.Log
import com.antony.muzei.pixiv.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.Executors

/**
 * Custom http request log [Interceptor]
 */
class NetworkTrafficLogInterceptor : Interceptor {

    companion object {
        private const val TRAFFIC_TAG = "TRAFFIC"
    }

    private val logPrintDispatcher by lazy {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    private val defaultCharset by lazy { Charset.defaultCharset() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = try {
            chain.proceed(request)
        } catch (ex: IOException) {
            Log.e(TRAFFIC_TAG, "<-- HTTP FAILED: [${request.url}] - ${ex.message}", ex)
            throw ex
        }

        printRequestLog(request, response)
        return response
    }

    private fun printRequestLog(request: Request, response: Response) {
        val jsonStringOrRaw = { inputStr: String? ->
            if (inputStr == null)
                null
            else {
                try {
                    JSONObject(inputStr).toString(4)
                } catch (e: JSONException) {
                    inputStr
                }
            }
        }
        val formatHeaders = { headers: Headers ->
            val resList = mutableListOf<String>()
            headers.names().onEach { name ->
                resList.add("$name: ${headers.get(name)}")
            }
            resList
        }

        val requestBodyStr = request.body?.run {
            val buff = Buffer()
            writeTo(buff)
            buff.readString(defaultCharset)
        }
        val requestBodyString = jsonStringOrRaw(requestBodyStr)
        val responseString = readRespContent(response)

        val lineList = mutableListOf<String>()
        lineList.add("╔══════════════════════════════════════════════════════════════════════════")
        lineList.add("║ Request(${request.url})")
        lineList.add("╠═════════════════════════════>Request Header<═════════════════════════════")
        lineList.addAll(formatHeaders(request.headers).map { "║ $it" })
        lineList.add("╠═════════════════════════════>Request Body<═══════════════════════════════")
        requestBodyString?.also { reqBodyStr ->
            lineList.addAll(reqBodyStr.reader().readLines().map { "║ $it" })
        } ?: {
            lineList.add("║ null")
        }()
        lineList.add("╠═════════════════════════════>Response header<════════════════════════════")
        lineList.addAll(formatHeaders(response.headers).map { "║ $it" })
        lineList.add("╠═════════════════════════════>Response Body<══════════════════════════════")
        responseString?.also { resBodyStr ->
            lineList.addAll(resBodyStr.reader().readLines().map { "║ $it" })
        } ?: {
            lineList.add("║ null")
        }()
        lineList.add("╚══════════════════════════════════════════════════════════════════════════")

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            if (BuildConfig.DEBUG) {
                throwable.printStackTrace()
            }
        }
        GlobalScope.launch(logPrintDispatcher + exceptionHandler) {
            lineList.onEach { Log.d(TRAFFIC_TAG, it) }
        }
    }

    private fun readRespContent(response: Response): String? =
        response.body
            .let { body ->
                val source = body.source()
                    .apply {
                        request(Long.MAX_VALUE)
                    }
                var buffer = source.buffer
                val encoding = response.encoding()
                if ("gzip".equals(encoding, true)) {
                    GzipSource(buffer.clone()).use { gzippedBody ->
                        buffer = Buffer().also { it.writeAll(gzippedBody) }
                    }
                }
                buffer
            }
            ?.clone()
            ?.readString(response.charset())

    private fun Response.encoding() = this.header("content-encoding") ?: this.header("Content-Encoding")

    private fun Response.charset(): Charset {
        this.encoding()
            ?.takeIf { Charset.isSupported(it) }
            ?.also {
                return Charset.forName(it)
            }
        return body.contentType()?.charset() ?: defaultCharset
    }

}
