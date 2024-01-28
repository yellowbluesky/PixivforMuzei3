package com.antony.muzei.pixiv.provider.network.interceptor

import android.util.Log
import com.antony.muzei.pixiv.provider.exceptions.CorruptFileException
import okhttp3.Interceptor
import okhttp3.Response

// This interceptor makes sure the artworks we are downloading from Pixiv are nto corrupt due to network issues
// It does this by checking the declared length from the HTTP response header ("content-length")
// Then comparing it to the actual length of the response body.
// If a mismatch is found then the request is retried up to three times after a short delay.
// If an intact is still not found, then an exception is thrown
class ImageIntegrityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())

        // This interceptor should only work for image responses
        if (response.header("content-type")?.contains("image") == false) {
            Log.d("LENGTH", "Not an image")
            return response
        }

        var retryCount = 0
        while (retryCount < 3) {
            val contentLength = response.header("content-length")?.toLong()
            val responseLength = response.peekBody(Long.MAX_VALUE).bytes().size.toLong()
            // I am unsure where response.body.contentLength() gets its answer from, but I am reasonably sure that it mirrors the header value
            // Which is why I am making a copy of the body and then counting the number of bytes
            // I am aware that this will buffer the entire body into memory and then is discarded immediately after use

            Log.d("LENGTH", "Reported length: $contentLength")
            Log.d("LENGTH", "Actual length: $responseLength")

            if (contentLength == responseLength) {
                return response
            }
            response = chain.proceed(chain.request())
            retryCount++
            Log.d("LENGTH", "Corrupt image found, attempt $retryCount / 3")
        }
        throw CorruptFileException("Could not download intact image")
    }
}
