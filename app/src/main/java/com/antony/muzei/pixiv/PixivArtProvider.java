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

import androidx.annotation.NonNull;
import androidx.core.app.RemoteActionCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.IconCompat;
import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


public class PixivArtProvider extends MuzeiArtProvider
{
	// Pass true to clear cache and download new images
	// Pass false to append new images to cache
	@Override
	public void onLoadRequested(boolean clearCache)
	{
		PixivArtWorker.enqueueLoad(false, getContext());
	}

	@Override
	public List<RemoteActionCompat> getCommandActions(Artwork artwork)
	{
		List<RemoteActionCompat> list = new ArrayList<>();
		list.add(shareImage(artwork));
		list.add(viewArtworkDetailsAlternate(artwork));
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		if (!sharedPrefs.getString("accessToken", "").isEmpty())
		{
			list.add(addToBookmarks(artwork));
		}
		return list;
	}

	private RemoteActionCompat shareImage(Artwork artwork)
	{
		File newFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), artwork.getToken() + ".png");
		Uri uri = FileProvider.getUriForFile(getContext(), "com.antony.muzei.pixiv.fileprovider", newFile);
		Intent sharingIntent = new Intent()
				.setAction(Intent.ACTION_SEND)
				.setType("image/*")
				.putExtra(Intent.EXTRA_STREAM, uri);

		Intent chooserIntent = Intent.createChooser(sharingIntent, "Share image using");
		chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		String title = getContext().getString(R.string.command_shareImage);
		RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
				null,
				title,
				title,
				PendingIntent.getActivity(
						getContext(),
						(int) artwork.getId(),
						chooserIntent,
						PendingIntent.FLAG_UPDATE_CURRENT));
		return remoteActionCompat;
	}

	private RemoteActionCompat viewArtworkDetailsAlternate(Artwork artwork)
	{
		String token = artwork.getToken();
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
		String title = getContext().getString(R.string.command_viewArtworkDetails);
		RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
				IconCompat.createWithResource(getContext(), R.drawable.muzei_launch_command),
				title,
				title,
				PendingIntent.getActivity(getContext(),
						(int) artwork.getId(),
						intent,
						PendingIntent.FLAG_UPDATE_CURRENT));
		remoteActionCompat.setShouldShowIcon(false);
		return remoteActionCompat;
	}

	private RemoteActionCompat addToBookmarks(Artwork artwork)
	{
		Intent addToBookmarkIntent = new Intent(getContext(), AddToBookmarkBroadcast.class);
		addToBookmarkIntent.putExtra("artworkId", artwork.getToken());
		PendingIntent pendingIntent = PendingIntent.getBroadcast(getContext(), 0, addToBookmarkIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		RemoteActionCompat remoteActionCompat = new RemoteActionCompat(IconCompat.createWithResource(getContext(), R.drawable.muzei_launch_command),
				"Add to bookmarks",
				"sample description",
				pendingIntent);
		remoteActionCompat.setShouldShowIcon(false);
		return remoteActionCompat;
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
