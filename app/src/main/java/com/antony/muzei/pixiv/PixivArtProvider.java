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
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;


public class PixivArtProvider extends MuzeiArtProvider
{
    private static final String LOG_TAG = "PIXIV_DEBUG";

    // Pass true to clear cache and download new images
    // Pass false to add new images to cache
    @Override
    protected void onLoadRequested(boolean initial)
    {
        PixivArtWorker.enqueueLoad(false);
    }

    // This method called on insertion of new images.
    @Override
    @NonNull
    public InputStream openFile(@NonNull Artwork artwork) throws IOException
    {
        Log.d(LOG_TAG, "openFile() overridden");
        TokenFilenameFilter tokenFilter = new TokenFilenameFilter(artwork.getToken());
        File[] listFiles = getContext().getExternalFilesDir(
                Environment.DIRECTORY_PICTURES).listFiles(tokenFilter);
        if(listFiles.length == 0)
        {
            throw new FileNotFoundException("No file with token: " + artwork.getToken());
        }
        return new FileInputStream(listFiles[0]);
    }

    public static class TokenFilenameFilter implements FilenameFilter
    {
        private String token;
        TokenFilenameFilter(String token)
        {
            this.token = token;
        }

        @Override
        public boolean accept(File dir, String name)
        {
            return name.startsWith(token);
        }
    }
}
