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
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.antony.muzei.pixiv.ClearCacheWorker;
import com.antony.muzei.pixiv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class AdvOptionsPreferenceFragment extends PreferenceFragmentCompat
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.adv_setting_preference_layout);

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

		// Maximum file size slider
//		SeekBarPreference maximumFileSizeSliderPref = findPreference("prefSlider_maxFileSize");
//		maximumFileSizeSliderPref.setUpdatesContinuously(true);
//		int fileSizeSetting = sharedPrefs.getInt("prefSlider_maxFileSize", 0);
//		if (fileSizeSetting == 0)
//		{
//			maximumFileSizeSliderPref.setSummary(getString(R.string.prefSummary_noFileSizeLimit));
//		}
//		else
//		{
//			maximumFileSizeSliderPref.setSummary(fileSizeSetting + "MB");
//		}
//
//		maximumFileSizeSliderPref.setOnPreferenceChangeListener((((preference, newValue) ->
//		{
//			if ((int) newValue == 0)
//			{
//				maximumFileSizeSliderPref.setSummary(getString(R.string.prefSummary_noFileSizeLimit));
//			}
//			else
//			{
//				maximumFileSizeSliderPref.setSummary(newValue + "MB");
//			}
//			return true;
//		})));

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
	public void onStop()
	{
		super.onStop();
		// Automatic cache clearing at 1AM every night for as long as the setting is toggled active
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		if (sharedPrefs.getBoolean("pref_autoClearMode", false))
		{
			// Calculates the hours to midnight
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("kk");
			int hoursToMidnight = 24 - Integer.parseInt(simpleDateFormat.format(new Date()));

			// Builds and submits the work request
			Constraints constraints = new Constraints.Builder()
					.setRequiredNetworkType(NetworkType.CONNECTED)
					.build();
			PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ClearCacheWorker.class, 24, TimeUnit.HOURS)
					.setInitialDelay(hoursToMidnight, TimeUnit.HOURS)
					.addTag("PIXIV_CACHE_AUTO")
					.setConstraints(constraints)
					.build();
			WorkManager.getInstance(getContext())
					.enqueueUniquePeriodicWork("PIXIV_CACHE_AUTO", ExistingPeriodicWorkPolicy.KEEP, request);
		} else
		{
			WorkManager.getInstance(getContext()).cancelAllWorkByTag("PIXIV_CACHE_AUTO");
		}
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
	{

	}
}
