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

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.RemoteActionCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.IconCompat;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.BuildConfig;
import com.antony.muzei.pixiv.PixivMuzeiSupervisor;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException;
import com.antony.muzei.pixiv.provider.network.RubyHttpDns;
import com.antony.muzei.pixiv.provider.network.RubySSLSocketFactory;
import com.antony.muzei.pixiv.provider.network.interceptor.NetworkTrafficLogInterceptor;
import com.antony.muzei.pixiv.util.IntentUtils;
import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.antony.muzei.pixiv.PixivProviderConst.PREFERENCE_PIXIV_ACCESS_TOKEN;
import static com.antony.muzei.pixiv.PixivProviderConst.SHARE_IMAGE_INTENT_CHOOSER_TITLE;

public class PixivArtProvider extends MuzeiArtProvider {

    private static final String TAG = "PixivArtProvider";

    private static final int COMMAND_ADD_TO_BOOKMARKS = 1;
    private static final int COMMAND_VIEW_IMAGE_DETAILS = 2;
    private static final int COMMAND_SHARE_IMAGE = 3;

    private boolean running = false;

    @Override
    public boolean onCreate() {
        super.onCreate();
        running = true;

        PixivMuzeiSupervisor.INSTANCE.start(checkContext());

        return true;
    }

    // Pass true to clear cache and download new images
    // Pass false to append new images to cache
    @Override
    public void onLoadRequested(boolean clearCache) {
        PixivArtWorker.enqueueLoad(false, getContext());
    }

    @NonNull
    @Override
    public List<RemoteActionCompat> getCommandActions(@NonNull Artwork artwork) {
        if (!running) {
            return Collections.emptyList();
        }
        List<RemoteActionCompat> list = new ArrayList<>();
        RemoteActionCompat shareAction = shareImage(artwork);
        if (shareAction != null) {
            list.add(shareAction);
        }
        RemoteActionCompat viewDetailsAction = viewArtworkDetailsAlternate(artwork);
        if (viewDetailsAction != null) {
            list.add(viewDetailsAction);
        }
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(checkContext());
        if (!sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "").isEmpty()) {
            RemoteActionCompat collectAction = addToBookmarks(artwork);
            if (collectAction != null) {
                list.add(collectAction);
            }
        }

