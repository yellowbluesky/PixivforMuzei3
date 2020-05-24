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

package com.antony.muzei.pixiv.ui.adapter;

import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.ui.fragments.ArtworkFragment.OnListFragmentInteractionListener;
import com.antony.muzei.pixiv.ArtworkContent.ArtworkItem;
import com.bumptech.glide.Glide;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtworkItemRecyclerViewAdapter extends RecyclerView.Adapter<ArtworkItemRecyclerViewAdapter.ViewHolder>
{

	private final List<ArtworkItem> mValues;
	private final OnListFragmentInteractionListener mListener;
	private Map<ArtworkItem, Boolean> selected = new HashMap<>();

	public ArtworkItemRecyclerViewAdapter(List<ArtworkItem> items, OnListFragmentInteractionListener listener)
	{
		mValues = items;
		mListener = listener;
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
		holder.mItem = mValues.get(position);
		//holder.mTitleView.setText(mValues.get(position).title);
		// Glide is used instead of simply setting the ImageView Uri directly
		//  Without Glide, performance was unacceptable because the ImageView was directly pulling
		//  the full size image and resizing it to fit the View every time it was asked to
		//  Glide caches the resized image, much more performant
		Glide.with(holder.mView)
				.load(mValues.get(position).persistent_uri)
				.centerCrop()
				.into(holder.mImageView);
		// holder.mImageView.setImageURI(mValues.get(position).persistent_uri);

		holder.mView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (null != mListener)
				{
					if (!selected.containsKey(holder.mItem) || !selected.get(holder.mItem))
					{
						selected.put(holder.mItem, true);
						holder.mImageView.setColorFilter(Color.argb(120, 0, 150, 250));
					} else
					{
						selected.put(holder.mItem, false);
						holder.mImageView.clearColorFilter();
					}
					// Notify the active callbacks interface (the activity, if the
					// fragment is attached to one) that an item has been selected.

					mListener.onListFragmentInteraction(holder.mItem);
				}
			}
		});
	}

	@Override
	public int getItemCount()
	{
		return mValues.size();
	}

	public class ViewHolder extends RecyclerView.ViewHolder
	{
		public final View mView;
		//public final TextView mTitleView;
		public final ImageView mImageView;
		public ArtworkItem mItem;

		public ViewHolder(View view)
		{
			super(view);
			mView = view;
			//mTitleView = view.findViewById(R.id.title);
			mImageView = view.findViewById(R.id.image);
			//mTitleView = view.findViewById(R.id.title);
		}

		@Override
		public String toString()
		{
			return super.toString();
		}
	}
}
