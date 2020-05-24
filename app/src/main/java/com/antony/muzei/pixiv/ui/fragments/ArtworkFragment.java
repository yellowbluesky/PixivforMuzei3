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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.antony.muzei.pixiv.ArtworkContent;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.ui.adapter.ArtworkItemRecyclerViewAdapter;

import org.jetbrains.annotations.NotNull;

public class ArtworkFragment extends Fragment
{

	// TODO: Customize parameter argument names
	private static final String ARG_COLUMN_COUNT = "column-count";
	// TODO: Customize parameters
	private int mColumnCount = 1;
	//private OnListFragmentInteractionListener mListener;

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

//		FloatingActionButton fab = view.findViewById(R.id.fab);
//		fab.setOnClickListener(new View.OnClickListener()
//		{
//			@Override
//			public void onClick(View view)
//			{
//				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//						.setAction("Action", null).show();
//			}
//		});

		// Set the adapter
		if (view instanceof RecyclerView)
		{
			Context context = view.getContext();
			RecyclerView recyclerView = (RecyclerView) view;
//			recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
			recyclerView.setLayoutManager(new GridLayoutManager(context, 2));
			//recyclerView.setLayoutManager(new AutoFitGridLayoutManager(context, 200));
			ArtworkItemRecyclerViewAdapter adapter = new ArtworkItemRecyclerViewAdapter(ArtworkContent.ITEMS);
			adapter.setOnItemClickListener(new ArtworkItemRecyclerViewAdapter.OnItemClickListener()
			{
				@Override
				public void onItemClick(View itemView, int position)
				{
					Log.v("CLICK", Integer.toString(position));
					Log.v("CLICK", ArtworkContent.ITEMS.get(position).title);
					Log.v("CLICK", ArtworkContent.ITEMS.get(position).token);
				}
			});
			recyclerView.setAdapter(adapter);
		}

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
