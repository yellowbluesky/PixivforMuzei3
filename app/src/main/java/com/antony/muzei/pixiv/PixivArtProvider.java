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

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.MuzeiContract;
import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;
import com.google.android.apps.muzei.api.provider.ProviderClient;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;


public class PixivArtProvider extends MuzeiArtProvider
{
	// Pass true to clear cache and download new images
	// Pass false to append new images to cache

	@Override
	protected void onLoadRequested(boolean initial)
	{
		PixivArtWorker.enqueueLoad(false);
	}

	@Override
	@NonNull
	protected List<UserCommand> getCommands(@NonNull Artwork artwork)
	{
		super.getCommands(artwork);
		LinkedList<UserCommand> commands = new LinkedList<>();
		commands.add(new UserCommand(1, "Add to Bookmarks"));
		return commands;
	}

	@Override
	protected void onCommand(@NonNull Artwork artwork, int id)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		switch (id)
		{
			case 1:
				ContentResolver conRes = getContext().getContentResolver();
				Uri conResUri = ProviderContract.getProviderClient(getContext(), PixivArtProvider.class).getContentUri();
				Cursor cursor = conRes.query(conResUri, null, null, null, null);
				ProviderClient client = ProviderContract.getProviderClient(getContext(), PixivArtProvider.class);
				if(cursor.moveToNext())
				{
					addToBookmarks(Artwork.fromCursor(cursor));
				}
				handler.post(() -> Toast.makeText(getContext(), "Adding to bookmarks", Toast.LENGTH_SHORT).show());
		}
	}

	private void addToBookmarks(Artwork artwork)
	{
		Log.d("PIXIV_DEBUG", "addToBookmarks(): Entered");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		String accessToken = sharedPrefs.getString("accessToken", "");
		if (accessToken.isEmpty())
		{
			Log.d("PIXIV_DEBUG", "No access token found");
			new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), "Login first", Toast.LENGTH_SHORT).show());
			return;
		}
		HttpUrl rankingUrl = new HttpUrl.Builder()
				.scheme("https")
				.host("app-api.pixiv.net")
				.addPathSegments("v2/illust/bookmark/add")
				.build();
		RequestBody authData = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("illust_id", artwork.getToken())
				.addFormDataPart("restrict", "public")
				.build();
		Request request = new Request.Builder()
				.addHeader("Content-Type", "application/x-www-form-urlencoded")
				.addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
				.addHeader("Authorization", "Bearer " + accessToken)
				.post(authData)
				.url(rankingUrl)
				.build();
		try
		{
			OkHttpClient httpClient = new OkHttpClient();
			httpClient.newCall(request).execute();
		} catch (IOException ex)
		{
			ex.printStackTrace();
		}
		Log.d("PIXIV_DEBUG", "Added to bookmarks");
	}

}
