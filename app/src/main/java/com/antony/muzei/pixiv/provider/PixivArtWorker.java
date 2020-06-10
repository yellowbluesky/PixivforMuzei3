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
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException;
import com.antony.muzei.pixiv.provider.exceptions.CorruptFileException;
import com.antony.muzei.pixiv.provider.exceptions.FilterMatchNotFoundException;
import com.antony.muzei.pixiv.provider.network.moshi.AuthArtwork;
import com.antony.muzei.pixiv.provider.network.moshi.Contents;
import com.antony.muzei.pixiv.provider.network.moshi.Illusts;
import com.antony.muzei.pixiv.login.OauthResponse;
import com.antony.muzei.pixiv.provider.network.moshi.RankingArtwork;
import com.antony.muzei.pixiv.provider.network.AuthJsonServerResponse;
import com.antony.muzei.pixiv.provider.network.ImageDownloadServerResponse;
import com.antony.muzei.pixiv.provider.network.RankingJsonServerResponse;
import com.antony.muzei.pixiv.provider.network.RestClient;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderClient;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;

public class PixivArtWorker extends Worker
{
	private static final String LOG_TAG = "ANTONY_WORKER";
	private static final String WORKER_TAG = "ANTONY";

	private static final String[] IMAGE_SUFFIXES = {".png", ".jpg"};
	private static boolean clearArtwork = false;


	public PixivArtWorker(
			@NonNull Context context,
			@NonNull WorkerParameters params)
	{
		super(context, params);
	}

	public static void enqueueLoad(boolean clear, Context context)
	{
		if (clear)
		{
			clearArtwork = true;
		}

		Constraints constraints = new Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build();
		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PixivArtWorker.class)
				.setConstraints(constraints)
				.addTag(WORKER_TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
				.build();
		WorkManager.getInstance(context).enqueueUniqueWork(WORKER_TAG, ExistingWorkPolicy.KEEP, request);
		// Must be a uniqueWork
		// If not Muzei will queue MANY at once on initial load
		// This is good for saturating a network link and for fast picture downloads
		// However, race conditions develop if work required is authenticated
		// unique work ensures that only one Artwork is being processed at once
	}

	// Upon successful authentication stores tokens returned from Pixiv into device memory
	public static void storeTokens(SharedPreferences sharedPrefs,
	                               OauthResponse response)
	{
		Log.i(LOG_TAG, "Storing tokens");
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putString("accessToken", response.getPixivOauthResponse().getAccess_token());
		editor.putLong("accessTokenIssueTime", (System.currentTimeMillis() / 1000));
		editor.putString("refreshToken", response.getPixivOauthResponse().getRefresh_token());
		editor.putString("userId", response.getPixivOauthResponse().getUser().getId());
		editor.putString("name", response.getPixivOauthResponse().getUser().getName());
		// Not yet tested, but I believe that this needs to be a commit() and not an apply()
		// Muzei queues up many picture requests at one. Almost all of them will not have an access token to use
		editor.apply();
		Log.i(LOG_TAG, "Stored tokens");
	}

	/*
		Ranking images are only provided with a URL to a low resolution thumbnail
		We want the high resolution image, so we need to do some work first

		Secondly, the thumbnail is always a .jpg
		For the high resolution image we require a correct file extension
		This method cycles though all possible file extensions until a good response is received
			i.e. a response that is not a 400 error
		Returns a Response whose body contains the picture selected to be downloaded
	*/
	private ResponseBody getRemoteFileExtension(String url) throws IOException
	{
		Log.i(LOG_TAG, "Getting remote file extensions");
		Response response;
		/*
			Thumbnail URL:
				https://tc-pximg01.techorus-cdn.com/c/240x480/img-master/img/2020/02/19/00/00/39/79583564_p0_master1200.jpg
			High resolution URL (direct access will 403):
				https://i.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0.png
			Accessible high resolution URL:
				https://i-cf.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0.png
		 */

		String uri0 = "https://i.pximg.net/img-original" + url.substring(url.indexOf("/img/"));
		String uri1 = uri0
				.replace("_master1200", "");
		String uri2 = uri1.substring(0, uri1.length() - 4);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean bypassActive = sharedPreferences.getBoolean("pref_enableNetworkBypass", false);

		for (String suffix : IMAGE_SUFFIXES)
		{
			String uri = uri2 + suffix;
			ImageDownloadServerResponse service = RestClient.getRetrofitImageInstance(bypassActive).create(ImageDownloadServerResponse.class);
			Call<ResponseBody> call = service.downloadImage(uri);
			retrofit2.Response<ResponseBody> responseBodyResponse = call.execute();
			response = responseBodyResponse.raw();

			if (response.isSuccessful())
			{
				Log.i(LOG_TAG, "Gotten remote file extensions");
				return responseBodyResponse.body();
			}
		}
		Log.e(LOG_TAG, "Failed to get remote file extensions");
		return null;
	}

