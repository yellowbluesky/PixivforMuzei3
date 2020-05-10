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

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.antony.muzei.pixiv.R;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter
{

	@StringRes
	private static final int[] TAB_TITLES = new int[]{R.string.tab_heading_main, R.string.tab_heading_adv_options, R.string.tab_heading_roadmap, R.string.tab_heading_credits};
	private final Context mContext;

	public SectionsPagerAdapter(Context context, FragmentManager fm)
	{
		super(fm);
		mContext = context;
	}

	@Override
	public Fragment getItem(int position)
	{
		// getItem is called to instantiate the fragment for the given page.
		// Return a PlaceholderFragment (defined as a static inner class below).
		switch (position)
		{
			case 0:
				return new MainPreferenceFragment();
			case 1:
				return new AdvOptionsPreferenceFragment();
			case 2:
				return new RoadmapPreferenceFragment();
			case 3:
				return new CreditsPreferenceFragment();
			default:
				return new CreditsPreferenceFragment();
		}
	}

	@Nullable
	@Override
	public CharSequence getPageTitle(int position)
	{
		return mContext.getResources().getString(TAB_TITLES[position]);
	}

	@Override
	public int getCount()
	{
		// How many pages to show
		return 4;
	}
}