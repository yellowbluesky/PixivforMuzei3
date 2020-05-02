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

package com.antony.muzei.pixiv.ui.main;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

import com.antony.muzei.pixiv.R;

public class FilteringPreferenceFragment extends PreferenceFragmentCompat
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.filtering_preference_layout);

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

		// Artwork minimum views slider
		// Updates the summary in real time as the user drags the thumb
		// Increments of 500, hence the scalar
		SeekBarPreference minimumViewSliderPref = findPreference("prefSlider_minViews");
		minimumViewSliderPref.setUpdatesContinuously(true);
		minimumViewSliderPref.setSummary(Integer.toString(
				sharedPrefs.getInt("prefSlider_minViews", 0) * 500));
		minimumViewSliderPref.setOnPreferenceChangeListener((((preference, newValue) ->
		{
			minimumViewSliderPref.setSummary(Integer.toString((Integer) newValue * 500));
			return true;
		})));
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
	{

	}
}
