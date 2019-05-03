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
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;
import com.google.android.apps.muzei.api.provider.Artwork;

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

public class PixivArtProvider extends MuzeiArtProvider
{
	private static final int LIMIT = 5;
	private static final String LOG_TAG = "PIXIV_DEBUG";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};

	// Returns a string containing a valid access token
	// Otherwise returns an empty string if authentication failed or not possible
	private String getAccessToken()
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

		// If we possess an access token, AND it has not expired, instantly return it
		// is this code style ok? Some declared variables, some getters
		String accessToken = sharedPrefs.getString("accessToken", "");
		if (!accessToken.isEmpty() && sharedPrefs.getLong("accessTokenIssueTime", 0) > System.currentTimeMillis() - 3600000)
		{
			LOG.i(LOG_TAG, "Existing access token found");
			return accessToken;
		}

		LOG.i(LOG_TAG, "No access token, or access token expired");


		// If we did not have an access token or if it had expired, we proceed to build a request to acquire one
		Uri.Builder authQueryBuilder = new Uri.Builder()
				.appendQueryParameter("get_secure_url", Integer.toString(1))
				.appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
				.appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET);

		// Check if we have a refresh token
		// If we do, we can avoid Pixiv sending an email to the user regarding a new login
		if (sharedPrefs.getString("refreshToken", "").isEmpty())
		{
			Log.i(LOG_TAG, "No refresh token found, proceeding with username / password authentication");
			authQueryBuilder.appendQueryParameter("grant_type", "password")
					.appendQueryParameter("username", sharedPrefs.getString("pref_loginId", ""))
					.appendQueryParameter("password", sharedPrefs.getString("pref_loginPassword", ""));
		} else
		{
			Log.i(LOG_TAG, "Found refresh token, using it to request an access token");
			authQueryBuilder.appendQueryParameter("grant_type", "refresh_token")
					.appendQueryParameter("refresh_token", sharedPrefs.getString("refreshToken", ""));
		}

		Uri authQuery = authQueryBuilder.build();

		try
		{
			Response response = sendPostRequest(PixivArtProviderDefines.OAUTH_URL, authQuery);
			JSONObject authResponseBody = new JSONObject(response.body().string());
			response.close();
			// Check if returned JSON indicates an error in authentication
			if (authResponseBody.has("has_error"))
			{
				// TODO maybe a one off toast message indicating error
				// do we change the mode to ranking too?
				Log.i(LOG_TAG, "Error authenticating, check username or password");
				return "";
			}

			JSONObject tokens = authResponseBody.getJSONObject("response");

			SharedPreferences.Editor editor = sharedPrefs.edit();
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

	// Only used when acquiring an access token
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

	// Used for pulling (unauthenticated) ranking JSON's
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

	// Used for pulling authenticated feed and bookmark JSON's
	private Response sendGetRequestAuth(String url, String accessToken) throws IOException
	{
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.build();

		Request.Builder builder = new Request.Builder()
				.addHeader("User-Agent", "PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)")
				.addHeader("App-OS", "ios")
				.addHeader("App-OS-Version", "10.3.1")
				.addHeader("App-Version", "6.9.0")
				.addHeader("Authorization", "Bearer " + accessToken)
				.url(url);

		return httpClient.newCall(builder.build()).execute();
	}

	// TODO Maybe mark this function as throwing exception
	// Downloads the selected image to cache folder on local storage
	// Cache folder is periodically pruned of its oldest images by Android
	private Uri downloadFile(Response response, String token)
	{
		Context context = getContext();
		// File extensions do not matter to muzei
		// Only there to more easily allow local user to open them
		File downloadedFile = new File(context.getCacheDir(), token + ".png");
		try
		{
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
		} catch (IOException ex)
		{
			return null;
		} finally
		{
			response.close();
		}

		return Uri.fromFile(downloadedFile);
	}

	// For ranking images, we are only provided with an illustration id
	// We require the correct file extension in order to pull the picture
	// So we cycle through all common file extensions until we get a good response
	private Response getRemoteFileExtension(String url) throws IOException
	{
		Response response;

		// All urls have predictable formats
		// TODO check the function of this
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

	@Override
	protected void onLoadRequested(boolean initial)
	{
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		String mode = sharedPrefs.getString("pref_updateMode", "");
		Log.d(LOG_TAG, "mode: " + mode);
		JSONObject overallJson = null, pictureMetadata;
		String title, byline, token, imageUrl, accessToken = "";
		Response response, rankingResponse;

		// Gets an access token if required
		// If the process failed in any way, then change modes to daily_rank
		if (sharedPrefs.getBoolean("pref_useAuth", false))
		{
			accessToken = getAccessToken();
			if (accessToken.isEmpty())
			{
				// Is the permanent change acceptable?
				Log.i(LOG_TAG, "Authentication failed, switching to Daily Ranking");
				sharedPrefs.edit().putString("pref_updateMode", "daily_rank").apply();
				mode = "daily_rank";
			} else
			{
				Log.i(LOG_TAG, "Authentication success");
			}
		}

		try
		{
			if (mode.equals("follow") || mode.equals("bookmark"))
			{
				rankingResponse = sendGetRequestAuth(
						getUpdateUriInfo(mode, sharedPrefs.getString("userId", "")),
						accessToken);
			} else
			{
				rankingResponse = sendGetRequest(getUpdateUriInfo(mode, ""));
			}

			// If HTTP code was anything other than 200 ... 301, failure
			if (!rankingResponse.isSuccessful())
			{
				Log.e(LOG_TAG, "HTTP error: " + rankingResponse.code());
				JSONObject errorBody = new JSONObject(rankingResponse.body().string());
				Log.e(LOG_TAG, errorBody.toString());
				Log.e(LOG_TAG, "Could not get overall ranking JSON");
				rankingResponse.close();
				return;
			}

			overallJson = new JSONObject((rankingResponse.body().string()));
			rankingResponse.close();

			Random random = new Random();
			// If mode determine
			if (mode.equals("follow") || mode.equals("bookmark"))
			{
				Log.d(LOG_TAG, "Feed or bookmark");
				// Raise an issue if this line proves too hard to understand
				// jfc this line is a mess
				pictureMetadata = overallJson
						.getJSONArray("illusts")
						.getJSONObject(random.nextInt(overallJson
								.getJSONArray("illusts")
								.length()));
				title = pictureMetadata.getString("title");
				byline = pictureMetadata.getJSONObject("user").getString("name");
				token = pictureMetadata.getString("id");

				// If picture pulled is a single image
				if (pictureMetadata.getJSONArray("meta_pages").length() == 0)
				{
					Log.d(LOG_TAG, "Picture is a single image");
					imageUrl = pictureMetadata.getJSONObject("meta_single_page")
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
				response = sendGetRequest(imageUrl);
			} else
			{
				Log.d(LOG_TAG, "Ranking");
				// Prevents manga or gifs from being chosen
				if(sharedPrefs.getBoolean("pref_illustType", false))
				{
					do
					{
						// Raise an issue if this line proves too hard to understand
						pictureMetadata = overallJson.getJSONArray("contents")
								.getJSONObject(random.nextInt(overallJson.getJSONArray("contents").length()));
					} while (pictureMetadata.getInt("illust_type") != 0);
				}
				else
				{
					pictureMetadata = overallJson.getJSONArray("contents")
							.getJSONObject(random.nextInt(overallJson.getJSONArray("contents").length()));
				}

				title = pictureMetadata.getString("title");
				byline = pictureMetadata.getString("user_name");
				token = pictureMetadata.getString("illust_id");
				String thumbUrl = pictureMetadata.getString(("url"));
				response = getRemoteFileExtension(thumbUrl);
			}
		} catch (IOException | JSONException ex)
		{
			Log.d(LOG_TAG, "error");
			Log.d(LOG_TAG, overallJson.toString());
			ex.printStackTrace();
			return;
		}

		String webUri = PixivArtProviderDefines.MEMBER_ILLUST_URL + token;

		Uri finalUri = downloadFile(response, token);
		response.close();
		addArtwork(new Artwork.Builder()
				.title(title)
				.byline(byline)
				.persistentUri(finalUri)
				.token(token)
				.webUri(Uri.parse(webUri))
				.build());
	}
}
