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
package com.antony.muzei.pixiv.settings.fragments

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.BookmarksHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


class BookmarkExportFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.bookmark_export_layout, rootKey)
        // setting up some prereqs
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val userId = sharedPrefs.getString("userId", "")

        // public bookmark
        val getPublicBookmarkPreference =
            findPreference<Preference>("button_exportBookmarkPublic") ?: return
        getPublicBookmarkPreference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                getBookmarks(true, userId!!, getPublicBookmarkPreference)

                Snackbar.make(requireView(), "public bookmarks exported", Snackbar.LENGTH_SHORT)
                    .show()
                true
            }

        // private BOOKMARK
        val getPrivateBookmarkPreference =
            findPreference<Preference>("button_exportBookmarkPrivate") ?: return
        getPrivateBookmarkPreference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                getBookmarks(false, userId!!, getPrivateBookmarkPreference)

                Snackbar.make(requireView(), "private bookmarks exported", Snackbar.LENGTH_SHORT)
                    .show()
                true
            }
    }

    private fun getBookmarks(publicBookmark: Boolean, userId: String, preference: Preference) {
        try {
            // NetworkOnMainThread exception
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch(Dispatchers.IO) {
                val bookmarks = BookmarksHelper(userId)

                if (publicBookmark) {
                    bookmarks.getNewPublicBookmarks()
                } else {
                    bookmarks.getNewPrivateIllusts()
                }

                val filename = if (publicBookmark) {
                    "bookmarksPublic.txt"
                } else {
                    "bookmarksPrivate.txt"
                }
                val textFile: File = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    filename
                ).also { it.writeText("") }

                var iterationCounter = 1
                while (true) {
                    // Iterate through list of artworks and add id's to list
                    for (artwork in bookmarks.getBookmarks().artworks) {
                        //Log.d("BOOKMARK", artwork.id.toString())
                        textFile.appendText("https://www.pixiv.net/en/artworks/${artwork.id}\n")
                    }
                    // Update the UI to show progress
                    withContext(Dispatchers.Main) {
                        preference.summary = "Saving page $iterationCounter"
                    }
                    iterationCounter++
                    // Delay of 500 ms to avoid overloading Pixiv network
                    delay(500L)
                    if (bookmarks.getBookmarks().next_url == null) {
                        break
                    }
                    bookmarks.getNextBookmarks()
                }
                withContext(Dispatchers.Main) {
                    preference.summary = "Completed saving artworks"
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }
}
