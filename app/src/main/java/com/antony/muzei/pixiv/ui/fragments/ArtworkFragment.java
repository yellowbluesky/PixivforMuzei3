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
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
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

public class ArtworkFragment extends Fragment
{


	private static final String ARG_COLUMN_COUNT = "column-count";

	private int mColumnCount = 1;

	private List<ArtworkContent.ArtworkItem> selectedArtworks = new ArrayList<>();

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public ArtworkFragment()
	{
	}

	// TODO: Customize parameter initialization
	@SuppressWarnings("unused")
	public static ArtworkFragment newInstance(int columnCount)
	{
		ArtworkFragment fragment = new ArtworkFragment();
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
		View view = inflater.inflate(R.layout.fragment_artwork_list, container, false);

		// The boilerplate list fragment code had the RecyclerView as the only View in the layout xml
		// I have since added a parent View for the RecyclerView (ConstraintLayout)
		Context context = view.getContext();
		RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.list);
		// TODO figure out a better way to size each image grid square
		//  Currently have it hardcoded to 200dp
		recyclerView.setLayoutManager(new GridLayoutManager(context, 2));
		ArtworkItemRecyclerViewAdapter adapter = new ArtworkItemRecyclerViewAdapter(ArtworkContent.ITEMS);
		adapter.setOnItemClickListener(new ArtworkItemRecyclerViewAdapter.OnItemClickListener()
		{
			@Override
			public void onItemClick(View itemView, int position)
			{
				ArtworkContent.ArtworkItem item = ArtworkContent.ITEMS.get(position);
				ImageView imageView = itemView.findViewById(R.id.image);
				if (!selectedArtworks.contains(item))
				{
					selectedArtworks.add(item);
					imageView.setColorFilter(Color.argb(130, 0, 150, 250));
				} else
				{
					selectedArtworks.remove(item);
					imageView.clearColorFilter();
				}
			}
		});
		recyclerView.setAdapter(adapter);

		// This FAB actions the delete operation from both the RecyclerView, and the
		//  ContentProvider storing the Artwork details.
		// All changes appear instantaneously
		FloatingActionButton fab = view.findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				// Optimization if no artworks are selected
				if (selectedArtworks.isEmpty())
				{
					Snackbar.make(view, R.string.snackbar_selectArtworkFirst,
							Snackbar.LENGTH_SHORT)
							.show();
					return;
				}

				// Deletes the artwork items from the ArrayList used as backing for the RecyclerView
				ArtworkContent.ITEMS.removeAll(selectedArtworks);
				// TODO somehow notify only single artwork change, instead of notofyDataSetChanged()
				adapter.notifyDataSetChanged();

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
			}
		});

		return view;
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
