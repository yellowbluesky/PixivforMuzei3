package com.antony.muzei.pixiv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderClient;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PixivArtWorker extends Worker
{
    private static final int LIMIT = 5;
    private static final String LOG_TAG = "PIXIV_DEBUG";

    private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};

    public PixivArtWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    static void enqueueLoad()
    {
        WorkManager manager = WorkManager.getInstance();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        WorkRequest request = new OneTimeWorkRequest.Builder(PixivArtWorker.class)
                .setConstraints(constraints)
                .build();
        manager.enqueue(request);

    }

    // Returns a string containing a valid access token
    // Otherwise returns an empty string if authentication failed or not possible
    private String getAccessToken()
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPrefs.edit();

        // If we possess an access token, AND it has not expired, instantly return it
        // Must be a divide by 1000, cannot be subtract 3600 * 1000
        String accessToken = sharedPrefs.getString("accessToken", "");
        if (!accessToken.isEmpty()
                && sharedPrefs.getLong("accessTokenIssueTime", 0)
                > (System.currentTimeMillis() / 1000) - 3600)
        {
            Log.i(LOG_TAG, "Existing access token found");
            return accessToken;
        }

        Log.i(LOG_TAG, "No access token, or access token expired");


        // If we did not have an access token or if it had expired, we proceed to build a request to acquire one
        Uri.Builder authQueryBuilder = new Uri.Builder()
                .appendQueryParameter("get_secure_url", Integer.toString(1))
                .appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
                .appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET);

        if (sharedPrefs.getString("refreshToken", "").isEmpty())
        {
            Log.i(LOG_TAG, "No refresh token found, proceeding with username / password authentication");
            authQueryBuilder.appendQueryParameter("grant_type", "password")
                    .appendQueryParameter("username", sharedPrefs.getString("pref_loginId", ""))
                    .appendQueryParameter("password", sharedPrefs.getString("pref_loginPassword", ""));
        } else
        {
            Log.i(LOG_TAG, "Refresh token found, using it to request an access token");
            authQueryBuilder.appendQueryParameter("grant_type", "refresh_token")
                    .appendQueryParameter("refresh_token", sharedPrefs.getString("refreshToken", ""));
        }

        // Now to actually send the auth GET request
        Uri authQuery = authQueryBuilder.build();
        try
        {
            Response response = sendPostRequest(PixivArtProviderDefines.OAUTH_URL, authQuery);
            JSONObject authResponseBody = new JSONObject(response.body().string());
            response.close();
            // Check if returned JSON indicates an error in authentication
            if (authResponseBody.has("has_error"))
            {
                Log.i(LOG_TAG, "Error authenticating, check username or password");
                editor.putString("pref_loginPassword", "");
                editor.putString("accessToken", "");
                editor.putString("refreshToken", "");
                editor.putString("storedCreds", "");
                editor.commit();
                return "";
            }

            // Authentication succeeded, storing tokens returned from Pixiv
            JSONObject tokens = authResponseBody.getJSONObject("response");
            editor.putString("accessToken", tokens.getString("access_token"));
            editor.putLong("accessTokenIssueTime", (System.currentTimeMillis() / 1000));
            editor.putString("refreshToken", tokens.getString("refresh_token"));
            editor.putString("userId", tokens.getJSONObject("user").getString("id"));
            editor.putString("deviceToken", tokens.getString("device_token"));

            editor.apply();

        } catch (IOException | JSONException ex)
        {
            ex.printStackTrace();
            return "";
        }
        return sharedPrefs.getString("accessToken", "");
    }

    // Only used for authentication in this application
    // Therefore all necessary headers are hardcoded in and not dynamically chosen
    private Response sendPostRequest(String url, Uri authQuery) throws IOException
    {
        String contentType = "application/x-www-form-urlencoded";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .build();

        RequestBody body = RequestBody.create(MediaType.parse(contentType), authQuery.toString());

        Request.Builder builder = new Request.Builder()
                .addHeader("User-Agent", "PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)")
                .addHeader("App-OS", "ios")
                .addHeader("App-OS-Version", "10.3.1")
                .addHeader("App-Version", "6.9.0")
                .addHeader("Content-type", body.contentType().toString())
                .post(body)
                .url(url);
        return httpClient.newCall(builder.build()).execute();
    }

    private String getUpdateUriInfo(String mode, String userId)
    {
        String urlString;
        switch (mode)
        {
            case "follow":
                urlString = PixivArtProviderDefines.FOLLOW_URL + "?restrict=public";
                break;
            case "bookmark":
                urlString = PixivArtProviderDefines.BOOKMARK_URL + "?user_id=" + userId + "&restrict=public";
                break;
            case "weekly_rank":
                urlString = PixivArtProviderDefines.WEEKLY_RANKING_URL;
                break;
            case "monthly_rank":
                urlString = PixivArtProviderDefines.MONTHLY_RANKING_URL;
                break;
            case "daily_rank":
            default:
                urlString = PixivArtProviderDefines.DAILY_RANKING_URL;
        }
        return urlString;
    }

    // authMode = true: auth Needed
    // TODO should there be a second overloaded method without auth features
    private Response sendGetRequest(String url, boolean authMode, String accessToken) throws IOException
    {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .build();

        Request.Builder builder = new Request.Builder();
        if (authMode)
        {
            builder.addHeader("User-Agent", "PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)")
                    .addHeader("App-OS", "ios")
                    .addHeader("App-OS-Version", "10.3.1")
                    .addHeader("App-Version", "6.9.0")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url(url);
        } else
        {
            builder.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0")
                    .addHeader("Referer", PixivArtProviderDefines.PIXIV_HOST)
                    .url(url);
        }
        return httpClient.newCall(builder.build()).execute();
    }

    // TODO Maybe mark this function as throwing exception
    // Downloads the selected image to cache folder on local storage
    // Cache folder is periodically pruned of its oldest images by Android
    private Uri downloadFile(Response response, String token) throws IOException
    {
        Context context = getApplicationContext();
        // Muzei does not care about file extensions
        // Only there to more easily allow local user to open them
        File downloadedFile = new File(context.getCacheDir(), token + ".png");
        FileOutputStream fileStream = new FileOutputStream(downloadedFile);
        InputStream inputStream = response.body().byteStream();
        final byte[] buffer = new byte[1024 * 50];
        int read;
        while ((read = inputStream.read(buffer)) > 0)
        {
            fileStream.write(buffer, 0, read);
        }
        fileStream.close();
        inputStream.close();
        response.body().close();

        return Uri.fromFile(downloadedFile);
    }

    // For ranking images, we are only provided with an illustration id
    // We require the correct file extension in order to pull the picture
    // So we cycle through all common file extensions until we get a good response
    private Response getRemoteFileExtension(String url) throws IOException
    {
        Response response;

        // All urls have predictable formats, so we can do simple substring replacement
        String uri0 = url
                .replace("c/240x480/", "")
                .replace("img-master", "img-original")
                .replace("_master1200", "");
        String uri1 = uri0.substring(0, uri0.length() - 4);

        for (String suffix : IMAGE_SUFFIXS)
        {
            String uri = uri1 + suffix;
            response = sendGetRequest(uri, false, "");
            if (response.code() == 200)
            {
                return response;
            }
        }
        return null;
    }

    // Filters through the JSON containing all the images
    // Picks one image based on user settings to show manga and NSFW
    // TODO more granular NSFW filtering (sanity_level)
    private JSONObject selectPicture(JSONObject overallJson)
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
        boolean showNsfw = sharedPrefs.getBoolean("pref_restrictMode", false);
        JSONObject pictureMetadata = null;
        boolean validImage = false;
        Random random = new Random();

        try
        {
            // If passed JSON was for feed or bookmark
            if (overallJson.has("illusts"))
            {
                JSONArray illusts = overallJson.getJSONArray("illusts");
                while (!validImage)
                {
                    // Random seems to be very inefficient, potentialyl visiting the same image multiple times
                    pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
                    // If user does not want manga to display
                    if (!showManga)
                    {
                        Log.d(LOG_TAG, "checking for no manga");
                        while (!pictureMetadata.getString("type").equals("illust"))
                        {
                            Log.d(LOG_TAG, "spinning for non manga");
                            pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
                        }
                    }
                    // If user does not want NSFW images to show
                    if (!showNsfw)
                    {
                        Log.d(LOG_TAG, "checking for R18");
                        while (pictureMetadata.getInt("x_restrict") != 0)
                        {
                            Log.d(LOG_TAG, "spinning for SFW");
                            pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
                        }
                    }
                    validImage = true;
                }
                // Else if passed JSON was from a ranking
            } else if (overallJson.has("contents"))
            {
                JSONArray contents = overallJson.getJSONArray("contents");
                pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
                while (!validImage)
                {
                    // If user does not want manga to display
                    if (!showManga)
                    {
                        Log.d(LOG_TAG, "checking for no manga");
                        while (pictureMetadata.getInt("illust_type") != 0)
                        {
                            Log.d(LOG_TAG, "spinning for non manga");
                            pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
                        }
                    }
                    // If user does not want NSFW images to show
                    if (!showNsfw)
                    {
                        Log.d(LOG_TAG, "Checking for R18");
                        while (pictureMetadata.getJSONObject("illust_content_type").getInt("sexual") != 0)
                        {
                            Log.d(LOG_TAG, "spinning for SFW");
                            pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
                        }
                    }
                    validImage = true;
                }
            }
        } catch (JSONException ex)
        {
            ex.printStackTrace();
            Log.w(LOG_TAG, pictureMetadata.toString());
        }
        return pictureMetadata;
    }

    @NonNull
    @Override
    public Result doWork()
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String mode = sharedPrefs.getString("pref_updateMode", "");
        Log.d(LOG_TAG, "Mode: " + mode);
        JSONObject overallJson = null, pictureMetadata;
        String title, byline, token, imageUrl, accessToken = "";
        Response response, rankingResponse;
        Uri finalUri;

        // Gets an access token if required
        // If the process failed in any way, then change modes to daily_rank
        // Really not happy about the many if statements checking the same thing
        if (mode.equals("follow") || mode.equals("bookmark"))
        {
            accessToken = getAccessToken();
            if (accessToken.isEmpty())
            {
                // Is the permanent change acceptable?
                // Should chuck it into a textView on the activity
                Log.i(LOG_TAG, "Authentication failed, switching to Daily Ranking");
                sharedPrefs.edit().putString("pref_updateMode", "daily_rank").apply();
                mode = "daily_rank";
            }
        }

        try
        {
            // This is a mess
            rankingResponse = sendGetRequest(
                    getUpdateUriInfo(mode, sharedPrefs.getString("userId", "")),
                    mode.equals("follow") || mode.equals("bookmark"),
                    accessToken
            );

            // If HTTP code was anything other than 200 ... 301, failure
            if (!rankingResponse.isSuccessful())
            {
                Log.e(LOG_TAG, "HTTP error: " + rankingResponse.code());
                JSONObject errorBody = new JSONObject(rankingResponse.body().string());
                Log.e(LOG_TAG, errorBody.toString());
                Log.e(LOG_TAG, "Could not get overall ranking JSON");
                rankingResponse.close();
                return Result.failure();
            }

            overallJson = new JSONObject((rankingResponse.body().string()));
            rankingResponse.close();
            pictureMetadata = selectPicture(overallJson);

            if (mode.equals("follow") || mode.equals("bookmark"))
            {
                Log.d(LOG_TAG, "Feed or bookmark");
                title = pictureMetadata.getString("title");
                byline = pictureMetadata.getJSONObject("user").getString("name");
                token = pictureMetadata.getString("id");

                // If picture pulled is a single image
                if (pictureMetadata.getJSONArray("meta_pages").length() == 0)
                {
                    Log.d(LOG_TAG, "Picture is a single image");
                    imageUrl = pictureMetadata
                            .getJSONObject("meta_single_page")
                            .getString("original_image_url");
                }
                // Otherwise we have pulled an album, picking the first picture in album
                else
                {
                    Log.d(LOG_TAG, "Picture is part of an album");
                    imageUrl = pictureMetadata
                            .getJSONArray("meta_pages")
                            .getJSONObject(0)
                            .getJSONObject("image_urls")
                            .getString("original");
                }
                response = sendGetRequest(imageUrl, false, "");
            } else
            {
                Log.d(LOG_TAG, "Ranking");
                title = pictureMetadata.getString("title");
                byline = pictureMetadata.getString("user_name");
                token = pictureMetadata.getString("illust_id");
                String thumbUrl = pictureMetadata.getString("url");
                response = getRemoteFileExtension(thumbUrl);
            }
            finalUri = downloadFile(response, token);
        } catch (IOException | JSONException ex)
        {
            Log.d(LOG_TAG, "error");
            Log.d(LOG_TAG, overallJson.toString());
            ex.printStackTrace();
            return Result.failure();
        }

        response.close();
        ProviderClient client =
                ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class);
        final Artwork artwork = new Artwork.Builder()
                .title(title)
                .byline(byline)
                .persistentUri(finalUri)
                .token(token)
                .webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
                .build();
        client.addArtwork(artwork);
        return Result.success();
    }
}
