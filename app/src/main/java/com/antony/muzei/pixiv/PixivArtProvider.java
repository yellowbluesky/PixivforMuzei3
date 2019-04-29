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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.app.Activity;

import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;
import com.google.android.apps.muzei.api.provider.Artwork;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.R.string;

import static android.content.Context.MODE_PRIVATE;

public class PixivArtProvider extends MuzeiArtProvider
{
	private static final int LIMIT = 5;
	private static final String LOG_TAG = "PIXIV";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};

	// Returns true when an acccess token is found or successfully obtained
	private Boolean getAccessToken()
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		// If we possess an access token, AND it has not expired
//		if (!sharedPreferences.getString("accessToken", "").isEmpty() &&
//				sharedPreferences.getLong("accessTokenIssueTime", 0) < System.currentTimeMillis() - 3600)
//		{
//			return true;
//		}

		Uri.Builder authQueryBuilder = new Uri.Builder()
				.appendQueryParameter("get_secure_url", Integer.toString(1))
				.appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
				.appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET);

		// If we did not have an access token or if it had expired, we proceed to build a request to acquire one
		//if (sharedPreferences.getString("refreshToken", "").isEmpty())
		if (true)
		{
			Log.i(LOG_TAG, "No refresh token found, proceeding with username / password authentication");
			authQueryBuilder.appendQueryParameter("grant_type", "password")
					.appendQueryParameter("username", sharedPreferences.getString("pref_loginId", ""))
					.appendQueryParameter("password", sharedPreferences.getString("pref_loginPassword", ""));
		} else
		{
			Log.i(LOG_TAG, "Found refresh token, using it to request an access token");
			authQueryBuilder.appendQueryParameter("grant_type", "refresh_token")
					.appendQueryParameter("refresh_token", sharedPreferences.getString("refreshToken", ""));
		}

		Uri authQuery = authQueryBuilder.build();

		try
		{
			Response response = sendPostRequest(PixivArtProviderDefines.OAUTH_URL, authQuery, "");
			JSONObject authResponseBody = new JSONObject(response.body().string());
			// Check if error here
			if (authResponseBody.has("has_error"))
			{
				// TODO maybe a toast message indicating error
				response.close();
				Log.i(LOG_TAG, "Error authenticating, check username or password");
				return false;
			}
			JSONObject tokens = authResponseBody.getJSONObject("response");
			response.close();

			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("accessToken", tokens.getString("access_token"));
			editor.putLong("accessTokenIssueTime", System.currentTimeMillis());
			editor.putString("refreshToken", tokens.getString("refresh_token"));
			editor.putString("userId", tokens.getJSONObject("user").getString("id"));
			editor.putString("deviceToken", tokens.getString("device_token"));
			editor.commit();
		} catch (IOException | JSONException ex)
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	private Response sendPostRequest(String url, Uri authQuery, String accessToken) throws IOException
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

	private Response sendGetRequestAuth(String url, String accessToken) throws IOException
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
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

	private Uri downloadFile(Response response, String token)
	{
		Context context = getContext();
		File downloadedFile = new File(context.getExternalCacheDir(), token + ".png");
		FileOutputStream fileStream = null;
		InputStream inputStream = null;
		try
		{
			fileStream = new FileOutputStream(downloadedFile);
			inputStream = response.body().byteStream();
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

	private Response getRemoteFileExtension(String url)
	{
		Response response;

		String uri0 = url.replace("c/240x480/", "");
		String uri1 = uri0.replace("img-master", "img-original");
		String uri2 = uri1.replace("_master1200", "");
		String uri3 = uri2.substring(0, uri2.length() - 4);
		for (String suffix : IMAGE_SUFFIXS)
		{
			String uri = uri3 + suffix;
			try
			{
				response = sendGetRequest(uri);
				if (response.code() == 200)
				{
					return response;
				}
			} catch (IOException e)
			{
				return null;
			}
		}
		return null;
	}

	@Override
	protected void onLoadRequested(boolean initial)
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String mode = sharedPreferences.getString("pref_updateMode", "");
		Log.d(LOG_TAG, "mode: " + mode);
		JSONObject overallJson, pictureMetadata;
		JSONArray contents;
		String title, byline, token, thumbUri, imageUrl;
		Response response, rankingResponse;

		try
		{
			if (mode.equals("follow") || mode.equals("bookmark"))
			{
				rankingResponse = sendGetRequestAuth(
						getUpdateUriInfo(
								sharedPreferences.getString("pref_updateMode", ""),
								sharedPreferences.getString("userId", "")),
						sharedPreferences.getString("accessToken", ""));
			} else
			{
				rankingResponse = sendGetRequest(
						getUpdateUriInfo(
								sharedPreferences.getString("pref_updateMode", ""),
								""));
			}

			// If HTTP code was anything other than 200 - 301, failure
			if (!rankingResponse.isSuccessful())
			{
				Log.e(LOG_TAG, "HTTP error: " + rankingResponse.code());
				JSONObject errorBody = new JSONObject(rankingResponse.body().string());
				Log.e(LOG_TAG, errorBody.toString());
				Log.e(LOG_TAG, "Could not get overall ranking JSON");
				return;
			}

			overallJson = new JSONObject((rankingResponse.body().string()));
			rankingResponse.close();

			Random random = new Random();
			if (!mode.equals("follow") || !mode.equals("bookmark"))
			{
				Log.d(LOG_TAG, "ranking");
				// Prevents manga or gifs from being chosen
				// TODO make this a setting
				do
				{
					pictureMetadata = overallJson.getJSONArray("contents")
							.getJSONObject(random.nextInt(50));
				} while (pictureMetadata.getInt("illust_type") != 0);
				Log.d(LOG_TAG, "ranking");
				title = pictureMetadata.getString("title");
				byline = pictureMetadata.getString("user_name");
				token = pictureMetadata.getString("illust_id");
				String thumbUrl = pictureMetadata.getString(("url"));
				response = getRemoteFileExtension(thumbUrl);
			}
			// pictures from follow feed or bookmark
			else
			{
				Log.d(LOG_TAG, "feed or bookmark");
				pictureMetadata = overallJson.getJSONArray("illusts")
						.getJSONObject(random.nextInt(50));
				title = pictureMetadata.getString("title");
				byline = pictureMetadata.getJSONObject("user").getString("name");
				token = pictureMetadata.getString("id");

				// picture pulled is a single image
				if (pictureMetadata.getJSONArray("meta_pages").length() == 0)
				{
					Log.d(LOG_TAG, "single image");
					imageUrl = pictureMetadata.getJSONObject("meta_single_page")
							.getString("original_image_url");
				}
				// we have pulled an album, picking the first picture in album
				else
				{
					Log.d(LOG_TAG, "album");
					imageUrl = pictureMetadata
							.getJSONArray("meta_pages")
							.getJSONObject(0)
							.getJSONObject("image_urls")
							.getString("original");
				}
				response = sendGetRequest(imageUrl);
			}
		} catch (IOException | JSONException ex)
		{
			Log.d(LOG_TAG, "error");
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