        RemoteActionCompat deleteArtwork = deleteArtwork(artwork);
        if (deleteArtwork != null) {
            list.add(deleteArtwork);
        }
        return list;
    }

    @Nullable
    private RemoteActionCompat shareImage(Artwork artwork) {
        if (!running) {
            return null;
        }
        final Context context = checkContext();
        File newFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                artwork.getToken() + ".png");
        Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", newFile);
        Intent sharingIntent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .setType("image/*")
                .putExtra(Intent.EXTRA_STREAM, uri);

        String title = context.getString(R.string.command_shareImage);
        return new RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.ic_baseline_share_24),
                title,
                title,
                PendingIntent.getActivity(
                        context,
                        (int) artwork.getId(),
                        IntentUtils.chooseIntent(sharingIntent, SHARE_IMAGE_INTENT_CHOOSER_TITLE, context),
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
        );
    }

    @Nullable
    private RemoteActionCompat viewArtworkDetailsAlternate(Artwork artwork) {
        if (!running) {
            return null;
        }
        String token = artwork.getToken();
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        final Context context = checkContext();
        String title = context.getString(R.string.command_viewArtworkDetails);
        RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.muzei_launch_command),
                title,
                title,
                PendingIntent.getActivity(context,
                        (int) artwork.getId(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));
        remoteActionCompat.setShouldShowIcon(false);
        return remoteActionCompat;
    }

    @Nullable
    private RemoteActionCompat addToBookmarks(Artwork artwork) {
        if (!running) {
            return null;
        }
        Log.v("BOOKMARK", "adding to bookmarks");
        final Context context = checkContext();
        Intent addToBookmarkIntent = new Intent(context, AddToBookmarkService.class);
        addToBookmarkIntent.putExtra("artworkId", artwork.getToken());
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, addToBookmarkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        String label = context.getString(R.string.command_addToBookmark);
        RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.muzei_launch_command),
                label,
                label,
                pendingIntent);
        remoteActionCompat.setShouldShowIcon(false);
        return remoteActionCompat;
    }

    @Nullable
    private RemoteActionCompat deleteArtwork(Artwork artwork) {
        if (!running) {
            return null;
        }
        final Context context = checkContext();
        Intent deleteArtworkIntent = new Intent(context, DeleteArtworkReceiver.class);
        deleteArtworkIntent.putExtra("artworkId", artwork.getToken());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                0,
                deleteArtworkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        String title = "Delete current artwork";
        RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.ic_delete_white_24dp),
                title,
                title,
                pendingIntent
        );
        remoteActionCompat.setShouldShowIcon(false);
        return remoteActionCompat;
    }

    //<editor-fold desc="Deprecated in Muzei">

    @SuppressWarnings("deprecation")
    @Override
    @NonNull
    public List<UserCommand> getCommands(@NonNull Artwork artwork) {
        super.getCommands(artwork);
        if (!running) {
            return Collections.emptyList();
        }
        LinkedList<UserCommand> commands = new LinkedList<>();
        // Android 10 limits the ability for activities to run in the background
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Context context = checkContext();
            UserCommand addToBookmark = new UserCommand(COMMAND_ADD_TO_BOOKMARKS,
                    context.getString(R.string.command_addToBookmark));
            commands.add(addToBookmark);
            UserCommand openIntentImage = new UserCommand(COMMAND_VIEW_IMAGE_DETAILS,
                    context.getString(R.string.command_viewArtworkDetails));
            UserCommand shareImage = new UserCommand(COMMAND_SHARE_IMAGE,
                    context.getString(R.string.command_shareImage));
            commands.add(shareImage);
            commands.add(openIntentImage);
        }
        return commands;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCommand(@NonNull Artwork artwork, int id) {
        @Nullable final Context context = getContext();
        Handler handler = new Handler(Looper.getMainLooper());
        switch (id) {
            case COMMAND_ADD_TO_BOOKMARKS:
                Log.d("PIXIV_DEBUG", "addToBookmarks(): Entered");
                if (context == null) {
                    break;
                }
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "").isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, R.string.toast_loginFirst, Toast.LENGTH_SHORT).show());
                    return;
                }

                String accessToken;
                try {
                    accessToken = PixivMuzeiSupervisor.INSTANCE.getAccessToken();
                } catch (AccessTokenAcquisitionException e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, R.string.toast_loginFirst, Toast.LENGTH_SHORT).show());
                    return;
                }
                sendPostRequest(accessToken, artwork.getToken());
                Log.d("PIXIV_DEBUG", "Added to bookmarks");
                handler.post(() ->
                        Toast.makeText(context, R.string.toast_addingToBookmarks, Toast.LENGTH_SHORT).show());
                break;
            case COMMAND_VIEW_IMAGE_DETAILS:
                if (context == null) {
                    break;
                }
                String token = artwork.getToken();
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
                IntentUtils.launchActivity(context, intent);
                break;
            case COMMAND_SHARE_IMAGE:
                Log.d("ANTONY_WORKER", "Opening sharing ");
                if (context == null) {
                    break;
                }
                File newFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        artwork.getToken() + ".png");
                Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", newFile);
                Intent sharingIntent = new Intent();
                sharingIntent.setAction(Intent.ACTION_SEND);
                sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
                sharingIntent.setType("image/jpg");
                IntentUtils.launchActivity(context, sharingIntent);
            default:
        }
    }

    //</editor-fold>

    @Override
    @NonNull
    public InputStream openFile(@NonNull Artwork artwork) throws IOException {
        Objects.requireNonNull(artwork);

        Context context;
        try {
            context = checkContext();
        } catch (IllegalStateException ex) {
            throw new IOException("", ex);
        }

        final Uri artworkPersistentUri = artwork.getPersistentUri();
        if (artworkPersistentUri == null) {
            throw new IOException("Require non-null persistent uri in Artwork " + artwork);
        }

        InputStream inputStream = null;
        IOException exception = null;
        try {
            inputStream = context.getContentResolver().openInputStream(artworkPersistentUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            exception = e;
        }
        if (inputStream == null) {
            throw exception != null ? exception : new IOException("Fail to open stream with " + artworkPersistentUri);
        }
        return Objects.requireNonNull(inputStream);
    }

    /**
     * Return the {@link Context} this provider is running in.
     *
     * @throws IllegalStateException if not currently running after {@link #onCreate()}.
     */
    @NonNull
    private Context checkContext() {
        Context context = getContext();
        if (!running || context == null) {
            throw new IllegalStateException("Provider " + this + " not in running.");
        }
        return context;
    }

    // TODO: move from PixivArtService deprecated, to be refactor …
    // Function only used to add artworks to bookmarks on the old commands
    // this function to be removed, doesn't fit with anything other method
    private void sendPostRequest(String accessToken, String token) {
        HttpUrl rankingUrl = new HttpUrl.Builder()
                .scheme("https")
                .host("app-api.pixiv.net")
                .addPathSegments("v2/illust/bookmark/add")
                .build();
        RequestBody authData = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("illust_id", token)
                .addFormDataPart("restrict", "public")
                .build();
        Request request = new Request.Builder()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(authData)
                .url(rankingUrl)
                .build();

        OkHttpClient httpClient = null;
        if (Locale.getDefault().getISO3Language().equals("zho")) {
            Log.d(TAG, "Bypass in effect");
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.sslSocketFactory(new RubySSLSocketFactory(), new X509TrustManager() {
                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });//SNI bypass
            builder.hostnameVerifier((s, sslSession) -> true);//disable hostnameVerifier
            builder.addInterceptor(new NetworkTrafficLogInterceptor());
            builder.dns(new RubyHttpDns());//define the direct ip address
            httpClient = builder.build();
            /* SNI Bypass end */
        }

        if (httpClient == null) {
            return;
        }

        try {
            httpClient.newCall(request).execute();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
