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
package com.antony.muzei.pixiv.settings.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.work.*
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.ClearCacheWorker
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AdvOptionsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Indicate here the XML resource you created above that holds the preferences
        setPreferencesFromResource(R.xml.adv_setting_preference_layout, rootKey)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        // Artwork minimum views slider
        // Updates the summary in real time as the user drags the thumb
        // Increments of 500, hence the scalar
        val minimumViewSliderPref = findPreference<SeekBarPreference>("prefSlider_minViews")
        minimumViewSliderPref!!.updatesContinuously = true
        minimumViewSliderPref.summary = (sharedPrefs.getInt("prefSlider_minViews", 0) * 500).toString()
        minimumViewSliderPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            minimumViewSliderPref.summary = (newValue as Int * 500).toString()
            true
        }

        // Maximum file size slider
//        SeekBarPreference maximumFileSizeSliderPref = findPreference("prefSlider_maxFileSize");
//        maximumFileSizeSliderPref.setUpdatesContinuously(true);
//        int fileSizeSetting = sharedPrefs.getInt("prefSlider_maxFileSize", 0);
//        if (fileSizeSetting == 0)
//        {
//            maximumFileSizeSliderPref.setSummary(getString(R.string.prefSummary_noFileSizeLimit));
//        }
//        else
//        {
//            maximumFileSizeSliderPref.setSummary(fileSizeSetting + "MB");
//        }
//
//        maximumFileSizeSliderPref.setOnPreferenceChangeListener((((preference, newValue) ->
//        {
//            if ((int) newValue == 0)
//            {
//                maximumFileSizeSliderPref.setSummary(getString(R.string.prefSummary_noFileSizeLimit));
//            }
//            else
//            {
//                maximumFileSizeSliderPref.setSummary(newValue + "MB");
//            }
//            return true;
//        })));

        // Requests the WRITE_EXTERNAL_STORAGE permission
        // is needed if the user has checked the option to store artworks into external storage
        // These artworks are not cleared when the Android cache is cleared
        val externalStoragePref = findPreference<Preference>("pref_storeInExtStorage")
        externalStoragePref!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1)
            }
            true
        }
        externalStoragePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
            (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
        }

        // Slider that lets the user adjust how many artworks to download at a time
        // Draws and updates the slider position number as the user drags
        val numToDownloadSlider = findPreference<SeekBarPreference>("prefSlider_numToDownload")
        numToDownloadSlider!!.updatesContinuously = true
        numToDownloadSlider.summary = sharedPrefs.getInt("prefSlider_numToDownload", 2).toString()
        numToDownloadSlider.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
            numToDownloadSlider.summary = (newValue as Int).toString()
            true
        }
    }

    override fun onStop() {
        super.onStop()
        // Automatic cache clearing at 1AM every night for as long as the setting is toggled active
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPrefs.getBoolean("pref_autoClearMode", false)) {
            // Calculates the hours to midnight
            @SuppressLint("SimpleDateFormat") val simpleDateFormat = SimpleDateFormat("kk")
            val hoursToMidnight = 24 - simpleDateFormat.format(Date()).toInt()

            // Builds and submits the work request
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request = PeriodicWorkRequest.Builder(ClearCacheWorker::class.java, 24, TimeUnit.HOURS)
                    .setInitialDelay(hoursToMidnight.toLong(), TimeUnit.HOURS)
                    .addTag("PIXIV_CACHE_AUTO")
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context!!)
                    .enqueueUniquePeriodicWork("PIXIV_CACHE_AUTO", ExistingPeriodicWorkPolicy.KEEP, request)
        }
        else {
            WorkManager.getInstance(context!!).cancelAllWorkByTag("PIXIV_CACHE_AUTO")
        }
    }
}
