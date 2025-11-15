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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.network.OkHttpSingleton
import com.antony.muzei.pixiv.provider.network.interceptor.PixivAuthHeaderInterceptor
import com.antony.muzei.pixiv.provider.network.interceptor.StandardAuthHttpHeaderInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class AddToBookmarkService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adding artwork to bookmarks")
            .setContentText("${intent.getStringExtra("artworkTitle")} by ${intent.getStringExtra("artworkArtist")}")
            .setSmallIcon(R.drawable.ic_baseline_bookmark_24)
            .build()
            .let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    startForeground(1, it)
                } else {
                    startForeground(
                        1, it,
                        FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            }

        try {
            // NetworkOnMainThread exception
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch(Dispatchers.IO) {
                // in here execute a post request
                // no need to use the service really
                val imageHttpClient = OkHttpSingleton.getInstance().newBuilder()
                    .addInterceptor(PixivAuthHeaderInterceptor())
                    .addInterceptor(StandardAuthHttpHeaderInterceptor())
                    .build()

                val formBody = FormBody.Builder()
                    .add("illust_id", intent.getStringExtra("artworkId")!!)
                    .add("restrict", if (intent.getBooleanExtra("isPrivate", false)) "private" else "public")
                    .build()

                val request = Request.Builder()
                    .url("https://app-api.pixiv.net/v2/illust/bookmark/add")
                    .post(formBody)
                    .build()

                val artworkTitle = intent.getStringExtra("artworkTitle") ?: ""

                imageHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        PixivMuzeiSupervisor.post(Runnable {
                            Toast.makeText(
                                applicationContext,
                                if (intent.getBooleanExtra("isPrivate", false))
                                    applicationContext.getString(R.string.toast_bookmark_private_success, artworkTitle)
                                else
                                    applicationContext.getString(R.string.toast_bookmark_success, artworkTitle),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                    } else {
                        PixivMuzeiSupervisor.post(Runnable {
                            Toast.makeText(
                                applicationContext,
                                applicationContext.getString(R.string.toast_bookmark_failure, artworkTitle),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                    }
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        const val CHANNEL_ID = "PixivForMuzei3NotificationChannel"
    }
}
