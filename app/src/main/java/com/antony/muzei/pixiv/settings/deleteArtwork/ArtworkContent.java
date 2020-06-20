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

package com.antony.muzei.pixiv.settings.deleteArtwork;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.antony.muzei.pixiv.provider.PixivArtProvider;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import java.util.ArrayList;
import java.util.List;

public class ArtworkContent
{
    public static final List<ArtworkItem> ITEMS = new ArrayList<>();
    public static final List<ArtworkItem> SELECTED_ITEMS = new ArrayList<>();
    public static final List<Integer> SELECTED_POSITIONS = new ArrayList<>();

    public static void populateListInitial(Context context)
    {
        ITEMS.clear();

        String[] projection = {"token", "title", "persistent_uri"};
        Uri conResUri = ProviderContract.getProviderClient(context, PixivArtProvider.class).getContentUri();
        Cursor cursor = context.getContentResolver().query(conResUri, projection, null, null, null);

        assert cursor != null;
        while (cursor.moveToNext())
        {
            String token = cursor.getString(cursor.getColumnIndex(ProviderContract.Artwork.TOKEN));
            String title = cursor.getString(cursor.getColumnIndex(ProviderContract.Artwork.TITLE));
            Uri persistent_uri = Uri.parse(cursor.getString(cursor.getColumnIndex(ProviderContract.Artwork.PERSISTENT_URI)));

            ArtworkItem artworkItem = new ArtworkItem(token, title, persistent_uri);
            ITEMS.add(artworkItem);
        }
        cursor.close();
    }

    public static class ArtworkItem
    {
        public final String token;
        public final String title;
        public final Uri persistent_uri;
        // Stores if the image has been selected
        //  So the image tinting will not be forgotten in the View gets recycled
        public boolean selected = false;

        public ArtworkItem(String token, String title, Uri persistent_uri)
        {
            this.token = token;
            this.title = title;
            this.persistent_uri = persistent_uri;
        }
    }
}