	/*
		PixivforMuzei3 often downloads an incomplete image, i.e. the lower section of images is not
		downloaded, the file header is intact but file closer is not present.
		This function converts the image to a byte representation, then checks the last few bytes
		in the image for a valid file closer.
		If image is incomplete, throws CorruptFileException
		Returns:
			1   png
			2   jpg
			CorruptFileException
    */
	private int getLocalFileExtension(File image) throws IOException, CorruptFileException
	{
		byte[] byteArray = FileUtils.readFileToByteArray(image);
		int length = byteArray.length;
		int result = 0;
		// if jpeg
		if (byteArray[0] == -119 && byteArray[1] == 80 && byteArray[2] == 78 && byteArray[3] == 71)
		{
			if (byteArray[length - 8] == 73 && byteArray[length - 7] == 69 && byteArray[length - 6] == 78 && byteArray[length - 5] == 68)
			{
				Log.d(LOG_TAG, "image is intact PNG");
				result = 1;
			} else
			{
				Log.d(LOG_TAG, "image is corrupt PNG");
				throw new CorruptFileException("Corrupt PNG");
			}
		} else if (byteArray[0] == -1 && byteArray[1] == -40)
		{
			if (byteArray[length - 2] == -1 && byteArray[length - 1] == -39)
			{
				Log.d(LOG_TAG, "image is intact JPG");
				result = 2;
			} else
			{
				Log.d(LOG_TAG, "image is corrupt JPG");
				throw new CorruptFileException("Corrupt JPG");
			}
		}
		return result;
	}

	/*
		First downloads the file to ExternalFilesDir, always with a png file extension
		Checks if the file is incomplete; if incomplete deletes it and passes a CorruptFileException
		up the chain
		Otherwise returns a Uri to the File to the caller
		If option is checked, also makes a copy into external storage
		The external storage copy is not used for backing any database
		The external storage copy also has correct file extensions
	 */
	private Uri downloadFile(ResponseBody responseBody,
	                         String filename) throws IOException, CorruptFileException
	{
		Log.i(LOG_TAG, "Downloading file");
		Context context = getApplicationContext();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		File imageInternal = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename + ".png");
		FileOutputStream fosInternal = new FileOutputStream(imageInternal);

		InputStream inputStreamNetwork = responseBody.byteStream();

		byte[] bufferTemp = new byte[1024 * 1024 * 10];
		int readTemp;
		while ((readTemp = inputStreamNetwork.read(bufferTemp)) != -1)
		{
			fosInternal.write(bufferTemp, 0, readTemp);
		}
		inputStreamNetwork.close();
		fosInternal.close();
		responseBody.close();

		// TODO make this an enum
		int fileExtension = getLocalFileExtension(imageInternal);

