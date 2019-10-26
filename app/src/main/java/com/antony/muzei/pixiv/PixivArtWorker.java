/*
    This file is part of PixivforMuzei3.

    PixivforMuzei3 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program  is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.antony.muzei.pixiv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
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
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import static com.antony.muzei.pixiv.PixivArtProviderDefines.HASH_SECRET;

public class PixivArtWorker extends Worker
{
	private static final String LOG_TAG = "PIXIV_DEBUG";
	private static final String WORKER_TAG = "PIXIV";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};
	private static boolean clearArtwork = false;
	private final OkHttpClient httpClient;

	public PixivArtWorker(
			@NonNull Context context,
			@NonNull WorkerParameters params)
	{
		super(context, params);

		/* SNI Bypass begin */
		HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(
				s -> Log.v("aaa", "message====" + s));

		httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
		OkHttpClient.Builder builder = new OkHttpClient.Builder();

		builder.sslSocketFactory(new RubySSLSocketFactory(), new X509TrustManager()
		{
			@Override
			public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
			{

			}

			@Override
			public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
			{

			}

			@Override
			public X509Certificate[] getAcceptedIssuers()
			{
				return new X509Certificate[0];
			}
		});//SNI bypass
		builder.hostnameVerifier(new HostnameVerifier()
		{
			@Override
			public boolean verify(String s, SSLSession sslSession)
			{
				return true;
			}
		});//disable hostnameVerifier
		builder.addInterceptor(httpLoggingInterceptor);
		builder.dns(new RubyHttpDns());//define the direct ip address
		httpClient = builder.build();
		/* SNI Bypass end */
	}

	static void enqueueLoad(boolean clear)
	{
		if (clear)
		{
			clearArtwork = true;
		}
		WorkManager manager = WorkManager.getInstance();
		Constraints constraints = new Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build();
		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PixivArtWorker.class)
				.setConstraints(constraints)
				.addTag(WORKER_TAG)
				.build();
		manager.enqueueUniqueWork(WORKER_TAG, ExistingWorkPolicy.APPEND, request);
	}

	// Returns a string containing a valid access token
	// Otherwise returns an empty string if authentication failed or not possible
	private String getAccessToken()
	{
		Log.d(LOG_TAG, "getAccessToken(): Entered");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// If we possess an access token, AND it has not expired, instantly return it
		// Must be a divide by 1000, cannot be subtract 3600 * 1000 for some reason
		String accessToken = sharedPrefs.getString("accessToken", "");
		long accessTokenIssueTime = sharedPrefs.getLong("accessTokenIssueTime", 0);
		if (!accessToken.isEmpty() && accessTokenIssueTime > (System.currentTimeMillis() / 1000) - 3600)
		{
			Log.i(LOG_TAG, "Existing access token found, using it");
			Log.d(LOG_TAG, "getAccessToken(): Exited");
			return accessToken;
		}

		Log.i(LOG_TAG, "Access token expired or non-existent, proceeding to acquire a new access token");

		try
		{
			Response response;
			if (sharedPrefs.getString("refreshToken", "").isEmpty())
			{
				Log.i(LOG_TAG, "Using username and password to acquire an access token");
				String loginId = sharedPrefs.getString("pref_loginId", "");
				String loginPassword = sharedPrefs.getString("pref_loginPassword", "");
				response = authLogin(loginId, loginPassword);
			} else
			{
				Log.i(LOG_TAG, "Using refresh token to acquire an access token");
				String refreshToken = sharedPrefs.getString("refreshToken", "");
				response = authRefreshToken(refreshToken);
			}
			JSONObject authResponseBody = new JSONObject(response.body().string());
			response.close();

			if (authResponseBody.has("has_error"))
			{
				Log.i(LOG_TAG, "Error authenticating, check username or password");
				// Clearing loginPassword is a hacky way to alerting to the user that their credentials do not work
				sharedPrefs.edit().putString("pref_loginPassword", "").apply();
				return "";
			}

			// Authentication succeeded, storing tokens returned from Pixiv
			//Log.d(LOG_TAG, authResponseBody.toString());
//            Uri profileImageUri = storeProfileImage(authResponseBody.getJSONObject("response"));
//            sharedPrefs.edit().putString("profileImageUri", profileImageUri.toString()).apply();
			storeTokens(authResponseBody.getJSONObject("response"));
		} catch (IOException | JSONException ex)
		{
			ex.printStackTrace();
			Log.d(LOG_TAG, "getAccessToken(): Exited with error");
			return "";
		}
		Log.d(LOG_TAG, "Acquired access token");
		Log.d(LOG_TAG, "getAccessToken(): Exited");
		return sharedPrefs.getString("accessToken", "");
	}

	// Acquires an access token and refresh token from a username / password pair
	// Returns a response containing an error or the tokens
	// It is up to the caller to handle any errors
	private Response authLogin(String loginId, String loginPassword) throws IOException
	{
		Uri authQuery = new Uri.Builder()
				.appendQueryParameter("get_secure_url", Integer.toString(1))
				.appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
				.appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET)
				.appendQueryParameter("grant_type", "password")
				.appendQueryParameter("username", loginId)
				.appendQueryParameter("password", loginPassword)
				.build();

		return sendPostRequest(authQuery);
	}

    /*
        AUTH
     */

	// Acquire an access token from an existing refresh token
	// Returns a response containing an error or the tokens
	// It is up to the caller to handle any errors
	private Response authRefreshToken(String refreshToken) throws IOException
	{
		Uri authQuery = new Uri.Builder()
				.appendQueryParameter("get_secure_url", Integer.toString(1))
				.appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
				.appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET)
				.appendQueryParameter("grant_type", "refresh_token")
				.appendQueryParameter("refresh_token", refreshToken)
				.build();

		return sendPostRequest(authQuery);
	}

