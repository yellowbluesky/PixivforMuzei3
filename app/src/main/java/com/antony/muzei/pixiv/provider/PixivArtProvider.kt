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

@file:Suppress("DEPRECATION")

package com.antony.muzei.pixiv.provider

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteActionCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.getAccessToken
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.start
import com.antony.muzei.pixiv.PixivProviderConst
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.provider.network.RubyHttpDns
import com.antony.muzei.pixiv.provider.network.RubySSLSocketFactory
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor
import com.antony.muzei.pixiv.util.IntentUtils
import com.google.android.apps.muzei.api.UserCommand
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import okhttp3.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

class PixivArtProvider : MuzeiArtProvider() {

    companion object {
        const val TAG = "PixivArtProviderKt"
    }

    private val commandManager by lazy { MuzeiCommandManager() }

    private var running = false

    override fun onCreate(): Boolean {
        super.onCreate()
        running = true

        start(checkContext())

        return true
    }

    override fun onLoadRequested(initial: Boolean) {
        PixivArtWorker.enqueueLoad(false, context)
    }

    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        if (!running) {
            return emptyList()
        }
        return commandManager.provideActions(checkContext(), artwork)
    }

    //<editor-fold desc="Deprecated in Muzei">

    @Suppress("OverridingDeprecatedMember")
    override fun getCommands(artwork: Artwork): List<UserCommand> {
        if (!running) {
            return emptyList()
        }
        return commandManager.provideActionsLegacy(checkContext(), artwork)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun onCommand(artwork: Artwork, id: Int) {
        if (!running) {
            return
        }
        handleCommandLegacy(checkContext(), artwork, id)
    }

    //</editor-fold>

    @Throws(IOException::class)
    override fun openFile(artwork: Artwork): InputStream {
        val context = try {
            checkContext()
        } catch (ex: IllegalStateException) {
            throw IOException("Provider not prepared.", ex)
        }

        val artworkPersistentUri = artwork.persistentUri
            ?: throw IOException("Require non-null persistent uri in Artwork $artwork")

        val inputStream = try {
            context.contentResolver.openInputStream(artworkPersistentUri)
        } catch (ex: FileNotFoundException) {
            Log.d(TAG, "Fail to open stream: $artworkPersistentUri", ex)
            throw IOException("Fail to open stream: $artworkPersistentUri", ex)
        }
        return requireNotNull(inputStream)
    }

    /**
     * Return the [Context] this provider is running in.
     *
     * @throws IllegalStateException if not currently running after [onCreate].
     */
    @Throws(IllegalStateException::class)
    private fun checkContext(): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            check(running) { "Provider $this not in running." }
            requireContext()
        } else {
            val context = context
            check(running && context != null) { "Provider $this not in running." }
            context
        }
    }

    private fun handleCommandLegacy(context: Context, artwork: Artwork, id: Int) {
        when (id) {
            MuzeiCommandManager.COMMAND_VIEW_IMAGE_DETAILS -> {
                artwork.token
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { token ->
                            Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=$token")
                            )
                        }
                        ?.also { intent ->
                            IntentUtils.launchActivity(context, intent)
                        }
            }
            MuzeiCommandManager.COMMAND_SHARE_IMAGE -> {
                Log.d("ANTONY_WORKER", "Opening sharing ")
                artwork.token
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { token ->
                            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$token.png")
                        }
                        ?.let { file ->
                            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                        }
                        ?.let { uri ->
                            Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                            }
                        }
                        ?.also {
                            IntentUtils.launchActivity(context, it)
                        }
            }
            MuzeiCommandManager.COMMAND_ADD_TO_BOOKMARKS -> {
                Log.d("PIXIV_DEBUG", "addToBookmarks(): Entered")
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
                if (sharedPrefs.getString(PixivProviderConst.PREFERENCE_PIXIV_ACCESS_TOKEN, "").isNullOrEmpty()) {
                    PixivMuzeiSupervisor.post {
                        Toast.makeText(context, R.string.toast_loginFirst, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                val accessToken = try {
                    getAccessToken()
                } catch (e: AccessTokenAcquisitionException) {
                    PixivMuzeiSupervisor.post {
                        Toast.makeText(context, R.string.toast_loginFirst, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                // TODO this is a ugly mess
                val rankingUrl = HttpUrl.Builder()
                        .scheme("https")
                        .host("app-api.pixiv.net")
                        .addPathSegments("v2/illust/bookmark/add")
                        .build()
                val authData: RequestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("illust_id", artwork.token!!)
                        .addFormDataPart("restrict", "public")
                        .build()
                val request = Request.Builder()
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(authData)
                        .url(rankingUrl)
                        .build()

                var httpClient: OkHttpClient? = null
                if (Locale.getDefault().isO3Language == "zho") {
                    val builder = OkHttpClient.Builder()
                    builder.sslSocketFactory(RubySSLSocketFactory(), object : X509TrustManager {
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, s: String) {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, s: String) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> {
                            return arrayOf()
                        }
                    }) //SNI bypass
                    builder.hostnameVerifier { s: String?, sslSession: SSLSession? -> true } //disable hostnameVerifier
                    builder.addInterceptor(NetworkTrafficLogInterceptor())
                    builder.dns(RubyHttpDns()) //define the direct ip address
                    httpClient = builder.build()
                    /* SNI Bypass end */
                }

                if (httpClient == null) {
                    return
                }

                try {
                    httpClient.newCall(request).execute()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
                Log.d("PIXIV_DEBUG", "Added to bookmarks")
                PixivMuzeiSupervisor.post {
                    Toast.makeText(context, R.string.toast_addingToBookmarks, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
