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

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.settings.deleteArtwork.ArtworkContent.ArtworkItem;
import com.bumptech.glide.Glide;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArtworkItemRecyclerViewAdapter extends RecyclerView.Adapter<ArtworkItemRecyclerViewAdapter.ViewHolder>
{

    private final List<ArtworkItem> artworkItems;

    public ArtworkItemRecyclerViewAdapter(List<ArtworkItem> items)
    {
        artworkItems = items;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_artwork, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position)
    {
        holder.mItem = artworkItems.get(position);
        //holder.mTitleView.setText(mValues.get(position).title);
        // Glide is used instead of simply setting the ImageView Uri directly
        //  Without Glide, performance was unacceptable because the ImageView was directly pulling
        //  the full size image and resizing it to fit the View every time it was asked to
        //  Glide caches the resized image, much more performant
        Glide.with(holder.mView)
                .load(artworkItems.get(position).persistent_uri)
                .centerCrop()
                .into(holder.mImageView);

        if (holder.mItem.selected)
        {
            holder.mImageView.setColorFilter(Color.argb(130, 0, 150, 250));
        } else
        {
            holder.mImageView.clearColorFilter();
        }
    }

    @Override
    public int getItemCount()
    {
        return artworkItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        public final View mView;
        //public final TextView mTitleView;
        public final ImageView mImageView;
        public ArtworkItem mItem;

        public ViewHolder(View itemView)
        {
            super(itemView);
            mView = itemView;
            mImageView = itemView.findViewById(R.id.image);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view)
        {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION)
            {
                // Check if an item was deleted, but the user clicked it before the UI removed it
                // We can access the data within the views
                if (!mItem.selected)
                {
                    ArtworkContent.SELECTED_ITEMS.add(mItem);
                    ArtworkContent.SELECTED_POSITIONS.add((Integer) position);
                    mImageView.setColorFilter(Color.argb(130, 0, 150, 250));
                    mItem.selected = true;
                } else
                {
                    ArtworkContent.SELECTED_ITEMS.remove(mItem);
                    ArtworkContent.SELECTED_POSITIONS.remove((Integer) position);
                    mImageView.clearColorFilter();
                    mItem.selected = false;
                }
                Log.v("CLICK", Integer.toString(position));
            }
        }
    }
}
