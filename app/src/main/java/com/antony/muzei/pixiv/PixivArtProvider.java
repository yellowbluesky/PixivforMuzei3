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

package com.antony.muzei.pixiv;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.RemoteActionCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.IconCompat;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.exceptions.AccessTokenAcquisitionException;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


public class PixivArtProvider extends MuzeiArtProvider
{
	// Pass true to clear cache and download new images
	// Pass false to append new images to cache
	@Override
	public void onLoadRequested(boolean clearCache)
	{
		PixivArtWorker.enqueueLoad(false);
	}

	@Override
	public List<RemoteActionCompat> getCommandActions(Artwork artwork)
	{
		List<RemoteActionCompat> list = null;
		list.add(shareImage(artwork));
		//list.add(viewArtworkDetailsAlternate(artwork));
		return list;
	}

	private RemoteActionCompat shareImage(Artwork artwork)
	{
		Log.d("ANTONY_WORKER", "Opening sharing ");
		File newFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), artwork.getToken() + ".png");
		Uri uri = FileProvider.getUriForFile(getContext(), "com.antony.muzei.pixiv.fileprovider", newFile);
		Intent sharingIntent = new Intent();
		sharingIntent.setAction(Intent.ACTION_SEND);
		sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
		sharingIntent.setType("image/jpg");
		//sharingIntent.putExtra(Intent.EXTRA_TEXT)
		String title = getContext().getString(R.string.command_shareImage);

		Intent chooserIntent = Intent.createChooser(sharingIntent, null);
		chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return new RemoteActionCompat(
				IconCompat.createWithResource(getContext(), R.drawable.muzei_launch_command),
				title,
				title,
				PendingIntent.getActivity(
						getContext(),
						(int) artwork.getId(),
						chooserIntent,
						PendingIntent.FLAG_UPDATE_CURRENT));
	}

	private RemoteActionCompat viewArtworkDetailsAlternate(Artwork artwork)
	{
		String token = artwork.getToken();
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
		return new RemoteActionCompat(IconCompat.createWithResource(getContext(), R.drawable.muzei_launch_command),
				getContext().getString(R.string.command_viewArtworkDetails),
				"sample Description",
				PendingIntent.getActivity(getContext(),
						0,
						intent,
						PendingIntent.FLAG_UPDATE_CURRENT));
	}

	// Provided you are logged in, adds the currently displayed images to your Pixiv bookmarks
	// Only works on Android 9 and lower, as Android 10 limits the ability to start activities in the background
	private void addToBookmarks(Artwork artwork)
	{
		Log.d("ANTONY_WORKER", "addToBookmarks(): Entered");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		String accessToken;
		try
		{
			accessToken = PixivArtService.refreshAccessToken(sharedPrefs);
		} catch (AccessTokenAcquisitionException e)
		{
			Log.d("ANTONY_WORKER", "No access token found");
			new Handler(Looper.getMainLooper()).post(() ->
					Toast.makeText(getContext(), getContext().getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show());
			return;
		}
		PixivArtService.sendBookmarkPostRequest(accessToken, artwork.getToken());
		Log.d("ANTONY_WORKER", "Added to bookmarks");
	}

	@Override
	@NonNull
	public InputStream openFile(@NonNull Artwork artwork)
	{
		InputStream inputStream = null;
		try
		{
			inputStream = getContext().getContentResolver().openInputStream(artwork.getPersistentUri());
		} catch (FileNotFoundException ex)
		{
			ex.printStackTrace();
		}
		return inputStream;
	}
}
