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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.RemoteActionCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.start
import com.antony.muzei.pixiv.util.IntentUtils
import com.google.android.apps.muzei.api.UserCommand
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

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
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context!!)
        if (sharedPrefs.getBoolean("pref_dataMode", false) && isOnMobileData()) {
            Log.i("ANTONY_PROVIDER", "Data saver mode, stopping new artwork download")
            return
        }
        PixivArtWorker.enqueueLoad(false, context)
    }

    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        if (!running) {
            return emptyList()
        }
        return commandManager.provideActions(checkContext(), artwork)
    }

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

    // ConnectivityManager.activeNetworkInfo deprecated in API level 29
    private fun isOnMobileData(): Boolean {
        val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 29) {
            val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            if (networkCapabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true
            }
        } else if (cm.activeNetworkInfo!!.type == ConnectivityManager.TYPE_MOBILE) {
            return true
        }
        return false
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
                        File(
                            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            "$token.png"
                        )
                    }
                    ?.let { file ->
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            file
                        )
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
        }
    }

    @SuppressLint("InlinedApi")
    override fun getArtworkInfo(artwork: Artwork): PendingIntent? {
        if (artwork.webUri != null && context != null) {
            val intent = Intent(Intent.ACTION_VIEW, artwork.webUri)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
        return null
    }
}
