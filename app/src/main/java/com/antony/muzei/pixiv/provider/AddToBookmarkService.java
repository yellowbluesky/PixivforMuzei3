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

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;

import com.antony.muzei.pixiv.R;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddToBookmarkService extends JobIntentService {
    final Handler mHandler = new Handler();

    private static final String TAG = "AddToBookmarkService";
    /**
     * Unique job ID for this service.
     */
    private static final int JOB_ID = 2;
    OkHttpClient client = new OkHttpClient();
    String BASE_URL = "https://ptsv2.com/";

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, AddToBookmarkService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        // TODO use RestClient
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
        try {
            client.newCall(request).execute();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }
}
