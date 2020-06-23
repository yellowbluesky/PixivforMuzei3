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
package com.antony.muzei.pixiv.settings.deleteArtwork

import android.content.Context
import android.net.Uri
import com.antony.muzei.pixiv.provider.PixivArtProvider
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.PERSISTENT_URI
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.TITLE
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.TOKEN
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient

object ArtworkContent {
    val ITEMS = mutableListOf<ArtworkItem>()
    val SELECTED_ITEMS = mutableListOf<ArtworkItem>()
    val SELECTED_POSITIONS = mutableListOf<Int>()

    fun populateListInitial(context: Context) {
        ITEMS.clear()
        val projection = arrayOf("token", "title", "persistent_uri")
        val conResUri = getProviderClient(context, PixivArtProvider::class.java).contentUri
        val cursor = context.contentResolver.query(conResUri, projection, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val token = cursor.getString(cursor.getColumnIndex(TOKEN))
                val title = cursor.getString(cursor.getColumnIndex(TITLE))
                val persistentUri = Uri.parse(cursor.getString(cursor.getColumnIndex(PERSISTENT_URI)))
                val artworkItem = ArtworkItem(token, title, persistentUri)
                ITEMS.add(artworkItem)
            }
            cursor.close()
        }

    }

    class ArtworkItem(val token: String, val title: String, val persistent_uri: Uri) {

        // Stores if the image has been selected
        //  So the image tinting will not be forgotten in the View gets recycled
        var selected = false

    }
}
