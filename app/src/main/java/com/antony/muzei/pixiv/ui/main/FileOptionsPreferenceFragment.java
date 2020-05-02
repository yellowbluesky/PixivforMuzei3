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

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

import com.antony.muzei.pixiv.R;

public class FileOptionsPreferenceFragment extends PreferenceFragmentCompat
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.file_options_preference_layout);

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

		// Requests the WRITE_EXTERNAL_STORAGE permission
		// is needed if the user has checked the option to store artworks into external storage
		// These artworks are not cleared when the Android cache is cleared
		Preference externalStoragePref = findPreference("pref_storeInExtStorage");
		externalStoragePref.setOnPreferenceClickListener(preference ->
		{
			if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED)
			{
				ActivityCompat.requestPermissions(getActivity(),
						new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
						1);
			}
			return true;
		});

		externalStoragePref.setOnPreferenceChangeListener(((preference, newValue) ->
				ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
						== PackageManager.PERMISSION_GRANTED));

		// Slider that lets the user adjust how many artworks to download at a time
		// Draws and updates the slider position number as the user drags
		SeekBarPreference numToDownloadSlider = findPreference("prefSlider_numToDownload");
		numToDownloadSlider.setUpdatesContinuously(true);
		numToDownloadSlider.setSummary(Integer.toString(
				sharedPrefs.getInt("prefSlider_numToDownload", 2)));
		numToDownloadSlider.setOnPreferenceChangeListener((((preference, newValue) ->
		{
			numToDownloadSlider.setSummary(Integer.toString((Integer) newValue));
			return true;
		})));
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
	{

	}
}