		// If option in SettingsActivity is checked AND permission is granted
		if (sharedPrefs.getBoolean("pref_storeInExtStorage", false) &&
				ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
						== PackageManager.PERMISSION_GRANTED)
		{
			OutputStream fosExternal = null;
			boolean allowedToStoreIntoExternal = false;

			// Android 10 introduced scoped storage
			// Different code path depending on Android APi level
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			{
				ContentResolver contentResolver = context.getContentResolver();
				ContentValues contentValues = new ContentValues();

				// Check if existing copy of file exists
				String[] projection = {MediaStore.Images.Media._ID};
				String selection = "title = ?";
				//String selection ={MediaStore.Images.Media.DISPLAY_NAME + " = ? AND ", MediaStore.Images.Media.RELATIVE_PATH + " = ?"};
				String[] selectionArgs = {filename};
				Cursor cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
				if (cursor.getCount() == 0)
				{
					contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
					contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PixivForMuzei3");
					if (fileExtension == 1)
					{
						contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
					} else if (fileExtension == 2)
					{
						contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
					}

					Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
					fosExternal = contentResolver.openOutputStream(imageUri);
					allowedToStoreIntoExternal = true;
				}
				cursor.close();
			}
			// If app OS is N or lower
			else
			{
				String directoryString = "/storage/emulated/0/Pictures/PixivForMuzei3/";
				File directory = new File(directoryString);
				if (!directory.exists())
				{
					directory.mkdirs();
				}

				// If the image has already been downloaded, do not redownload
				File imagePng = new File(directoryString, filename + ".png");
				File imageJpg = new File(directoryString, filename + ".jpg");
				if (!imageJpg.exists() || !imagePng.exists())
				{
					if (fileExtension == 1)
					{
						fosExternal = new FileOutputStream(imagePng);
					} else if (fileExtension == 2)
					{
						fosExternal = new FileOutputStream(imageJpg);
					}
					allowedToStoreIntoExternal = true;
				}

			}

			// Finally copies the image into external storage if allowed to
			if (allowedToStoreIntoExternal)
			{
				FileInputStream fis = new FileInputStream(imageInternal);
				byte[] buffer = new byte[1024 * 1024 * 10];
				int lengthInternal;
				while ((lengthInternal = fis.read(buffer)) > 0)
				{
					fosExternal.write(buffer, 0, lengthInternal);
				}
				fosExternal.close();
				fis.close();
			}
		}

