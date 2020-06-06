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
import android.os.Build;
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
import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


public class PixivArtProvider extends MuzeiArtProvider
{
	private final int COMMAND_ADD_TO_BOOKMARKS = 1;
	private final int COMMAND_VIEW_IMAGE_DETAILS = 2;
	private final int COMMAND_SHARE_IMAGE = 3;

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
		//if (!sharedPrefs.getString("accessToken", "").isEmpty())
		{
			list.add(addToBookmarks(artwork));
		}
		return list;
	}

	/*
		Deprecated methods
	 */

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
		return new RemoteActionCompat(
				IconCompat.createWithResource(getContext(), R.drawable.ic_baseline_share_24),
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
		Log.v("BOOKMARK", "adding to bookmarks");
		Intent addToBookmarkIntent = new Intent(getContext(), AddToBookmarkService.class);
		addToBookmarkIntent.putExtra("artworkId", artwork.getToken());
		PendingIntent pendingIntent = PendingIntent.getService(getContext(), 0, addToBookmarkIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		RemoteActionCompat remoteActionCompat = new RemoteActionCompat(
				IconCompat.createWithResource(getContext(), R.drawable.muzei_launch_command),
				"Add to bookmarks",
				"sample description",
				pendingIntent);
		remoteActionCompat.setShouldShowIcon(false);
		return remoteActionCompat;
	}

	// Deprecated in Muzei
	@Override
	@NonNull
	public List<UserCommand> getCommands(@NonNull Artwork artwork)
	{
		super.getCommands(artwork);
		LinkedList<UserCommand> commands = new LinkedList<>();
		// Android 10 limits the ability for activities to run in the background
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
		{
			UserCommand addToBookmark = new UserCommand(COMMAND_ADD_TO_BOOKMARKS, getContext().getString(R.string.command_addToBookmark));
			commands.add(addToBookmark);
			UserCommand openIntentImage = new UserCommand(COMMAND_VIEW_IMAGE_DETAILS, getContext().getString(R.string.command_viewArtworkDetails));
			UserCommand shareImage = new UserCommand(COMMAND_SHARE_IMAGE, getContext().getString(R.string.command_shareImage));
			commands.add(shareImage);
			commands.add(openIntentImage);
		}
		return commands;
	}

	@Override
	public void onCommand(@NonNull Artwork artwork, int id)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		switch (id)
		{
			case COMMAND_ADD_TO_BOOKMARKS:
				Log.d("PIXIV_DEBUG", "addToBookmarks(): Entered");
				SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
				if (sharedPrefs.getString("accessToken", "").isEmpty())
				{
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(getContext(), getContext().getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show());
					return;
				}

				String accessToken;
				try
				{
					accessToken = PixivArtService.refreshAccessToken(sharedPrefs);
				} catch (AccessTokenAcquisitionException e)
				{
					new Handler(Looper.getMainLooper()).post(() ->
							Toast.makeText(getContext(), getContext().getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show());
					return;
				}
				PixivArtService.sendPostRequest(accessToken, artwork.getToken());
				Log.d("PIXIV_DEBUG", "Added to bookmarks");
				handler.post(() -> Toast.makeText(getContext(), getContext().getString(R.string.toast_addingToBookmarks), Toast.LENGTH_SHORT).show());
				break;
			case COMMAND_VIEW_IMAGE_DETAILS:
				String token = artwork.getToken();
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
				intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(intent);
				break;
			case COMMAND_SHARE_IMAGE:
				Log.d("ANTONY_WORKER", "Opening sharing ");
				File newFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), artwork.getToken() + ".png");
				Uri uri = FileProvider.getUriForFile(getContext(), "com.antony.muzei.pixiv.fileprovider", newFile);
				Intent sharingIntent = new Intent();
				sharingIntent.setAction(Intent.ACTION_SEND);
				sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
				sharingIntent.setType("image/jpg");

				Intent chooserIntent = Intent.createChooser(sharingIntent, null);
				chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(chooserIntent);
				break;
		}
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
