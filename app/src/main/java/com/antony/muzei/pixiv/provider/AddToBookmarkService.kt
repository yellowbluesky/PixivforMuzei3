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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.network.OkHttpSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody

class AddToBookmarkService : Service(), CoroutineScope by CoroutineScope(Dispatchers.Main + SupervisorJob()) {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Adding artwork to bookmarks")
                .setContentText(intent.getStringExtra("artworkTitle") + " by " + intent.getStringExtra("artworkArtist"))
                .setSmallIcon(R.drawable.ic_baseline_bookmark_24)
                .build()
        startForeground(1, notification)

        val rankingUrl: HttpUrl = HttpUrl.Builder()
                .scheme("https")
                .host("app-api.pixiv.net")
                .addPathSegments("v2/illust/bookmark/add")
                .build()
        val authData: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("illust_id", intent.getStringExtra("artworkId")!!)
                .addFormDataPart("restrict", "public")
                .build()
        val request: Request = Request.Builder()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
                .addHeader("Authorization", "Bearer " + intent.getStringExtra("accessToken"))
                .addHeader("Connection", "close")
                .post(authData)
                .url(rankingUrl)
                .build()

        val call = OkHttpSingleton.getInstance().newCall(request)
        // NetworkOnMainThread exception
        launch(Dispatchers.IO) {
            call.execute()
        }
        stopSelf()
        return START_REDELIVER_INTENT
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Pixiv for Muzei 3 Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        const val CHANNEL_ID = "PixivForMuzei3NotificationChannel"
    }
}
