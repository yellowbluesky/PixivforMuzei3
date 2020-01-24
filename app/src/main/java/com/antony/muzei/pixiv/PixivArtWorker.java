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

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class PixivArtWorker extends Worker
{
	private static final String LOG_TAG = "PIXIV_DEBUG";
	private static final String WORKER_TAG = "PIXIV";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};
	private static boolean clearArtwork = false;

	public PixivArtWorker(
			@NonNull Context context,
			@NonNull WorkerParameters params)
	{
		super(context, params);
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
		// Must be a uniqueWork
		// If not Muzei will queue MANY at once on initial load
		// This is good for saturating a network link and for fast picture downloads
		// However, race conditions develop if work required is authenticated
		// uniqueue work ensures that only one Artwork is being processed at once
	}

	// Upon successful authentication stores tokens returned from Pixiv into device memory
	static void storeTokens(SharedPreferences sharedPrefs, JSONObject tokens) throws JSONException
	{
		Log.i(LOG_TAG, "Storing tokens");
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putString("accessToken", tokens.getString("access_token"));
		editor.putLong("accessTokenIssueTime", (System.currentTimeMillis() / 1000));
		editor.putString("refreshToken", tokens.getString("refresh_token"));
		editor.putString("userId", tokens.getJSONObject("user").getString("id"));
		// Not yet tested, but I believe that this needs to be a commit() and not an apply()
		// Muzei queues up many picture requests at one. Almost all of them will not have an access token to use
		editor.commit();
		Log.i(LOG_TAG, "Stored tokens");
	}

	// Downloads the selected image to cache folder on local storage
	// Cache folder is periodically pruned of its oldest images by Android
	private Uri downloadFile(Response response, String filename) throws IOException
	{
		Log.i(LOG_TAG, "Downloading file");
		Context context = getApplicationContext();
		// Muzei does not care about file extensions
		// Only there to more easily allow local user to open them
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		OutputStream fos = null;
		File image = null;

		// if option to store into external storage is checked
		if (sharedPrefs.getBoolean("pref_storeInExtStorage", false))
		{
			// if permission granted
			if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
					== PackageManager.PERMISSION_GRANTED)
			{
				// if app OS was Q or higher
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
				{
					ContentResolver contentResolver = context.getContentResolver();
					ContentValues contentValues = new ContentValues();
					contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
					contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Images/PixivForMuzei3");
					Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
					fos = contentResolver.openOutputStream(imageUri);
					image = new File(imageUri.getPath());
				}
				// If app OS is N or lower
				// this section tested to work
				else
				{
					String directoryString = "/storage/emulated/0/Pictures/PixivForMuzei3/";
					File directory = new File(directoryString);
					if (!directory.exists())
					{
						directory.mkdirs();
					}
					image = new File(directoryString, filename + ".png");
					fos = new FileOutputStream(image);
				}
//				String directoryString = Environment.DIRECTORY_PICTURES + File.separator + "PixivForMuzei3";
//				File directory = new File(directoryString);
//				if (!directory.exists())
//				{
//					directory.mkdirs();
//				}
//				// This does not create a new file, just a new File object
//				downloadedFile = new File(directory, filename + ".png");
//				// Check if file with the same token already exists
//				// Is not redundant with isDuplicate
//				if (downloadedFile.exists())
//				{
//					Log.d(LOG_TAG, "File already exists, using cache");
//					return Uri.fromFile(downloadedFile);
//				}
			}
		}
		// External storage option not checked, store into default internal location
		// this section tested to work
		else
		{
			image = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename + ".png");
			fos = new FileOutputStream(image);
		}

		InputStream inputStream = response.body().byteStream();

		byte[] buffer = new byte[1024 * 1024 * 10];
		while (true)
		{
			int read = inputStream.read(buffer);
			if (read == -1)
			{
				break;
			}
			fos.write(buffer, 0, read);
		}
		fos.flush();
		response.close();
		inputStream.close();
		return Uri.fromFile(image);
		//downloadedFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename + ".png");