//    private Uri storeProfileImage(JSONObject response) throws JSONException, java.io.IOException
//    {
//        String profileImageUrl = response
//                .getJSONObject("user")
//                .getJSONObject("profile_image_urls")
//                .getString("px_170x170");
//
//        return downloadFile(sendGetRequestAuth(profileImageUrl), "profile.png");
//    }

	// Upon successful authentication this function stores tokens returned from Pixiv into device memory
	private void storeTokens(JSONObject tokens) throws JSONException
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putString("accessToken", tokens.getString("access_token"));
		editor.putLong("accessTokenIssueTime", (System.currentTimeMillis() / 1000));
		editor.putString("refreshToken", tokens.getString("refresh_token"));
		editor.putString("userId", tokens.getJSONObject("user").getString("id"));
		// Not yet tested, but I believe that this needs to be a commit() and not an apply()
		// Muzei queues up many picture requests at one. Almost all of them will not have an access token to use
		editor.commit();
	}

    /*
        NETWORK
     */

	// This function is used when authentication via an access token is required
	private Response sendGetRequestAuth(HttpUrl url, String accessToken) throws IOException
	{
		Request request = new Request.Builder()
				.addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
				.addHeader("App-OS", PixivArtProviderDefines.APP_OS)
				.addHeader("App-OS-Version", PixivArtProviderDefines.APP_OS_VERSION)
				.addHeader("App-Version", PixivArtProviderDefines.APP_VERSION)
				.addHeader("Authorization", "Bearer " + accessToken)
				.get()
				.url(url)
				.build();
		return httpClient.newCall(request).execute();
	}

	// This function is used when authentication is not required
	private Response sendGetRequestRanking(HttpUrl url) throws IOException
	{
		Request request = new Request.Builder()
				.addHeader("User-Agent", PixivArtProviderDefines.BROWSER_USER_AGENT)
				.addHeader("Referer", PixivArtProviderDefines.PIXIV_HOST)
				.get()
				.url(url)
				.build();

		return httpClient.newCall(request).execute();
	}

	private Response sendHeadRequest(String url)
	{
		Request request = new Request.Builder().url(url).head().build();
		return null;
	}

	// ranking
	private long getRemoteFileSize(String url) throws IOException
	{
		// get only the head not the whole file
		Request request = new Request.Builder()
				.url(url)
				.head()
				.build();
		Response response = httpClient.newCall(request).execute();
		// OKHTTP put the length from the header here even though the body is empty
		long size = response.body().contentLength();
		return size;
	}

	// Sends an authentication request, and returns a Response
	// The Response is decoded by the caller; it contains authentication tokens or an error
	// this method is called by only one method, so all values are hardcoded
	private Response sendPostRequest(Uri authQuery) throws IOException
	{
		String contentType = "application/x-www-form-urlencoded";
		RequestBody body = RequestBody.create(MediaType.parse(contentType), authQuery.toString());

		String rfc3339Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
		String concatSecret = rfc3339Date + HASH_SECRET;
		String hashedSecret = "";
		try
		{
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(concatSecret.getBytes());
			byte[] messageDigest = digest.digest();
			StringBuilder hexString = new StringBuilder();
			// this loop is horrifically inefficient on CPU and memory
			// but is only executed once to acquire a new access token
			// i.e. at most once per hour
			for (byte aMessageDigest : messageDigest)
			{
				String h = Integer.toHexString(0xFF & aMessageDigest);
				while (h.length() < 2)
				{
					h = "0" + h;
				}
				hexString.append(h);
			}
			hashedSecret = hexString.toString();
		} catch (java.security.NoSuchAlgorithmException ex)
		{
			ex.printStackTrace();
		}

		Request request = new Request.Builder()
				//.addHeader("host", "oauth.secure.pixiv.net")
				.addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
				//.addHeader("App-OS", PixivArtProviderDefines.APP_OS)
				//.addHeader("App-OS-Version", PixivArtProviderDefines.APP_OS_VERSION)
				//.addHeader("App-Version", PixivArtProviderDefines.APP_VERSION)
				//.addHeader("Content-type", body.contentType().toString())
				.addHeader("x-client-time", rfc3339Date)
				.addHeader("x-client-hash", hashedSecret)
				.post(body)
				.url(PixivArtProviderDefines.OAUTH_URL)
				.build();
		return httpClient.newCall(request).execute();
	}

	// feed or bookmark
	private long getRemoteFileSize(String url, String accessToken) throws IOException
	{
		// get only the head not the whole file
		Request request = new Request.Builder()
				.url(url)
				.head()
				.build();
		Response response = httpClient.newCall(request).execute();
		// OKHTTP put the length from the header here even though the body is empty
		long size = response.body().contentLength();
		return size;
	}

	// Downloads the selected image to cache folder on local storage
	// Cache folder is periodically pruned of its oldest images by Android
	private Uri downloadFile(Response response, String filename) throws IOException
	{
		Log.d(LOG_TAG, "Downloading file");
		Context context = getApplicationContext();
		// Muzei does not care about file extensions
		// Only there to more easily allow local user to open them
		File downloadedFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename + ".png");
		FileOutputStream fileStream = new FileOutputStream(downloadedFile);
		InputStream inputStream = response.body().byteStream();
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = inputStream.read(buffer)) != -1)
		{
			fileStream.write(buffer, 0, read);
		}
		fileStream.close();
		inputStream.close();

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
			response = sendGetRequestRanking(HttpUrl.parse(uri));
			if (response.code() == 200)
			{
				return response;
			} else
			{
				response.close();
			}
		}
		return null;
	}

	/*
        RANKING
    */

	private Artwork getArtworkRanking(String mode) throws IOException, JSONException
	{
		Log.d(LOG_TAG, "getArtworkRanking(): Entering");
		HttpUrl.Builder rankingUrlBuilder = new HttpUrl.Builder()
				.scheme("https")
				.host("www.pixiv.net")
				.addPathSegment("ranking.php")
				.addQueryParameter("format", "json");
		HttpUrl rankingUrl = null;
		String attribution = null;
		switch (mode)
		{
			case "daily_rank":
				rankingUrl = rankingUrlBuilder
						.addQueryParameter("mode", "daily")
						.build();
				attribution = "Daily Ranking #";
				break;
			case "weekly_rank":
				rankingUrl = rankingUrlBuilder
						.addQueryParameter("mode", "weekly")
						.build();
				attribution = "Weekly Ranking #";
				break;
			case "monthly_rank":
				rankingUrl = rankingUrlBuilder
						.addQueryParameter("mode", "monthly")
						.build();
				attribution = "Monthly Ranking #";
				break;
		}
		Response rankingResponse = sendGetRequestRanking(rankingUrl);

		JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
		rankingResponse.close();
		JSONObject pictureMetadata = filterRanking(overallJson.getJSONArray("contents"));

		String title = pictureMetadata.getString("title");
		String byline = pictureMetadata.getString("user_name");
		String token = pictureMetadata.getString("illust_id");
		attribution += pictureMetadata.get("rank");
		Response remoteFileExtension = getRemoteFileExtension(pictureMetadata.getString("url"));
		Uri localUri = downloadFile(remoteFileExtension, token);
		remoteFileExtension.close();
		Log.d(LOG_TAG, "getArtworkRanking(): Exited");

		return new Artwork.Builder()
				.title(title)
				.byline(byline)
				.attribution(attribution)
				.persistentUri(localUri)
				.token(token)
				.webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
				.build();
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
	private JSONObject filterRanking(JSONArray contents) throws JSONException
	{
		Log.d(LOG_TAG, "filterRanking(): Entering");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
		int nsfwFilteringLevel = Integer.parseInt(sharedPrefs.getString("pref_nsfwFilterLevel", "2"));
		JSONObject pictureMetadata;
		Random random = new Random();

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
		if (nsfwFilteringLevel < 4)
		{
			Log.d(LOG_TAG, "Checking NSFW level of pulled picture");
			while (pictureMetadata.getJSONObject("illust_content_type").getInt("sexual") != 0)
			{
				Log.d(LOG_TAG, "Pulled picture is NSFW, retrying");
				pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
			}
		}
		Log.d(LOG_TAG, "Exited selecting ranking");
		return pictureMetadata;
	}

    /*
        RANKING
     */

	/*
		Method that submits the Artwork object for inclusion in the ContentProvider
	 */
	private Artwork getArtworkFeedBookmarkTag(String mode, String accessToken) throws IOException, JSONException
	{
		Log.d(LOG_TAG, "getArtworkFeedBookmarkTag(): Entering");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Builds the API URL to call depending on chosen update mode
		HttpUrl feedBookmarkTagUrl = null;
		HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
				.scheme("https")
				.host("app-api.pixiv.net");
		switch (mode)
		{
			case "follow":
				feedBookmarkTagUrl = urlBuilder
						.addPathSegments("v2/illust/follow")
						.addQueryParameter("user_id", sharedPrefs.getString("userId", ""))
						.addQueryParameter("restrict", "public")
						.build();
				break;
			case "bookmark":
				feedBookmarkTagUrl = urlBuilder
						.addPathSegments("v1/user/bookmarks/illust")
						.addQueryParameter("user_id", sharedPrefs.getString("userId", ""))
						.addQueryParameter("restrict", "public")
						.build();
				break;
			case "tag_search":
				feedBookmarkTagUrl = urlBuilder
						.addPathSegments("v1/search/illust")
						.addQueryParameter("word", sharedPrefs.getString("pref_artistId", ""))
						.addQueryParameter("search_target", "partial_match_for_tags")
						.addQueryParameter("sort", "date_desc")
						.addQueryParameter("filter", "for_ios")
						.build();
				break;
			case "artist":
				feedBookmarkTagUrl = urlBuilder
						.addPathSegments("v1/user/illusts")
						.addQueryParameter("user_id", sharedPrefs.getString("pref_artistId", ""))
						.addQueryParameter("filter", "for_ios")
						.build();
		}

		Response rankingResponse = sendGetRequestAuth(feedBookmarkTagUrl, accessToken);

		JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
		rankingResponse.close();
		Log.v(LOG_TAG, overallJson.toString());
		JSONObject pictureMetadata = filterFeedBookmarkTag(overallJson.getJSONArray("illusts"));

		String title = pictureMetadata.getString("title");
		String byline = pictureMetadata.getJSONObject("user").getString("name");
		String token = pictureMetadata.getString("id");

		// Different logic if the image pulled is a single image or an album
		// If album, we use the first picture
		String imageUrl;
		if (pictureMetadata.getJSONArray("meta_pages").length() == 0)
		{
			Log.d(LOG_TAG, "Picture is a single image");
			imageUrl = pictureMetadata
					.getJSONObject("meta_single_page")
					.getString("original_image_url");
		} else
		{
			Log.d(LOG_TAG, "Picture is part of an album");
			imageUrl = pictureMetadata
					.getJSONArray("meta_pages")
					.getJSONObject(0)
					.getJSONObject("image_urls")
					.getString("original");
		}

		Response imageDataResponse = sendGetRequestRanking(HttpUrl.parse(imageUrl));
		Uri localUri = downloadFile(imageDataResponse, token);
		imageDataResponse.close();
		Log.d(LOG_TAG, "getArtworkFeedBookmarkTag(): Exited");
		return new Artwork.Builder()
				.title(title)
				.byline(byline)
				.persistentUri(localUri)
				.token(token)
				.webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
				.build();
	}

	/*
		Called by getArtworkFeedBookmarkTag to return details about an artwork that complies with
		filtering restrictions set by the user
	 */
	private JSONObject filterFeedBookmarkTag(JSONArray illusts) throws JSONException
	{
		Log.d(LOG_TAG, "filterFeedBookmarkTag(): Entering");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);

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
		int nsfwFilteringLevel = Integer.parseInt(sharedPrefs.getString("pref_nsfwFilterLevel", "2"));
		Log.d(LOG_TAG, "NSFW filter level set to: " + nsfwFilteringLevel);
		if (nsfwFilteringLevel < 8)
		{
			Log.d(LOG_TAG, "Performing some level of NSFW filtering");
			// Allowing all sanity_level and filtering only x_restrict tagged pictures
			if (nsfwFilteringLevel == 6)
			{
				Log.d(LOG_TAG, "Allowing all but x_restrict, checking");
				while (pictureMetadata.getInt("x_restrict") != 0)
				{
					Log.d(LOG_TAG, "Retrying for a non x_restrict");
					pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
				}
			} else
			{
				int nsfwLevel = pictureMetadata.getInt("sanity_level");
				Log.d(LOG_TAG, "Sanity level of pulled picture is: " + nsfwLevel);
				// If it's equal it's ok
				while (nsfwLevel > nsfwFilteringLevel)
				{
					Log.d(LOG_TAG, "Pulled picture exceeds set filtering level, retrying");
					pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));
					nsfwLevel = pictureMetadata.getInt("sanity_level");
				}
			}
		}

		return pictureMetadata;
	}

	/*
		First method to be called
		Sets program flow into ranking or feed/bookmark paths
		Also acquires an access token to be passed into getArtworkFeedBookmarkTag()
			Why is this function the one acquiring the access token?
	 */
	private Artwork getArtwork()
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String mode = sharedPrefs.getString("pref_updateMode", "daily_rank");
		Log.d(LOG_TAG, "Display mode: " + mode);
		Artwork artwork = null;
		String accessToken = "";

		// These modes require an access token, so we check for and acquire one
		if (mode.equals("follow") || mode.equals("bookmark") || mode.equals("tag_search") || mode.equals("artist"))
		{
			accessToken = getAccessToken();
			if (accessToken.isEmpty())
			{
				Handler handler = new Handler(Looper.getMainLooper());
				String authFailMode = sharedPrefs.getString("pref_authFailAction", "changeDaily");
				switch (authFailMode)
				{
					case "changeDaily":
						Log.d(LOG_TAG, "Auth failed, changing mode to daily");
						sharedPrefs.edit().putString("pref_updateMode", "daily_rank").apply();
						mode = "daily_ranking";
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedSwitch, Toast.LENGTH_SHORT).show());
						break;
					case "doNotChange_downDaily":
						Log.d(LOG_TAG, "Auth failed, downloading a single daily");
						mode = "daily_ranking";
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedDown, Toast.LENGTH_SHORT).show());
						break;
					case "doNotChange_doNotDown":
						Log.d(LOG_TAG, "Auth failed, retrying with no changes");
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedRetry, Toast.LENGTH_SHORT).show());
						return null;
				}
			}
		}

		try
		{
			if (mode.equals("follow") || mode.equals("bookmark") || mode.equals("tag_search") || mode.equals("artist"))
			{
				artwork = getArtworkFeedBookmarkTag(mode, accessToken);
			} else
			{
				artwork = getArtworkRanking(mode);
			}
		} catch (IOException | JSONException ex)
		{
			ex.printStackTrace();
		}
		return artwork;
	}

	/*
		On any error/exception in the program (JSONException, network error, cannot access storage)
		this method will return true/artwork is null.
		Without this function, null Artwork's would have been added to the ContentProvider, resulting
		in app freezing until storage was cleared.
	 */
	private boolean isArtworkNull(Artwork artwork)
	{
		if (artwork == null)
		{
			Log.e(LOG_TAG, "Null artwork returned, retrying at later time");
			return true;
		}
		return false;
	}

	@NonNull
	@Override
	public Result doWork()
	{
		ProviderClient client = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class);
		Log.d(LOG_TAG, "Starting work");

		if (!clearArtwork)
		{
			Artwork artwork = getArtwork();
			if (isArtworkNull(artwork))
			{
				return Result.retry();
			}
			client.addArtwork(artwork);
		} else
		{
			clearArtwork = false;
			ArrayList<Artwork> artworkArrayList = new ArrayList<Artwork>();
			for (int i = 0; i < 3; i++)
			{
				Artwork artwork = getArtwork();
				if (isArtworkNull(artwork))
				{
					client.setArtwork(artworkArrayList);
					return Result.retry();
				}
				artworkArrayList.add(artwork);
			}
			client.setArtwork(artworkArrayList);
		}

		return Result.success();
	}

	enum appStatus
	{
		IDLE,
		NETWORK_POST,
		GET_RANKING_JSON,
		FILTER_RANKING,
		GET_FILE_EXTENSION,
		GET_FEED_JSON,
		FILTER_FEED,
		NETWORK_GET,
		ADDING_ARTWORK,
		DOWNLOADING,
	}
}
