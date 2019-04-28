package com.antony.muzei.pixiv;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.app.Activity;

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
import java.net.URI;
import java.net.URL;
import java.util.Random;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PixivArtProvider extends MuzeiArtProvider
{
	private static final int LIMIT = 5;
	private static final String LOG_TAG = "PIXIV";
	private final String mode = "daily_rank";

	private String userId = "";

	private static final String[] IMAGE_SUFFIXS = {".png", ".jpg", ".gif",};

	// placeholder for future functions that require auth, such as bookmark or feed
	private boolean checkAuth()
	{
		return false;
	}

	private Uri getUpdateUriInfo()
	{
		Uri.Builder uri = new Uri.Builder();
		switch (mode)
		{
			case "follow":
				if (checkAuth())
				{
					uri.appendQueryParameter("url", PixivArtProviderDefines.FOLLOW_URL + "?restrict=public");
				} else
				{
					uri.appendQueryParameter("url", PixivArtProviderDefines.DAILY_RANKING_URL);
				}
				break;
			case "bookmark":
				if (checkAuth())
				{
					uri.appendQueryParameter("url", PixivArtProviderDefines.BOOKMARK_URL + "?user_id=" + this.userId + "&restrict=public");
				} else
				{
					uri.appendQueryParameter("url", PixivArtProviderDefines.DAILY_RANKING_URL);
				}
				break;
			case "weekly_rank":
				uri.appendQueryParameter("url", PixivArtProviderDefines.WEEKLY_RANKING_URL);
				break;
			case "monthly_rank":
				uri.appendQueryParameter("url", PixivArtProviderDefines.MONTHLY_RANKING_URL);
				break;
			case "daily_rank":
			default:
				uri.appendQueryParameter("url", PixivArtProviderDefines.DAILY_RANKING_URL);
		}
		return uri.build();
	}

	private Response sendGetRequest(String url) throws IOException
	{
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.build();

		Request.Builder builder = new Request.Builder()
				.addHeader("User-Agent",
						"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0")
				.addHeader("Referer", PixivArtProviderDefines.PIXIV_HOST)
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
			response.body().close();
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
		JSONObject overallJson;
		JSONArray contents;
		JSONObject pic0Meta;
		String title;
		String byline;
		String token;
		String thumbUri;

		try
		{
			Response rankingResponse = sendGetRequest(PixivArtProviderDefines.DAILY_RANKING_URL);
			if (!rankingResponse.isSuccessful())
			{
				Log.e(LOG_TAG, "Could not get overall ranking JSON");
				return;
			}

			overallJson = new JSONObject((rankingResponse.body().string()));
			contents = overallJson.getJSONArray("contents");

			Random random = new Random();
			int cursor = random.nextInt(contents.length());
			pic0Meta = contents.getJSONObject(cursor);

			title = pic0Meta.getString("title");
			byline = pic0Meta.getString("user_name");
			token = pic0Meta.getString("illust_id");
			thumbUri = pic0Meta.getString(("url"));
		} catch (IOException | JSONException ex)
		{
			Log.d(LOG_TAG, "error");
			ex.printStackTrace();
			return;
		}

		String webUri = "https://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token;

		Response response = getRemoteFileExtension(thumbUri);
		if (response == null)
		{
			Log.e(LOG_TAG, "could not get file extension from Pixiv");
		}

		Uri finalUri = downloadFile(response, token);

		addArtwork(new Artwork.Builder()
				.title(title)
				.byline(byline)
				.persistentUri(finalUri)
				.token(token)
				.webUri(Uri.parse(webUri))
				.build());
	}
}
