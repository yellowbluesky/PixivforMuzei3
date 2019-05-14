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

        Log.i(LOG_TAG, "No access token or access token expired, proceeding to acquire a new access token");


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
            Log.i(LOG_TAG, "Refresh token found, proceeding to use it");
            authQueryBuilder.appendQueryParameter("grant_type", "refresh_token")
                    .appendQueryParameter("refresh_token", sharedPrefs.getString("refreshToken", ""));
        }

        // Now to actually send the auth POST request
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
                // Clearing loginPassword is a hacky way to alerting to the user that their crdentials do not work
                editor.putString("pref_loginPassword", "");
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

    // Sends an authentication request, and returns a Response
    // The Response is decoded by the caller; it contains authentication tokens or an error
    // this method is called by only one method, so all values are hardcoded
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

    // Returns the requested Pixiv API endpoint as a String
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
    private Response sendGetRequest(String url, String accessToken) throws IOException
    {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .build();

        Request.Builder builder = new Request.Builder();

        builder.addHeader("User-Agent", "PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)")
                .addHeader("App-OS", "ios")
                .addHeader("App-OS-Version", "10.3.1")
                .addHeader("App-Version", "6.9.0")
                .addHeader("Authorization", "Bearer " + accessToken)
                .url(url);
        return httpClient.newCall(builder.build()).execute();
    }

    private Response sendGetRequest(String url) throws IOException
    {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .build();

        Request.Builder builder = new Request.Builder()
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0")
                .addHeader("Referer", PixivArtProviderDefines.PIXIV_HOST)
                .url(url);

        return httpClient.newCall(builder.build()).execute();
    }

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

    /*
    Ranking images are only provided with an illustration id
    We require the correct file extension in order to successfully pull the picture
    This method cycles though all possible file extensions until a good response is received
        i.e. a response that is not a 400 error
    Returns a Response whose body contains the picture selected to be downloaded
    */
    private Response getRemoteFileExtension(String url) throws IOException
    {
        Response response;

        // All urls have predictable formats, so we can simply do substring replacement
        String uri0 = url
                .replace("c/240x480/", "")
                .replace("img-master", "img-original")
                .replace("_master1200", "");
        String uri1 = uri0.substring(0, uri0.length() - 4);

        for (String suffix : IMAGE_SUFFIXS)
        {
            String uri = uri1 + suffix;
            response = sendGetRequest(uri);
            if (response.code() == 200)
            {
                return response;
            }
        }
        return null;
    }

    /*
    Filters through the JSON containing the metadata of the pictures of the selected mode
    Picks one image based on the user's setting to show manga and level of NSFW filtering

    Regarding feed and bookmarks
        For NSFW filtering the two relevant JSON strings are "sanity_level" and "x_restrict"
            sanity_level
                2 -> Completely SFW
                4 -> Moderately ecchi e.g. beach bikinis, slight upskirts
                6 -> Very ecchi e.g. more explicit and suggestive themes
             x_restrict
                1 -> R18 e.g. nudity and penetration

            In this code x_restrict is treated as a level 8 sanity_level

        For manga filtering, the value of the "type" string is checked for either "manga" or "illust"

    Regarding rankings
        NSFW filtering is performed by checking the value of the "sexual" JSON string
        Manga filtering is performed by checking the value of the "illust_type" JSON string


    */
    // TODO also split this function up
    private JSONObject selectPictureAuth(JSONObject illusts)
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
        int nsfwFilterLevel = Integer.parseInt(sharedPrefs.getString("pref_nsfwFilterLevel", "0"));
        Log.i(LOG_TAG, "NSFW filter level set to: " + nsfwFilterLevel);
        Random random = new Random();

                    // Random seems to be very inefficient, potentially visiting the same image multiple times
        JSONObject pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));

            // If user does not want manga to display
        if (!showManga)
        {
            Log.d(LOG_TAG, "Manga not desired");
            while (!pictureMetadata.getString("type").equals("illust"))
            {
                Log.d(LOG_TAG, "Retrying for a non-manga");
                pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
            }
        }
        // If user does not want NSFW images to show
        // If the filtering level is 8, the user has selected to disable ALL filtering, i.e. allow R18
        // TODO this can be made better
        if (nsfwFilterLevel < 8)
        {
            Log.d(LOG_TAG, "Performing some level of NSFW filtering");
            // Allowing all sanity_level and filtering only x_restrict tagged pictures
            if (nsfwFilterLevel == 6)
            {
                Log.d(LOG_TAG, "Checking for x_restrict");
                while (pictureMetadata.getInt("x_restrict") != 0)
                {
                    Log.d(LOG_TAG, "Retrying for a non x_restrict");
                    pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
                }
            } else
            {
                int nsfwLevel = pictureMetadata.getInt("sanity_level");
                Log.d(LOG_TAG, "Filtering level set to: " + nsfwLevel + ",checking");
                while (nsfwLevel > nsfwFilterLevel)
                {
                    Log.d(LOG_TAG, "Pulled picture exceeds set filter level, retrying");
                    pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
                    nsfwLevel = pictureMetadata.getInt("sanity_level");
                }
            }
        }

        return pictureMetadata;
    }

    private JSONObject selectPictureRanking(JSONObject contents) throws JSONException
    {
        Log.d(LOG_TAG, "Selecting ranking");
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
        int nsfwFilterLevel = Integer.parseInt(sharedPrefs.getString("pref_nsfwFilterLevel", "0"));
        Log.i(LOG_TAG, "NSFW filtering is " + (nsfwFilterLevel > 2 : "true" ? "false"));
        JSONObject pictureMetadata = null;
        Random random = new Random();

        JSONArray contents = overallJson.getJSONArray("contents");
        pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
        // If user does not want manga to display
        if (!showManga)
        {
            Log.d(LOG_TAG, "Manga not desired");
            while (pictureMetadata.getInt("illust_type") != 0)
            {
                Log.d(LOG_TAG, "Retrying for a non-manga");
                pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
            }
        }
        // If user does not want NSFW images to show
        if (nsfwFilterLevel > 2)
        {
            Log.d(LOG_TAG, "Checking NSFW level of pulled picture");
            while (pictureMetadata.getJSONObject("illust_content_type").getInt("sexual") != 0)
            {
                Log.d(LOG_TAG, "pulled picture is NSFW, retrying");
                pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
            }
        }
        Log.d(LOG_TAG, "Exited selecting ranking")
        return pictureMetadata;
    }

    private Artwork getPictureRanking(String mode) throws IOException, JSONException
    {
        Log.d(LOG_TAG, "Getting ranking");
        Response rankingResponse = sendGetRequest(getUpdateUriInfo(mode, ""));

        JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
        rankingResponse.close();
        JSONObject pictureMetadata = selectPictureRanking(overallJson);

        String title = pictureMetadata.getString("title");
        String byline = pictureMetadata.getString("user_name");
        String token = pictureMetadata.getString("illust_id");
        Uri localUri = downloadFile(
                getRemoteFileExtension(pictureMetadata.getString("url")),
                token
        );
        Log.d(LOG_TAG, "Exited getting ranking");

        final Artwork artwork = new Artwork.Builder()
                .title(title)
                .byline(byline)
                .persistentUri(localUri)
                .token(token)
                .webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
                .build();
        return artwork;
    }

    private Artwork getPictureFeedOrBookmark(String mode, String accessToken) throws IOException, JSONException
    {
        Log.d(LOG_TAG, "Getting feed or bookmark");
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Response rankingResponse = sendGetRequest(getUpdateUriInfo(mode, sharedPrefs.getString("userId", "")), accessToken);

        JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
        rankingResponse.close();
        JSONObject pictureMetadata = selectPictureAuth(overallJson.getJSONArray("illusts"));

        String title = pictureMetadata.getString("title");
        String byline = pictureMetadata.getJSONObject("user").getString("name");
        String token = pictureMetadata.getString("id");

        // Picture pulled is a single
        String imageUrl;
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
        Response imageDataResponse = sendGetRequest(imageUrl);
        Uri localUri = downloadFile(imageDataResponse, token);
        imageDataResponse.close();
        Log.d(LOG_TAG, "Exited getting feed or bookmark");
        final Artwork artwork = new Artwork.Builder()
                .title(title)
                .byline(byline)
                .persistentUri(localUri)
                .token(token)
                .webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
                .build();
        return artwork;
    }

    @NonNull
    @Override
    public Result doWork()
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String mode = sharedPrefs.getString("pref_updateMode", "");
        Log.d(LOG_TAG, "Display mode: " + mode);
        Artwork artwork;
        String accessToken = "";

        if (mode.equals("follow") || mode.equals("bookmark"))
        {
            accessToken = getAccessToken();
            if (accessToken.isEmpty())
            {
                // TODO somehow make this Log be a toast message
                Log.i(LOG_TAG, "Authentication failed, switching update mode to daily ranking");
                sharedPrefs.edit().putString("pref_updateMode", "daily_rank").apply();
                mode = "daily_ranking";
            }
        }

        try
        {
            if (mode.equals("follow") || mode.equals("bookmark"))
            {
                artwork = getPictureFeedOrBookmark(mode, accessToken);
            } else
            {
                artwork = getPictureRanking(mode);
            }
        } catch (IOException | JSONException ex)
        {
            ex.printStackTrace();
            return Result.failure();
        }

        ProviderClient client = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class);
        client.addArtwork(artwork);
        return Result.success();
    }
}
