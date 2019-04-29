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
	private final String mode = "daily_rank";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};

	private boolean checkAuth()
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if (!sharedPreferences.getBoolean("pref_useAuth", false))
		{
			Log.d(LOG_TAG, "Authentication not needed, how did you even get here?");
			return false;
		}
		String loginId = sharedPreferences.getString("pref_loginId", "");
		String loginPassword = sharedPreferences.getString("pref_loginPassword", "");
		if (loginId.isEmpty() || loginPassword.isEmpty())
		{
			Log.e(LOG_TAG, "Username or password is empty");
			return false;
		}

		String refreshToken = sharedPreferences.getString("refreshToken", "");

		Uri.Builder authQueryBuilder = new Uri.Builder()
				.appendQueryParameter("get_secure_url", Integer.toString(1))
				.appendQueryParameter("client_id", PixivArtProviderDefines.CLIENT_ID)
				.appendQueryParameter("client_secret", PixivArtProviderDefines.CLIENT_SECRET);

		//if (refreshToken.isEmpty())
		if (true)
		{
			Log.d(LOG_TAG, "No refresh token found, proceeding with username / password authentication");
			authQueryBuilder.appendQueryParameter("grant_type", "password")
					.appendQueryParameter("username", loginId)
					.appendQueryParameter("password", loginPassword);
		} else
		{
			Log.d(LOG_TAG, "found refresh token");
			authQueryBuilder.appendQueryParameter("grant_type", "refresh_token")
					.appendQueryParameter("refresh_token", refreshToken);
		}
		Uri authQuery = authQueryBuilder.build();

		Response response;
		JSONObject authResponse;
		JSONObject tokens = new JSONObject();
		try
		{
			response = sendPostRequest(PixivArtProviderDefines.OAUTH_URL, authQuery, "");
			authResponse = new JSONObject(response.body().string());
			tokens = authResponse.getJSONObject("response");

			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("accessToken", tokens.getString("access_token"));
			editor.putString("refreshToken", tokens.getString("refresh_token"));
			editor.putString("deviceToken", tokens.getString("device_token"));
			editor.apply();
		} catch (IOException | JSONException ex)
		{
			ex.printStackTrace();
			return false;
		}
		Log.i(LOG_TAG, "Successfully authorised");

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
				.addHeader("Authorization", "Bearer " + accessToken)
				.post(body)
				.url(url);
		return httpClient.newCall(builder.build()).execute();
	}

	private String getUpdateUriInfo()
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String urlString;
		String mode = sharedPreferences.getString("pref_updateMode", "");
		String userId = sharedPreferences.getString("pref_loginId", "");
		switch (mode)
		{
			case "follow":
				if (checkAuth())
				{
					urlString = PixivArtProviderDefines.FOLLOW_URL + "?restrict=public";
				} else
				{
					urlString = PixivArtProviderDefines.DAILY_RANKING_URL;
				}
				break;
			case "bookmark":
				if (checkAuth())
				{
					urlString = PixivArtProviderDefines.BOOKMARK_URL + "?user_id=" + userId + "&restrict=public";
				} else
				{
					urlString = PixivArtProviderDefines.DAILY_RANKING_URL;
				}
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

	private Response sendGetRequestAuth(String url) throws IOException
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.build();

		Request.Builder builder = new Request.Builder()
				.addHeader("User-Agent", "PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)")
				.addHeader("App-OS", "ios")
				.addHeader("App-OS-Version", "10.3.1")
				.addHeader("App-Version", "6.9.0")
				.addHeader("Authorization", "Bearer " + sharedPreferences.getString("accessToken", ""))
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

	private JSONArray getContents(JSONObject ranking)
	{
		try
		{
			JSONArray contents = ranking.getJSONArray("contents");
			return contents;
		} catch (JSONException ex)
		{
			ex.printStackTrace();
		}
		try
		{
			JSONArray contents = ranking.getJSONArray("illusts");
			return contents;
		} catch (JSONException ex)
		{
			ex.printStackTrace();
		}
		return null;
	}

	@Override
	protected void onLoadRequested(boolean initial)
	{
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String mode = sharedPreferences.getString("pref_updateMode", "");
		Log.d(LOG_TAG, "mode: " + mode);
		JSONObject overallJson;
		JSONArray contents;
		JSONObject pictureMetadata;
		String title;
		String byline;
		String token;
		String thumbUri;
		String imageUrl;
		Response response;

		try
		{
			String url = getUpdateUriInfo();
			Response rankingResponse;
			if (mode.equals("follow") || mode.equals("bookmark"))
			{
				rankingResponse = sendGetRequestAuth(url);
			} else
			{
				rankingResponse = sendGetRequest(url);
			}

			if (!rankingResponse.isSuccessful())
			{
				Log.e(LOG_TAG, "Could not get overall ranking JSON");
				return;
			}

			overallJson = new JSONObject((rankingResponse.body().string()));
			Log.d(LOG_TAG, overallJson.toString());
			rankingResponse.close();
			contents = getContents(overallJson);


			// Prevent manga or gifs from being chosen
			Random random = new Random();
			//do
			{
				pictureMetadata = contents.getJSONObject(random.nextInt(contents.length()));
			} //while (pictureMetadata.getInt("illust_type") != 0);


			if (!mode.equals("follow") || !mode.equals("bookmark"))
			{
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
				title = pictureMetadata.getString("title");
				JSONObject usernameObject = pictureMetadata.getJSONObject("user");
				byline = usernameObject.getString("name");
				token = pictureMetadata.getString("id");
				// picture pulled is a single image
				if (pictureMetadata.getJSONArray("meta_pages").length() == 0)
				{
					Log.d(LOG_TAG, "single image");
					JSONObject imageJsonObject = pictureMetadata.getJSONObject("meta_single_page");
					imageUrl = imageJsonObject.getString("original_image_url");
				}
				// we have pulled an album, picking the first picture in album
				else
				{
					Log.d(LOG_TAG, "album");
					JSONArray albumArray = pictureMetadata.getJSONArray("meta_pages");
					JSONObject imageUrls = albumArray.getJSONObject(0);
					JSONObject mainPictureUrls = imageUrls.getJSONObject("image_urls");
					imageUrl = mainPictureUrls.getString("original");
				}
				response = sendGetRequest(imageUrl);
			}

			title = pictureMetadata.getString("title");
			byline = pictureMetadata.getString("user_name");
			token = pictureMetadata.getString("illust_id");
			thumbUri = pictureMetadata.getString(("url"));
		} catch (IOException | JSONException ex)
		{
			Log.d(LOG_TAG, "error");
			ex.printStackTrace();
			return;
		}

		String webUri = PixivArtProviderDefines.MEMBER_ILLUST_URL + token;

		if (response == null)
		{
			Log.e(LOG_TAG, "could not get file extension from Pixiv");
		}

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
