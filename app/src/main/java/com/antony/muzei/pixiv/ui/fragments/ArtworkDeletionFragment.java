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

package com.antony.muzei.pixiv.ui.fragments;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.antony.muzei.pixiv.ArtworkContent;
import com.antony.muzei.pixiv.PixivArtProvider;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.ui.adapter.ArtworkItemRecyclerViewAdapter;
import com.google.android.apps.muzei.api.provider.ProviderContract;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ArtworkDeletionFragment extends Fragment
{


	private static final String ARG_COLUMN_COUNT = "column-count";

	private int mColumnCount = 1;

	private List<ArtworkContent.ArtworkItem> selectedArtworks = new ArrayList<>();
	private List<Integer> selectedPositions = new ArrayList<>();

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public ArtworkDeletionFragment()
	{
	}

	// TODO: Customize parameter initialization
	@SuppressWarnings("unused")
	public static ArtworkDeletionFragment newInstance(int columnCount)
	{
		ArtworkDeletionFragment fragment = new ArtworkDeletionFragment();
		Bundle args = new Bundle();
		args.putInt(ARG_COLUMN_COUNT, columnCount);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ArtworkContent.populateListInitial(getContext());

		if (getArguments() != null)
		{
			mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState)
	{
		View linearLayoutView = inflater.inflate(R.layout.fragment_artwork_list, container, false);

		// The boilerplate list fragment code had the RecyclerView as the only View in the layout xml
		// I have since added a parent View for the RecyclerView (ConstraintLayout)
		Context context = linearLayoutView.getContext();
		RecyclerView recyclerView = linearLayoutView.findViewById(R.id.list);
		// TODO figure out a better way to size each image grid square
		//  Currently have it hardcoded to 200dp
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
		{
			recyclerView.setLayoutManager(new GridLayoutManager(context, 4));
		} else
		{
			recyclerView.setLayoutManager(new GridLayoutManager(context, 2));
		}

		ArtworkItemRecyclerViewAdapter adapter = new ArtworkItemRecyclerViewAdapter(ArtworkContent.ITEMS);
		adapter.setOnItemClickListener((itemView, position) ->
		{
			ArtworkContent.ArtworkItem item = ArtworkContent.ITEMS.get(position);
			ImageView imageView = itemView.findViewById(R.id.image);
			if (!selectedArtworks.contains(item))
			{
				selectedArtworks.add(item);
				selectedPositions.add((Integer) position);
				imageView.setColorFilter(Color.argb(130, 0, 150, 250));
				// TODO i've got three variables storing which artworks have been selected, and all over the place
				//  try to cut it down
				item.selected = true;
			} else
			{
				selectedArtworks.remove(item);
				selectedPositions.remove((Integer) position);
				imageView.clearColorFilter();
				item.selected = false;
			}
			Log.v("POSITION", Integer.toString(position));
		});
		recyclerView.setAdapter(adapter);

		// This FAB actions the delete operation from both the RecyclerView, and the
		//  ContentProvider storing the Artwork details.
		// All changes appear instantaneously
		FloatingActionButton fab = linearLayoutView.findViewById(R.id.fab);
		fab.setOnClickListener(view ->
		{
			// Optimization if no artworks are selected
			if (selectedArtworks.isEmpty())
			{
				Snackbar.make(view, R.string.snackbar_selectArtworkFirst,
						Snackbar.LENGTH_LONG)
						.show();
				return;
			} else
			{
				int numberDeleted = selectedArtworks.size();
				Snackbar.make(view, numberDeleted + " " + getString(R.string.snackbar_deletedArtworks),
						Snackbar.LENGTH_LONG)
						.show();
			}

			// Deletes the artwork items from the ArrayList used as backing for the RecyclerView
			ArtworkContent.ITEMS.removeAll(selectedArtworks);

			// Updates the RecyclerView.Adapter by removing the selected images
			// Nice animation as we're not calling notifyDataSetChanged()
			int deleteCount = 0;
			for (int i : selectedPositions)
			{
				adapter.notifyItemRemoved(i - deleteCount);
				deleteCount++;
			}
			selectedPositions.clear();

			// Now to delete the Artwork's themselves from the ContentProvider
			ArrayList<ContentProviderOperation> operations = new ArrayList<>();
			ContentProviderOperation operation;
			String selection = ProviderContract.Artwork.TOKEN + " = ?";
			// Builds a new delete operation for every selected artwork
			for (ArtworkContent.ArtworkItem artworkItem : selectedArtworks)
			{
				operation = ContentProviderOperation
						.newDelete(ProviderContract.getProviderClient(context, PixivArtProvider.class).getContentUri())
						.withSelection(selection, new String[]{artworkItem.token})
						.build();
				operations.add(operation);
			}

			try
			{
				context.getContentResolver().applyBatch("com.antony.muzei.pixiv.provider", operations);
			} catch (RemoteException | OperationApplicationException e)
			{
				e.printStackTrace();
			}

			// TODO also delete the files?

			selectedArtworks.clear();
		});

		return linearLayoutView;
	}


	@Override
	public void onAttach(@NotNull Context context)
	{
		super.onAttach(context);
	}

	@Override
	public void onDetach()
	{
		super.onDetach();
		//mListener = null;
	}
}
