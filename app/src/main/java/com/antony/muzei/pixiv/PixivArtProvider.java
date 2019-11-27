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

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.util.LinkedList;
import java.util.List;


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
		commands.add(new UserCommand(1, "Artist's Page"));
		commands.add(new UserCommand(2, "Bookmark This Image"));
		return commands;
	}

	@Override
	protected void onCommand(@NonNull Artwork artwork, int id)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		switch (id)
		{
			case 1:
				handler.post(() -> Toast.makeText(getContext(), "Going to artist page", Toast.LENGTH_SHORT).show());
			case 2:
				handler.post(() -> Toast.makeText(getContext(), "loving it", Toast.LENGTH_SHORT).show());
		}
	}

}
