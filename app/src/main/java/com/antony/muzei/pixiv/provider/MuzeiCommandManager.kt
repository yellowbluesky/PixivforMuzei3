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

package com.antony.muzei.pixiv.provider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.RemoteActionCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.getAccessToken
import com.antony.muzei.pixiv.PixivProviderConst
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.util.IntentUtils
import com.google.android.apps.muzei.api.UserCommand
import com.google.android.apps.muzei.api.provider.Artwork
import java.io.File

/**
 * Muzei artwork commands manager
 *
 * Created by alvince on 2020/6/29
 *
 * @author alvince.zy@gmail.com
 */
@Suppress("DEPRECATION")
class MuzeiCommandManager {

    companion object {
        const val COMMAND_ADD_TO_BOOKMARKS = 517
        const val COMMAND_VIEW_IMAGE_DETAILS = 988
        const val COMMAND_SHARE_IMAGE = 883
    }

    fun provideActions(context: Context, artwork: Artwork): List<RemoteActionCompat> {
        val list = mutableListOf<RemoteActionCompat>().apply {
            obtainActionShareImage(context, artwork)
                    ?.also { add(it) }
            obtainActionViewArtworkDetails(context, artwork)
                    ?.also { add(it) }
            obtainActionDeleteArtwork(context, artwork)
                    ?.also { add(it) }
        }

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPrefs.getString("accessToken", "")?.isEmpty()!!) {
            obtainActionAddToBookmarks(context, artwork)?.let { list.add(it) }
        }

        return list
    }

    fun provideActionsLegacy(context: Context, artwork: Artwork): List<UserCommand> {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return emptyList()
        }
        return mutableListOf(
                UserCommand(
                        COMMAND_VIEW_IMAGE_DETAILS,
                        context.getString(R.string.command_viewArtworkDetails)
                ),
                UserCommand(
                        COMMAND_SHARE_IMAGE,
                        context.getString(R.string.command_shareImage)
                ),
                UserCommand(
                        COMMAND_ADD_TO_BOOKMARKS,
                        context.getString(R.string.command_addToBookmark)
                )
        )
    }

    private fun obtainActionShareImage(context: Context, artwork: Artwork): RemoteActionCompat? =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${artwork.token}.png").let { f ->
                    FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", f)
                }.also { uri ->
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            }.let { intent ->
                IntentUtils.chooseIntent(intent, PixivProviderConst.SHARE_IMAGE_INTENT_CHOOSER_TITLE, context)
            }.let { actualIntent ->
                PendingIntent.getActivity(context, artwork.id.toInt(), actualIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            }.let { pendingIntent ->
                val title = context.getString(R.string.command_shareImage)
                RemoteActionCompat(
                        IconCompat.createWithResource(context, R.drawable.ic_baseline_share_24),
                        title,
                        title,
                        pendingIntent
                )
            }

    private fun obtainActionViewArtworkDetails(context: Context, artwork: Artwork): RemoteActionCompat? =
            artwork.token?.takeIf { it.isNotEmpty() }?.let { token ->
                Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=$token")
                )
            }?.let { intent ->
                PendingIntent.getActivity(context, artwork.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }?.let { pendingIntent ->
                val title = context.getString(R.string.command_viewArtworkDetails)
                RemoteActionCompat(
                        IconCompat.createWithResource(context, R.drawable.muzei_launch_command),
                        title,
                        title,
                        pendingIntent
                ).apply {
                    setShouldShowIcon(false)
                }
            }

    private fun obtainActionAddToBookmarks(context: Context, artwork: Artwork): RemoteActionCompat? =
            Intent(context, AddToBookmarkService::class.java).apply {
                putExtra("artworkId", artwork.token.toString())
                putExtra("accessToken", getAccessToken())
                putExtra("artworkTitle", artwork.title)
                putExtra("artworkArtist", artwork.byline)
            }.let { intent ->
                PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }.let { pendingIntent ->
                val label = context.getString(R.string.command_addToBookmark)
                RemoteActionCompat(
                        IconCompat.createWithResource(context, R.drawable.ic_baseline_bookmark_24),
                        label,
                        label,
                        pendingIntent
                ).apply {
                    setShouldShowIcon(true)
                }
            }

    private fun obtainActionDeleteArtwork(context: Context, artwork: Artwork): RemoteActionCompat? =
            Intent(context, DeleteArtworkReceiver::class.java).apply {
                putExtra("artworkId", artwork.token)
            }.let { intent ->
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }.let { pendingIntent ->
                val title = context.getString(R.string.command_delete_artwork)
                RemoteActionCompat(
                        IconCompat.createWithResource(context, R.drawable.ic_delete_white_24dp),
                        title,
                        title,
                        pendingIntent
                ).apply {
                    setShouldShowIcon(false)
                }
            }
}
