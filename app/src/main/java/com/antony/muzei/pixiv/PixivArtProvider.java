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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;


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
		UserCommand addToBookmark = new UserCommand(1, getContext().getString(R.string.command_addToBookmark));
		UserCommand openIntentImage = new UserCommand(2, "View artwork details");
		commands.add(addToBookmark);
		commands.add(openIntentImage);
		return commands;
	}

	@Override
	protected void onCommand(@NonNull Artwork artwork, int id)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		switch (id)
		{
			case 1:
				addToBookmarks(artwork);
				handler.post(() -> Toast.makeText(getContext(), "Adding to bookmarks", Toast.LENGTH_SHORT).show());
				break;
			case 2:
				String token = artwork.getToken();
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + token));
				intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(intent);
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
			new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), "Login first", Toast.LENGTH_SHORT).show());
			return;
		}
		PixivArtService.sendPostRequest(accessToken, artwork.getToken());
		Log.d("PIXIV_DEBUG", "Added to bookmarks");
	}

}
