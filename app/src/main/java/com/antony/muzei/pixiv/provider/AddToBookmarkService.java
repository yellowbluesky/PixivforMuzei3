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

package com.antony.muzei.pixiv.provider;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.common.PixivMuzeiActivity;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddToBookmarkService extends Service {
    public static final String CHANNEL_ID = "PixivForMuzei3NotificationChannel";
    // TODO use restclient
    OkHttpClient client = new OkHttpClient();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Adding artwork to bookmarks")
                .setContentText(intent.getStringExtra("artworkTitle") + " by" + intent.getStringExtra("artworkArtist"))
                .setSmallIcon(R.drawable.ic_baseline_bookmark_24)
                .build();

        startForeground(1, notification);

        //do heavy work on a background thread
        HttpUrl rankingUrl = new HttpUrl.Builder()
                .scheme("https")
                .host("app-api.pixiv.net")
                .addPathSegments("v2/illust/bookmark/add")
                .build();
        RequestBody authData = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("illust_id", intent.getStringExtra("artworkId"))
                .addFormDataPart("restrict", "public")
                .build();
        Request request = new Request.Builder()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
                .addHeader("Authorization", "Bearer " + intent.getStringExtra("accessToken"))
                .post(authData)
                .url(rankingUrl)
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

            }
        });

        stopSelf();

        return START_REDELIVER_INTENT;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pixiv for Muzei 3 Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
