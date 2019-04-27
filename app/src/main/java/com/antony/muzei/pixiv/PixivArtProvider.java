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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PixivArtProvider extends MuzeiArtProvider
{
	private final String mode = "daily_rank";

	private String userId = "";

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

		Response response = httpClient.newCall(builder.build()).execute();
		return response;
	}

	private Uri downloadFile(String url, String token)
	{
		Response imageResponse;
		Context context = getContext();
		File downloadedFile = new File(context.getCacheDir(), token + ".png");
		FileOutputStream fileStream = null;
		try
		{
			imageResponse = sendGetRequest(url);
			fileStream = new FileOutputStream(downloadedFile);
			final InputStream inputStream = imageResponse.body().byteStream();
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
		}

		return Uri.fromFile(downloadedFile);
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
		String originalUri;

		try
		{
			Response rankingResponse = sendGetRequest(PixivArtProviderDefines.DAILY_RANKING_URL);
			if (!rankingResponse.isSuccessful())
			{
				Log.e("PIXIV", "Could not get overall ranking JSON");
				return;
			}

			overallJson = new JSONObject((rankingResponse.body().string()));
			contents = overallJson.getJSONArray("contents");

			pic0Meta = contents.getJSONObject(2);
			title = pic0Meta.getString("title");
			byline = pic0Meta.getString("user_name");
			token = pic0Meta.getString("illust_id");
			originalUri = pic0Meta.getString(("url"));
			Log.i("PIXIV", title);
			Log.i("PIXIV", token);
		} catch (IOException | JSONException ex)
		{
			Log.d("PIXIV", "error");
			ex.printStackTrace();
			return;
		}

		String uri0 = originalUri.replace("c/240x480/", "");
		String uri1 = uri0.replace("img-master", "img-original");
		String uri2 = uri1.replace("_master1200", "");
		String uri3 = uri2.replace("jpg", "png");
		Log.i("PIXIV", uri3);

		String webUri = "https://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token;

		Uri finalUri = downloadFile(uri3, token);

		Log.i("PIXIV", finalUri.toString());

		setArtwork(new Artwork.Builder()
				.title(title)
				.byline(byline)
				.token(token)
				.persistentUri(finalUri)
				.webUri(Uri.parse(webUri))
				.build());
	}
}