//		FileOutputStream fileStream = new FileOutputStream(downloadedFile);
//		InputStream inputStream = response.body().byteStream();
//		final byte[] buffer = new byte[1024 * 1024 * 10];
//		int read;
//		while ((read = inputStream.read(buffer)) != -1)
//		{
//			fileStream.write(buffer, 0, read);
//		}
//		fileStream.close();
//		inputStream.close();
//
//		Log.i(LOG_TAG, "Downloaded file");
//		return Uri.fromFile(downloadedFile);
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
		Log.i(LOG_TAG, "Getting remote file extensions");
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
			response = PixivArtService.sendGetRequestRanking(HttpUrl.parse(uri));
			if (response.code() == 200)
			{
				Log.i(LOG_TAG, "Gotten remote file extensions");
				return response;
			} else
			{
				response.close();
			}
		}
		Log.e(LOG_TAG, "Failed to get remote file extensions");
		return null;
	}

	/*
        RANKING
    */

	/*
	Builds the API URL, requests the JSON containing the ranking, passes it to a separate function
	for filtering, then downloads the image and returns it Muzei for insertion
	 */
	private Artwork getArtworkRanking(String mode) throws IOException, JSONException
	{
		Log.i(LOG_TAG, "getArtworkRanking(): Entering");
		Log.d(LOG_TAG, "Mode: " + mode);
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
				attribution = getApplicationContext().getString(R.string.attr_daily);
				break;
			case "weekly_rank":
				rankingUrl = rankingUrlBuilder
						.addQueryParameter("mode", "weekly")
						.build();
				attribution = getApplicationContext().getString(R.string.attr_weekly);
				break;
			case "monthly_rank":
				rankingUrl = rankingUrlBuilder
						.addQueryParameter("mode", "monthly")
						.build();
				attribution = getApplicationContext().getString(R.string.attr_monthly);
				break;
		}

		Response rankingResponse = PixivArtService.sendGetRequestRanking(rankingUrl);
		JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
		rankingResponse.close();

		JSONObject pictureMetadata;
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);

		Set<String> defaultRankingSelect = new HashSet<>();
		defaultRankingSelect.add("0");
		Set<String> rankingFilterSelect = sharedPrefs.getStringSet("pref_rankingFilterSelect", defaultRankingSelect);
		int aspectRatioSettings = Integer.parseInt(sharedPrefs.getString("pref_aspectRatioSelect", "0"));
		pictureMetadata = filterRanking(overallJson.getJSONArray("contents"),
				showManga, rankingFilterSelect, aspectRatioSettings);

		String token = pictureMetadata.getString("illust_id");
		attribution += pictureMetadata.get("rank");
		Response remoteFileExtension = getRemoteFileExtension(pictureMetadata.getString("url"));
		Uri localUri = downloadFile(remoteFileExtension, token);
		remoteFileExtension.close();

		Log.i(LOG_TAG, "getArtworkRanking(): Exited");

		return new Artwork.Builder()
				.title(pictureMetadata.getString("title"))
				.byline(pictureMetadata.getString("user_name"))
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
	private JSONObject filterRanking(JSONArray contents, boolean showManga, Set<String> selectedFilterLevelSet, int aspectRatioSetting) throws JSONException
	{
		Log.i(LOG_TAG, "filterRanking(): Entering");
		JSONObject pictureMetadata;
		Random random = new Random();
		boolean found = false;
		int retryCount = 0;

		do
		{
			pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));

			retryCount++;
			if (isDuplicate(Integer.toString(pictureMetadata.getInt("illust_id"))))
			{
				Log.v(LOG_TAG, "Duplicate ID: " + pictureMetadata.getInt("illust_id"));
				continue;
			}

			if (!showManga)
			{
				if (pictureMetadata.getInt("illust_type") != 0)
				{
					Log.v(LOG_TAG, "Manga not desired");
					continue;
				}
			}

			if (retryCount < 50)
			{
				if (!isDesiredAspectRatio(pictureMetadata, aspectRatioSetting))
				{
					Log.v(LOG_TAG, "Rejecting aspect ratio");
					continue;
				}
			}

			if (retryCount < 100)
			{
				String[] selectedFilterLevelArray = selectedFilterLevelSet.toArray(new String[0]);
				for (String s : selectedFilterLevelArray)
				{
					if (Integer.parseInt(s) == pictureMetadata.getJSONObject("illust_content_type").getInt("sexual"))
					{
						found = true;
						break;
					}
				}
				if (!found)
				{
					Log.d(LOG_TAG, "matching filtering not found");
				}
			}

		} while (!found);

		Log.i(LOG_TAG, "filterRanking(): Exited");
		return pictureMetadata;
	}

    /*
        FEED / BOOKMARK / TAG / ARTIST
     */

	/*
	Builds the API URL, requests the JSON containing the ranking, passes it to a separate function
	for filtering, then downloads the image and returns it Muzei for insertion
	 */
	private Artwork getArtworkAuth(String mode, String userId, String accessToken) throws IOException, JSONException
	{
		Log.i(LOG_TAG, "getArtworkAuth(): Entering");
		Log.d(LOG_TAG, "Mode: " + mode);

		// Builds the API URL to call depending on chosen update mode
		int offset = 0;
		boolean success = false;
		JSONObject pictureMetadata;

		// Reiterates until a valid artwork is found
		// Will loop again if too many retries when filtering
		// Predict this to almost never need to loop again
		do
		{
			HttpUrl feedBookmarkTagUrl = null;
			HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
					.scheme("https")
					.host("app-api.pixiv.net");
			switch (mode)
			{
				case "follow":
					feedBookmarkTagUrl = urlBuilder
							.addPathSegments("v2/illust/follow")
							.addQueryParameter("restrict", "public")
							.addQueryParameter("offset", Integer.toString(offset)) // adding offset works
							.build();
					break;
				case "bookmark":
					feedBookmarkTagUrl = urlBuilder
							.addPathSegments("v1/user/bookmarks/illust")
							.addQueryParameter("user_id", userId)
							.addQueryParameter("restrict", "public")
							.addQueryParameter("offset", Integer.toString(offset))
							.build();
					break;
				case "tag_search":
					feedBookmarkTagUrl = urlBuilder
							.addPathSegments("v1/search/illust")
							.addQueryParameter("word", userId)
							.addQueryParameter("search_target", "partial_match_for_tags")
							.addQueryParameter("sort", "date_desc")
							.addQueryParameter("filter", "for_ios")
							.addQueryParameter("offset", Integer.toString(offset))
							.build();
					break;
				case "artist":
					feedBookmarkTagUrl = urlBuilder
							.addPathSegments("v1/user/illusts")
							.addQueryParameter("user_id", userId)
							.addQueryParameter("filter", "for_ios")
							.addQueryParameter("offset", Integer.toString(offset))
							.build();
					break;
				case "recommended":
					feedBookmarkTagUrl = urlBuilder
							.addPathSegments("v1/illust/recommended")
							.addQueryParameter("content_type", "illust")
							.addQueryParameter("include_ranking_label", "true")
//				.addQueryParameter("min_bookmark_id_for_recent_illust", "")
//				.addQueryParameter("offset", "")
							.addQueryParameter("include_ranking_illusts", "true")
//				.addQueryParameter("bookmark_illust_ids", "")
							.addQueryParameter("filter", "for_ios")
							.addQueryParameter("offset", Integer.toString(offset))
							.build();
			}
			Response rankingResponse = PixivArtService.sendGetRequestAuth(feedBookmarkTagUrl, accessToken);
			JSONObject overallJson = new JSONObject((rankingResponse.body().string()));
			rankingResponse.close();

			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			int aspectRatioSettings = Integer.parseInt(sharedPrefs.getString("pref_aspectRatioSelect", "0"));
			boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
			// null default case allowed
			// App *MUST* be first opened in order to change the update mode and log in
			// Opening the app populates the shared preference with a default entry
			// As opposed to ranking, where there can be an empty shared preference
			Set<String> selectedFilterLevel = sharedPrefs.getStringSet("pref_authFilterSelect", null);

			pictureMetadata = filterFeedAuth(overallJson.getJSONArray("illusts"),
					showManga, selectedFilterLevel, aspectRatioSettings);
			if (pictureMetadata == null)
			{
				// 30 added because thats how many artworks are in a single auth update mode JSON
				Log.d(LOG_TAG, "Too many retries, acquiring new offset JSON");
				offset += 30;
				continue;
			}
			success = true;
		} while (!success);

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

		String token = pictureMetadata.getString("id");
		Response imageDataResponse = PixivArtService.sendGetRequestRanking(HttpUrl.parse(imageUrl));
		Uri localUri = downloadFile(imageDataResponse, token);
		imageDataResponse.close();
		Log.i(LOG_TAG, "getArtworkAuth(): Exited");
		return new Artwork.Builder()
				.title(pictureMetadata.getString("title"))
				.byline(pictureMetadata.getJSONObject("user").getString("name"))
				.persistentUri(localUri)
				.token(token)
				.webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
				.build();
	}

	/*
		Called by getArtworkAuth to return details about an artwork that complies with
		filtering restrictions set by the user
	 */
	private JSONObject filterFeedAuth(JSONArray illusts, boolean showManga, Set<String> selectedFilterLevelSet, int aspectRatio) throws JSONException
	{
		Log.i(LOG_TAG, "filterFeedAuth(): Entering");
		Random random = new Random();
		boolean found = false;
		JSONObject pictureMetadata;
		int retryCount = 0;
		// 30 is the size of an auth feed JSON
		final int retryLimit = 30;

		// Reiterates until artwork matching all criteria found or too many reties
		do
		{
			// If the loop reiterates too many times
			// Request a new illusts JSON, with different artwork
			if (retryCount > retryLimit)
			{
				Log.i(LOG_TAG, "Too many retries, requesting offset JSON");
				return null;
			}
			retryCount++;
			// Random produces more pleasing streams of art
			// Duplication is filtered with isDuplicate()
			// Only time waste is a number of CPU cycles
			pictureMetadata = illusts.getJSONObject(random.nextInt(illusts.length()));

			// Check if duplicate before any other check to not waste time
			if (isDuplicate(Integer.toString(pictureMetadata.getInt("id"))))
			{
				Log.v(LOG_TAG, "Duplicate ID: " + pictureMetadata.getInt("id"));
				continue;
			}

			// If user does not want manga to display
			if (!showManga)
			{
				if (!pictureMetadata.getString("type").equals("illust"))
				{
					Log.v(LOG_TAG, "Manga not desired");
					continue;
				}
			}

			// Filter artwork based on chosen aspect ratio
			if (!(isDesiredAspectRatio(pictureMetadata, aspectRatio)))
			{
				Log.v(LOG_TAG, "Rejecting aspect ratio");
				continue;
			}

			// See if there is a match between chosen artwork's sanity level and those desired
			String[] selectedFilterLevelArray = selectedFilterLevelSet.toArray(new String[0]);
			for (String s : selectedFilterLevelArray)
			{
				if (s.equals(Integer.toString(pictureMetadata.getInt("sanity_level"))))
				{
					Log.d(LOG_TAG, "sanity_level found is " + pictureMetadata.getInt("sanity_level"));
					found = true;
					break;
				} else if (s.equals("8"))
				{
					if (pictureMetadata.getInt("x_restrict") == 1)
					{
						Log.d(LOG_TAG, "x_restrict found");
						found = true;
						break;
					}
				}
			}
			if (!found)
			{
				Log.v(LOG_TAG, "filter level not found, was : " + pictureMetadata.getInt("sanity_level"));
			}
		} while (!found);

		return pictureMetadata;
	}

	private boolean isDesiredAspectRatio(JSONObject pictureMetadata, int aspectRatioSetting) throws JSONException
	{
		int width = pictureMetadata.getInt("width");
		int height = pictureMetadata.getInt("height");
		switch (aspectRatioSetting)
		{
			case 0:
				return true;
			case 1:
				return height >= width;
			case 2:
				return height <= width;
		}
		return true;
	}

	// Be provided a token/ID from either of the filter functions
	// Somehow iterate through the database or the folder
	private boolean isDuplicate(String token)
	{
		Uri conResUri = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).getContentUri();
		Cursor cursor = getApplicationContext().getContentResolver().query(conResUri, new String[]{"token"}, null, null, null);
		try
		{
			while (cursor.moveToNext())
			{
				if (cursor.getString(0).equals(token))
				{
					return true;
				}
			}
		} finally
		{
			cursor.close();
		}
		return false;
	}

	/*
		First method to be called
		Sets program flow into ranking or feed/bookmark paths
		Also acquires an access token to be passed into getArtworkAuth()
			Why is this function the one acquiring the access token?
	 */
	private Artwork getArtwork()
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String mode = sharedPrefs.getString("pref_updateMode", "daily_rank");
		Log.d(LOG_TAG, "Display mode: " + mode);
		Artwork artwork = null;
		String accessToken = "";

		// These modes require an access token, so we check for and acquire one first
		if (Arrays.asList("follow", "bookmark", "tag_search", "artist", "recommended").contains(mode))
		{
			accessToken = PixivArtService.getAccesToken(sharedPrefs);
			if (accessToken.isEmpty())
			{
				// If acccess token was empty, due to failed auth or a network error
				// we have three pre defined behaviors that we can take 
				Handler handler = new Handler(Looper.getMainLooper());
				String authFailMode = sharedPrefs.getString("pref_authFailAction", "changeDaily");
				switch (authFailMode)
				{
					case "changeDaily":
						Log.d(LOG_TAG, "Auth failed, changing mode to daily");
						sharedPrefs.edit().putString("pref_updateMode", "daily_rank").apply();
						mode = "daily_rank";
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedSwitch, Toast.LENGTH_SHORT).show());
						break;
					case "doNotChange_downDaily":
						Log.d(LOG_TAG, "Auth failed, downloading a single daily");
						mode = "daily_rank";
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
			if (Arrays.asList("follow", "bookmark", "tag_search", "artist", "recommended").contains(mode))
			{
				String userId = sharedPrefs.getString("userId", "");
				artwork = getArtworkAuth(mode, userId, accessToken);
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

		// If we don't need to clear the cache, fetch just a single image on request
		if (!clearArtwork)
		{
			Artwork artwork = getArtwork();
			if (isArtworkNull(artwork))
			{
				return Result.retry();
			}
			client.addArtwork(artwork);
		}
		// Cache is being cleared for whatever reason, so we initially populate the 
		// database with 3 images.
		// All are submitted at once to Muzei using an ArrayList
		else
		{
			clearArtwork = false;
			ArrayList<Artwork> artworkArrayList = new ArrayList<>();
			for (int i = 0; i < 3; i++)
			{
				Artwork artwork = getArtwork();
				if (isArtworkNull(artwork))
				{
					// Add what we can
					if (!artworkArrayList.isEmpty())
					{
						client.setArtwork(artworkArrayList);
					}
					return Result.retry();
				}
				artworkArrayList.add(artwork);
			}
			client.setArtwork(artworkArrayList);
		}
		Log.d(LOG_TAG, "Work completed");
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