		return Uri.fromFile(imageInternal);
	}

	private boolean isArtworkNull(Artwork artwork)
	{
		if (artwork == null)
		{
			Log.e(LOG_TAG, "Null artwork returned, retrying at later time");
			return true;
		}
		return false;
	}

	/*
		Provided an artowrk ID (token), traverses the PixivArtProvider ContentProvider and sees
		if there is already a duplicate artwork with the same ID (token)
	 */
	private boolean isDuplicateArtwork(int token)
	{
		boolean duplicateFound = false;

		String[] projection = {"_id"};
		String selection = "token = ?";
		String[] selectionArgs = {Integer.toString(token)};
		Uri conResUri = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).getContentUri();
		Cursor cursor = getApplicationContext().getContentResolver().query(conResUri, projection, selection, selectionArgs, null);

		if (cursor.getCount() > 0)
		{
			duplicateFound = true;
		}
		cursor.close();

		return duplicateFound;
	}

	/*
		0   Any aspect ratio
		1   Landscape
		2   Portrait
	 */
	private boolean isDesiredAspectRatio(int width,
	                                     int height,
	                                     int aspectRatioSetting)
	{
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

	// Scalar must match with scalar in SettingsActivity
	private boolean isEnoughViews(int artworkViewCount,
	                              int minimumDesiredViews)
	{
		return artworkViewCount >= (minimumDesiredViews * 500);
	}

	private boolean isImageTooLarge(long sizeBytes, long limitBytes)
	{
		return sizeBytes > limitBytes;
	}

	private int[] generateShuffledArray(int length)
	{
		Random random = new Random();
		int[] array = new int[length];
		for (int i = 0; i < length; i++)
		{
			array[i] = i;
		}

		for (int i = length - 1; i > 0; i--)
		{
			int index = random.nextInt(length);
			int a = array[index];
			array[index] = array[i];
			array[i] = a;
		}
		return array;
	}

	/*
		Receives a JSON of the ranking artworks.
		Passes it off to filterArtworkRanking(), then builds the Artwork for submission to Muzei
	 */
	private Artwork getArtworkRanking(Contents contents) throws IOException, CorruptFileException, FilterMatchNotFoundException
	{
		Log.i(LOG_TAG, "getArtworkRanking(): Entering");
		String mode = contents.getMode();
		String attribution = null;

		switch (mode)
		{
			case "daily":
				attribution = getApplicationContext().getString(R.string.attr_daily);
				break;
			case "weekly":
				attribution = getApplicationContext().getString(R.string.attr_weekly);
				break;
			case "monthly":
				attribution = getApplicationContext().getString(R.string.attr_monthly);
				break;
			case "rookie":
				attribution = getApplicationContext().getString(R.string.attr_rookie);
				break;
			case "original":
				attribution = getApplicationContext().getString(R.string.attr_original);
				break;
			case "male":
				attribution = getApplicationContext().getString(R.string.attr_male);
				break;
			case "female":
				attribution = getApplicationContext().getString(R.string.attr_female);
				break;
		}
		String attributionDate = contents.getDate();
		String attTrans = attributionDate.substring(0, 4) + "/" + attributionDate.substring(4, 6) + "/" + attributionDate.substring(6, 8) + " ";

		//writeToFile(overallJson, "rankingLog.txt");

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Filter variables, to pass to filterArtworkRanking()
		boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
		Set<String> defaultRankingSelect = new HashSet<>();
		defaultRankingSelect.add("0");
		Set<String> rankingFilterSelect = sharedPrefs.getStringSet("pref_rankingFilterSelect", defaultRankingSelect);
		int aspectRatioSettings = Integer.parseInt(sharedPrefs.getString("pref_aspectRatioSelect", "0"));
		int minimumViews = sharedPrefs.getInt("prefSlider_minViews", 0);

		// Filtering
		RankingArtwork rankingArtwork = filterArtworkRanking(contents.getArtworks(),
				showManga, rankingFilterSelect, aspectRatioSettings, minimumViews);

		// Variables to submit to Muzei
		String token = Integer.toString(rankingArtwork.getIllust_id());
		attribution = attTrans + attribution;
		attribution += rankingArtwork.getRank();

		// Actually downloading the selected artwork
		ResponseBody remoteFileExtension = getRemoteFileExtension(rankingArtwork.getUrl());
		int fileSizeLimit = sharedPrefs.getInt("prefSlider_maxFileSize", 0);
		// 1024 scalar to convert MB to byte
		if (fileSizeLimit != 0 && isImageTooLarge(remoteFileExtension.contentLength(), fileSizeLimit * 1048576))
		{
			Log.v("SIZE", "too chonk");
			//throw new ImageTooLargeException("");
			// grab a new image, somehwo loop back
		} else
		{
			Log.v("SIZE", "good size");
		}
		Uri localUri = downloadFile(remoteFileExtension, token);
		remoteFileExtension.close();

		Log.i(LOG_TAG, "getArtworkRanking(): Exited");

		return new Artwork.Builder()
				.title(rankingArtwork.getTitle())
				.byline(rankingArtwork.getUser_name())
				.attribution(attribution)
				.persistentUri(localUri)
				.token(token)
				.webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
				.build();
	}

	/*
		Filters through the JSON containing the metadata of the pictures of the selected mode
		Picks one image based on the user's setting to show manga and level of NSFW filtering

			NSFW filtering is performed by checking the value of the "sexual" JSON string
			Manga filtering is performed by checking the value of the "illust_type" JSON string
	*/
	private RankingArtwork filterArtworkRanking(List<RankingArtwork> rankingArtworkList,
	                                            boolean showManga,
	                                            Set<String> selectedFilterLevelSet,
	                                            int aspectRatioSetting,
	                                            int minimumViews) throws FilterMatchNotFoundException
	{
		Log.i(LOG_TAG, "filterRanking(): Entering");
		RankingArtwork rankingArtwork = null;
		boolean found = false;

		int[] shuffledArray = generateShuffledArray(rankingArtworkList.size());

		for (int i = 0; i < rankingArtworkList.size(); i++)
		{
			rankingArtwork = rankingArtworkList.get(shuffledArray[i]);

			if (isDuplicateArtwork(rankingArtwork.getIllust_id()))
			{
				Log.v(LOG_TAG, "Duplicate ID: " + rankingArtwork.getIllust_id());
				continue;
			}

			if (!isEnoughViews(rankingArtwork.getView_count(), minimumViews))
			{
				Log.v(LOG_TAG, "Not enough views");
				continue;
			}

			if (!showManga && rankingArtwork.getIllust_type() != 0)
			{
				Log.v(LOG_TAG, "Manga not desired " + rankingArtwork.getIllust_id());
				continue;

			}

			if (!isDesiredAspectRatio(rankingArtwork.getWidth(),
					rankingArtwork.getHeight(), aspectRatioSetting))
			{
				Log.v(LOG_TAG, "Rejecting aspect ratio");
				continue;
			}

			// TODO this doesn't appear to work at all
			// I selected to show only NSFW artwork, and I got some random stuff
			String[] selectedFilterLevelArray = selectedFilterLevelSet.toArray(new String[0]);
			for (String s : selectedFilterLevelArray)
			{
				if (Integer.parseInt(s) == rankingArtwork.getIllust_content_type().getSexual())
				{
					found = true;
					break;
				}
			}
		}
		if (!found)
		{
			throw new FilterMatchNotFoundException("");
		}

		Log.i(LOG_TAG, "filterRanking(): Exited");
		return rankingArtwork;
	}

    /*
        FEED / BOOKMARK / TAG / ARTIST
     */

	/*
	Builds the API URL, requests the JSON containing the ranking, passes it to a separate function
	for filtering, then downloads the image and returns it Muzei for insertion
	 */
	private Artwork getArtworkAuth(List<AuthArtwork> authArtworkList) throws FilterMatchNotFoundException, IOException, CorruptFileException
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Filter variables to pass to filterArtworkAuth()
		int aspectRatioSettings = Integer.parseInt(sharedPrefs.getString("pref_aspectRatioSelect", "0"));
		boolean showManga = sharedPrefs.getBoolean("pref_showManga", false);
		// null default case allowed
		// App *MUST* be first opened in order to change the update mode and log in
		// Opening the app populates the shared preference with a default entry
		// As opposed to ranking, where there can be an empty shared preference
		Set<String> selectedFilterLevel = sharedPrefs.getStringSet("pref_authFilterSelect", null);
		int minimumViews = sharedPrefs.getInt("prefSlider_minViews", 0);

		// Filtering
		AuthArtwork selectedArtwork = filterArtworkAuth(authArtworkList,
				showManga, selectedFilterLevel, aspectRatioSettings, minimumViews);

		// Variables for submitting to Muzei
		String imageUrl;
		if (selectedArtwork.getMeta_pages().size() == 0)
		{
			Log.d(LOG_TAG, "Picture is a single image");
			imageUrl = selectedArtwork
					.getMeta_single_page()
					.getOriginal_image_url();
		} else
		{
			Log.d(LOG_TAG, "Picture is part of an album");
			imageUrl = selectedArtwork
					.getMeta_pages()
					.get(0)
					.getImage_urls()
					.getOriginal();
		}
		String token = Integer.toString(selectedArtwork.getId());

		boolean bypassActive = sharedPrefs.getBoolean("pref_enableNetworkBypass", false);

		// Actually downloading the file
		ImageDownloadServerResponse service = RestClient.getRetrofitImageInstance(bypassActive).create(ImageDownloadServerResponse.class);
		Call<ResponseBody> call = service.downloadImage(imageUrl);
		ResponseBody imageDataResponse = call.execute().body();
		int fileSizeLimitMegabytes = sharedPrefs.getInt("prefSlider_maxFileSize", 0);
		// 1024 scalar to convert from MB to bytes
		if (fileSizeLimitMegabytes != 0 && isImageTooLarge(imageDataResponse.contentLength(), fileSizeLimitMegabytes * 1048576))
		{
			Log.v("SIZE", "too chonk");
		} else
		{
			Log.v("SIZE", "good size");
		}
		Uri localUri = downloadFile(imageDataResponse, token);
		imageDataResponse.close();

		Log.i(LOG_TAG, "getArtworkAuth(): Exited");
		return new Artwork.Builder()
				.title(selectedArtwork.getTitle())
				.byline(selectedArtwork.getUser().getName())
				.persistentUri(localUri)
				.token(token)
				.webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
				.build();
	}

	/*
		Called by getArtworkAuth to return details about an artwork that complies with
		filtering restrictions set by the user

		For NSFW filtering the two relevant JSON strings are "sanity_level" and "x_restrict"
			sanity_level
				2 -> Completely SFW
				4 -> Moderately ecchi e.g. beach bikinis, slight upskirts
				6 -> Very ecchi e.g. more explicit and suggestive themes
			 x_restrict
				1 -> R18 e.g. nudity and penetration

			In this code x_restrict is treated as a level 8 sanity_level

		For manga filtering, the value of the "type" string is checked for either "manga" or "illust"

	 */
	private AuthArtwork filterArtworkAuth(List<AuthArtwork> authArtworkList,
	                                      boolean showManga,
	                                      Set<String> selectedFilterLevelSet,
	                                      int aspectRatioSetting,
	                                      int minimumViews) throws FilterMatchNotFoundException
	{
		Log.i(LOG_TAG, "filterArtworkAuth(): Entering");
		boolean found = false;
		AuthArtwork selectedArtwork = null;

		int[] shuffledArray = generateShuffledArray(authArtworkList.size());

		loop:
		for (int i = 0; i < authArtworkList.size(); i++)
		{
			selectedArtwork = authArtworkList.get(shuffledArray[i]);

			// Check if duplicate before any other check to not waste time
			if (isDuplicateArtwork(selectedArtwork.getId()))
			{
				Log.v(LOG_TAG, "Duplicate ID: " + selectedArtwork.getId());
				continue;
			}

			// If user does not want manga to display
			if (!showManga && !selectedArtwork.getType().equals("illust"))
			{
				Log.d(LOG_TAG, "Manga not desired");
				continue;

			}

			// Filter artwork based on chosen aspect ratio
			if (!(isDesiredAspectRatio(selectedArtwork.getWidth(),
					selectedArtwork.getHeight(), aspectRatioSetting)))
			{
				Log.d(LOG_TAG, "Rejecting aspect ratio");
				continue;
			}

			if (!isEnoughViews(selectedArtwork.getTotal_view(), minimumViews))
			{
				Log.d(LOG_TAG, "Not enough views");
				continue;
			}

			// See if there is a match between chosen artwork's sanity level and those desired
			String[] selectedFilterLevelArray = selectedFilterLevelSet.toArray(new String[0]);
			for (String s : selectedFilterLevelArray)
			{
				if (s.equals(Integer.toString(selectedArtwork.getSanity_Level())))
				{
					Log.d(LOG_TAG, "sanity_level found is " + selectedArtwork.getSanity_Level());
					found = true;
					break loop;
				} else if (s.equals("8") && selectedArtwork.getX_restrict() == 1)
				{
					Log.d(LOG_TAG, "x_restrict found");
					found = true;
					break loop;
				}
			}
		}
		if (!found)
		{
			throw new FilterMatchNotFoundException("too many retries");
		}
		return selectedArtwork;
	}

	/*
		First method to be called
		Sets program flow into ranking or feed/bookmark paths
		Also acquires an access token to be passed into getArtworkAuth()
			Why is this function the one acquiring the access token?
	 */
	private ArrayList<Artwork> getArtwork() throws IOException, CorruptFileException
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String mode = sharedPrefs.getString("pref_updateMode", "daily");
		String accessToken = "";

		// These modes require an access token, so we check for and acquire one first
		if (Arrays.asList(PixivArtProviderDefines.AUTH_MODES).contains(mode))
		{
			try
			{
				accessToken = PixivArtService.refreshAccessToken(sharedPrefs);
			} catch (AccessTokenAcquisitionException ex)
			{
				Handler handler = new Handler(Looper.getMainLooper());
				String authFailMode = sharedPrefs.getString("pref_authFailAction", "changeDaily");
				switch (authFailMode)
				{
					case "changeDaily":
						Log.d(LOG_TAG, "Auth failed, changing mode to daily");
						sharedPrefs.edit().putString("pref_updateMode", "daily").apply();
						mode = "daily";
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedSwitch, Toast.LENGTH_SHORT).show());
						break;
					case "doNotChange_downDaily":
						Log.d(LOG_TAG, "Auth failed, downloading a single daily");
						mode = "daily";
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedDown, Toast.LENGTH_SHORT).show());
						break;
					case "doNotChange_doNotDown":
						Log.d(LOG_TAG, "Auth failed, retrying with no changes");
						handler.post(() -> Toast.makeText(getApplicationContext(), R.string.toast_authFailedRetry, Toast.LENGTH_SHORT).show());
						return null;
				}
			}
		}

		ArrayList<Artwork> artworkArrayList = new ArrayList<>();
		Artwork artwork;
		boolean bypassActive = sharedPrefs.getBoolean("pref_enableNetworkBypass", false);

		if (Arrays.asList(PixivArtProviderDefines.AUTH_MODES).contains(mode))
		{
			AuthJsonServerResponse service = RestClient.getRetrofitAuthInstance(bypassActive).create(AuthJsonServerResponse.class);
			Call<Illusts> call;
			switch (mode)
			{
				case "follow":
					// This concat is ugly af
					call = service.getFollowJson("Bearer " + accessToken);
					break;
				case "bookmark":
					call = service.getBookmarkJson("Bearer " + accessToken, sharedPrefs.getString("userId", ""));
					break;
				case "recommended":
					call = service.getRecommendedJson("Bearer " + accessToken);
					break;
				case "artist":
					call = service.getArtistJson("Bearer " + accessToken, sharedPrefs.getString("pref_artistId", ""));
					break;
				case "tag_search":
					call = service.getTagSearchJson("Bearer " + accessToken, sharedPrefs.getString("pref_tagSearch", ""));
					break;
				default:
					throw new IllegalStateException("Unexpected value: " + mode);
			}
			Illusts illusts = call.execute().body();
			List<AuthArtwork> authArtworkList = illusts.getArtworks();

			for (int i = 0; i < sharedPrefs.getInt("prefSlider_numToDownload", 2); i++)
			{
				try
				{
					artwork = getArtworkAuth(authArtworkList);
					if (isArtworkNull(artwork))
					{
						throw new CorruptFileException("");
					}
					artworkArrayList.add(artwork);
				} catch (FilterMatchNotFoundException e)
				{
					e.printStackTrace();
					call = service.getNextUrl("Bearer " + accessToken, illusts.getNext_url());
					illusts = call.execute().body();
					authArtworkList = illusts.getArtworks();
				}
			}
		} else
		{
			RankingJsonServerResponse service = RestClient.getRetrofitRankingInstance(bypassActive).create(RankingJsonServerResponse.class);
			Call<Contents> call = service.getRankingJson(mode);
			Contents contents = call.execute().body();
			int pageNumber = 1;
			String date = contents.getDate();
			String prevDate = contents.getPrev_date();

			for (int i = 0; i < sharedPrefs.getInt("prefSlider_numToDownload", 2); i++)
			{
				try
				{
					artwork = getArtworkRanking(contents);
					if (isArtworkNull(artwork))
					{
						throw new CorruptFileException("");
					}
					artworkArrayList.add(artwork);
				} catch (FilterMatchNotFoundException e)
				{
					// If enough artworks are not found in the 50 from the first page of the rankings,
					// keep looking through the next pages or days
					e.printStackTrace();
					// We can continue to look through the 450 rankings for that day
					// There is a tenth page actually, but the next page number integer becomes a boolean
					// GSON can't handle this and throws a fit.
					// Thus I've limited my app to parsing only the top 450 rankings
					if (pageNumber != 9)
					{
						pageNumber++;
						call = service.getRankingJson(mode, pageNumber, date);
						contents = call.execute().body();
					}
					// We need to go through more than 500 ranking images, wow what are you doing
					// Reset the page number to 1, request yesterday's ranking
					// Update the current page number and date of the JSON
					else
					{
						pageNumber = 1;
						call = service.getRankingJson(mode, pageNumber, prevDate);
						contents = call.execute().body();
						date = contents.getDate();
						prevDate = contents.getPrev_date();
					}
				}
			}
		}
		Log.i(LOG_TAG, "Submitting " + sharedPrefs.getInt("prefSlider_numToDownload", 2) +
				" artworks");
		return artworkArrayList;
	}

	@NonNull
	@Override
	public Result doWork()
	{
		Log.d(LOG_TAG, "Starting work");
		ProviderClient client = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class);
		ArrayList<Artwork> artworkArrayList;
		try
		{
			artworkArrayList = getArtwork();
		} catch (IOException | CorruptFileException e)
		{
			e.printStackTrace();
			return Result.retry();
		}

		if (clearArtwork)
		{
			clearArtwork = false;
			client.setArtwork(artworkArrayList);
		} else
		{
			client.addArtwork(artworkArrayList);
		}
		Log.d(LOG_TAG, "Work completed");

		return Result.success();
	}
}
