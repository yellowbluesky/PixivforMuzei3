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
package com.antony.muzei.pixiv

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteActionCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.exceptions.AccessTokenAcquisitionException
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class PixivArtProvider : MuzeiArtProvider() {
    // Pass true to clear cache and download new images
    // Pass false to append new images to cache
    override fun onLoadRequested(clearCache: Boolean) {
        PixivArtWorker.enqueueLoad(false)
    }

    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        val list: MutableList<RemoteActionCompat>? = null
        list!!.add(shareImage(artwork))
        //list.add(viewArtworkDetailsAlternate(artwork));
        return list
    }

    private fun shareImage(artwork: Artwork): RemoteActionCompat {
        Log.d("ANTONY_WORKER", "Opening sharing ")
        val newFile = File(context!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES), artwork.token + ".png")
        val uri = FileProvider.getUriForFile(context!!, "com.antony.muzei.pixiv.fileprovider", newFile)
        val sharingIntent = Intent()
        sharingIntent.action = Intent.ACTION_SEND
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri)
        sharingIntent.type = "image/jpg"
        //sharingIntent.putExtra(Intent.EXTRA_TEXT)
        val title = context!!.getString(R.string.command_shareImage)
        val chooserIntent = Intent.createChooser(sharingIntent, null)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.muzei_launch_command),
                title,
                title,
                PendingIntent.getActivity(
                        context,
                        artwork.id.toInt(),
                        chooserIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun viewArtworkDetailsAlternate(artwork: Artwork): RemoteActionCompat {
        val token = artwork.token
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=$token"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return RemoteActionCompat(IconCompat.createWithResource(context, R.drawable.muzei_launch_command),
                context!!.getString(R.string.command_viewArtworkDetails),
                "sample Description",
                PendingIntent.getActivity(context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT))
    }

    // Provided you are logged in, adds the currently displayed images to your Pixiv bookmarks
    // Only works on Android 9 and lower, as Android 10 limits the ability to start activities in the background
    private fun addToBookmarks(artwork: Artwork) {
        Log.d("ANTONY_WORKER", "addToBookmarks(): Entered")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val accessToken: String
        accessToken = try {
            PixivArtService.refreshAccessToken(sharedPrefs)
        } catch (e: AccessTokenAcquisitionException) {
            Log.d("ANTONY_WORKER", "No access token found")
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, context!!.getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show() }
            return
        }
        PixivArtService.sendBookmarkPostRequest(accessToken, artwork.token)
        Log.d("ANTONY_WORKER", "Added to bookmarks")
    }

    override fun openFile(artwork: Artwork): InputStream {
        var inputStream: InputStream? = null
        try {
            inputStream = context!!.contentResolver.openInputStream(artwork.persistentUri!!)
        } catch (ex: FileNotFoundException) {
            ex.printStackTrace()
        }
        return inputStream!!
    }
}