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

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


public class PixivArtProvider extends MuzeiArtProvider
{
	// Pass true to clear cache and download new images
	// Pass false to append new images to cache

	private final int COMMAND_ADD_TO_BOOKMARKS = 1;
	private final int COMMAND_VIEW_IMAGE_DETAILS = 2;
	private final int COMMAND_SHARE_IMAGE = 3;

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
	protected void onCommand(@NonNull Artwork artwork, int id)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		switch (id)
		{
			case COMMAND_ADD_TO_BOOKMARKS:
				addToBookmarks(artwork);
				handler.post(() -> Toast.makeText(getContext(), getContext().getString(R.string.toast_addingToBookmarks), Toast.LENGTH_SHORT).show());
				break;
			case COMMAND_VIEW_IMAGE_DETAILS:
				String token = artwork.getToken();
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
				intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(intent);
				break;
			case COMMAND_SHARE_IMAGE:
				// Sharing feature doesn't work very well on Android 10
				// Android 10 restricts background activity startup, and the chooser is started as a
				// PixivForMuzei3 activity while muzei is the active activity.
				// To get it to work, the user must open the PixivForMuzei3 Settings activity,
				// navigate back out, then go and share
				// Works well on Android versions lower than 10
				shareImage(artwork);
				break;
		}
	}

	private void addToBookmarks(Artwork artwork)
	{
		Log.d("PIXIV_DEBUG", "addToBookmarks(): Entered");
		String accessToken = PixivArtService.getAccesToken(PreferenceManager.getDefaultSharedPreferences(getContext()));
		if (accessToken.isEmpty())
		{
			Log.d("PIXIV_DEBUG", "No access token found");
			new Handler(Looper.getMainLooper()).post(() ->
					Toast.makeText(getContext(), getContext().getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show());
			return;
		}
		PixivArtService.sendPostRequest(accessToken, artwork.getToken());
		Log.d("PIXIV_DEBUG", "Added to bookmarks");
	}

	private void shareImage(Artwork artwork)
	{
		Log.d("PIXIV_DEBUG", "Opening sharing ");
		File newFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), artwork.getToken() + ".png");
		Uri uri = FileProvider.getUriForFile(getContext(), "com.antony.muzei.pixiv.fileprovider", newFile);
		Intent sharingIntent = new Intent();
		sharingIntent.setAction(Intent.ACTION_SEND);
		sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
		sharingIntent.setType("image/jpg");

		Intent chooserIntent = Intent.createChooser(sharingIntent, null);
		chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		getContext().startActivity(chooserIntent);
	}
}
