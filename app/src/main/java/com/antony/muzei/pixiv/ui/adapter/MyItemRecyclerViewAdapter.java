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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.antony.muzei.pixiv.ui.adapter.ArtworkFragment.OnListFragmentInteractionListener;
import com.antony.muzei.pixiv.ui.adapter.dummy.DummyContent.DummyItem;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DummyItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class MyItemRecyclerViewAdapter extends RecyclerView.Adapter<MyItemRecyclerViewAdapter.ViewHolder>
{

	private final List<DummyItem> mValues;
	private final OnListFragmentInteractionListener mListener;

	public MyItemRecyclerViewAdapter(List<DummyItem> items, OnListFragmentInteractionListener listener)
	{
		mValues = items;
		mListener = listener;
	}

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
		holder.mIdView.setText(mValues.get(position).id);
		holder.mContentView.setText(mValues.get(position).content);

		holder.mView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (null != mListener)
				{
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
		public final TextView mIdView;
		public final TextView mContentView;
		public DummyItem mItem;

		public ViewHolder(View view)
		{
			super(view);
			mView = view;
			mIdView = (TextView) view.findViewById(R.id.item_number);
			mContentView = (TextView) view.findViewById(R.id.content);
		}

		@Override
		public String toString()
		{
			return super.toString() + " '" + mContentView.getText() + "'";
		}
	}
}
