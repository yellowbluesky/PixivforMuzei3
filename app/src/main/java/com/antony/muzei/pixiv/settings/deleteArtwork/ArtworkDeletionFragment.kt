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

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.BuildConfig
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.PixivArtProvider
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.TOKEN
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.ceil

class ArtworkDeletionFragment : Fragment() {
    companion object {
        val SELECTED_ITEMS = mutableListOf<ArtworkItem>()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val linearLayoutView = inflater.inflate(R.layout.fragment_artwork_list, container, false)
        val recyclerView: RecyclerView = linearLayoutView.findViewById(R.id.list)
        val context = recyclerView.context

        // Dynamically sets number of grid columns
        // The ceiling gives a minimum of 2 columns, and scales well up to a Nexus 10 tablet (1280dp width)
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        recyclerView.layoutManager = GridLayoutManager(context, ceil(dpWidth.toDouble() / 200).toInt())
        val adapter = ArtworkDeletionAdapter(getInitialArtworkItemList(context))
        recyclerView.adapter = adapter

        // This FAB actions the delete operation from both the RecyclerView, and the
        //  ContentProvider storing the Artwork details.
        // All changes appear instantaneously
        val fab: FloatingActionButton = linearLayoutView.findViewById(R.id.fab)
        fab.setOnClickListener {
            // Early exit when no artworks are selected for deletion
            if (SELECTED_ITEMS.isEmpty()) {
                Snackbar.make(
                    requireView(), R.string.snackbar_selectArtworkFirst,
                    Snackbar.LENGTH_LONG
                )
                    .show()
                return@setOnClickListener
            }
            val numberDeleted = SELECTED_ITEMS.size

            // Deletes the artwork items from the ArrayList used as backing for the RecyclerView
            adapter.removeItems(SELECTED_ITEMS)

            // We insert the deleted artwork ID's
            val listOfDeletedIds: MutableList<DeletedArtworkIdEntity> = mutableListOf()

            // Now to delete the Artwork's themselves from the ContentProvider
            val operations = ArrayList<ContentProviderOperation>()
            val selection = "$TOKEN = ?"
            // Builds a new delete operation for every selected artwork
            for (artworkItem in SELECTED_ITEMS) {
                val operation = ContentProviderOperation
                    .newDelete(getProviderClient(context, PixivArtProvider::class.java).contentUri)
                    .withSelection(selection, arrayOf(artworkItem.token))
                    .build()
                operations.add(operation)

                // Used to remember which artworks have been deleted, so we don't download them again
                listOfDeletedIds.add(DeletedArtworkIdEntity(artworkItem.token))
            }
            SELECTED_ITEMS.clear()

            try {
                context.contentResolver.applyBatch(BuildConfig.APPLICATION_ID + ".provider", operations)
            } catch (e: RemoteException) {
                e.printStackTrace()
            } catch (e: OperationApplicationException) {
                e.printStackTrace()
            }

            val appDatabase = AppDatabase.getInstance(context)
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch(Dispatchers.IO) {
                appDatabase.deletedArtworkIdDao().insertDeletedArtworkId(listOfDeletedIds.toList())
            }

            // TODO also delete the files from the disk?

            Snackbar.make(
                requireView(), numberDeleted.toString() + " " + getString(R.string.snackbar_deletedArtworks),
                Snackbar.LENGTH_LONG
            ).show()
        }
        return linearLayoutView
    }

    private fun getInitialArtworkItemList(context: Context): MutableList<ArtworkItem> {
        val listOfArtworkItem = mutableListOf<ArtworkItem>()
        val projection = arrayOf("token", "title", "persistent_uri")
        val conResUri = getProviderClient(context, PixivArtProvider::class.java).contentUri
        val cursor = context.contentResolver.query(conResUri, projection, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val token = cursor.getString(cursor.getColumnIndexOrThrow(TOKEN))
                //val title = cursor.getString(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.TITLE))
                val persistentUri =
                    Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.PERSISTENT_URI)))
                listOfArtworkItem.add(ArtworkItem(token, persistentUri))
            }
            cursor.close()
        }
        return listOfArtworkItem
    }
}
