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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.antony.muzei.pixiv.R
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors

class ArtworkDeletionAdapter(private val artworkItems: MutableList<ArtworkItem>) :
    RecyclerView.Adapter<ArtworkDeletionAdapter.ViewHolder>() {
    // Call to inflate the view. In our case, this is fragment_artwork, which is only an ImageView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_artwork, parent, false)
        return ViewHolder(view)
    }

    // Called to populate or "bind" an ArtworkItem with an ImageView
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(artworkItems[position])
    }

    override fun getItemCount(): Int {
        return artworkItems.size
    }

    fun removeItems(artworkItemsToDelete: List<ArtworkItem>, positionsToDelete: List<Int>) {
        artworkItems.removeAll(artworkItemsToDelete)
        for ((deleteCount, position) in positionsToDelete.withIndex()) {
            // With each deletion, the backing artworkItems list shifts in size.
            // If we do not offset the deletion index, we will quickly encounter IndexOutOfBounds errors
            notifyItemRemoved(position - deleteCount)
        }
    }

    class ViewHolder(private val mView: View) : RecyclerView.ViewHolder(mView), View.OnClickListener {
        private val mImageView: ImageView = mView.findViewById(R.id.image)
        private lateinit var mArtworkItem: ArtworkItem

        private val color = MaterialColors.getColor(mView, R.attr.colorSecondaryVariant, Color.BLUE)

        fun bind(artworkItem: ArtworkItem) {
            mArtworkItem = artworkItem
            // Glide is used instead of simply setting the ImageView Uri directly
            //  Without Glide, performance was unacceptable because the ImageView was directly pulling
            //  the full size image and resizing it to fit the View every time it was asked to
            //  Glide caches the resized image, much more performant
            Glide.with(mView)
                .load(artworkItem.persistent_uri)
                .centerCrop()
                .into(mImageView)

            if (artworkItem.selected) {
                mImageView.setColorFilter(Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)))
            } else {
                mImageView.clearColorFilter()
            }
        }

        override fun onClick(view: View) {
            val position = absoluteAdapterPosition

            if (position != RecyclerView.NO_POSITION) {
                // Check if an item was deleted, but the user clicked it before the UI removed it
                // We can access the data within the views
                if (!mArtworkItem.selected) {
                    ArtworkDeletionFragment.SELECTED_ITEMS.add(mArtworkItem)
                    ArtworkDeletionFragment.SELECTED_POSITIONS.add(position)
                    mImageView.setColorFilter(Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)))
                    mArtworkItem.selected = true
                } else {
                    ArtworkDeletionFragment.SELECTED_ITEMS.remove(mArtworkItem)
                    ArtworkDeletionFragment.SELECTED_POSITIONS.remove(position)
                    mImageView.clearColorFilter()
                    mArtworkItem.selected = false
                }
            }
        }

        init {
            mView.setOnClickListener(this)
        }
    }
}
