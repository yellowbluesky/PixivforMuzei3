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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.*
import androidx.work.*
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.ClearCacheWorker
import com.antony.muzei.pixiv.provider.PixivArtWorker
import com.antony.muzei.pixiv.provider.network.OkHttpSingleton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AdvOptionsPreferenceFragment : PreferenceFragmentCompat() {
    interface NightModePreferenceListener {
        fun nightModeOptionSelected(option: Int)
    }

    private lateinit var nightModePreferenceListener: NightModePreferenceListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        nightModePreferenceListener = context as NightModePreferenceListener
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Indicate here the XML resource you created above that holds the preferences
        setPreferencesFromResource(R.xml.adv_setting_preference_layout, rootKey)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Artwork minimum views slider
        // Updates the summary in real time as the user drags the thumb
        // Increments of 500, hence the scalar =
        findPreference<SeekBarPreference>("prefSlider_minViews")?.let { slider ->
            slider.updatesContinuously = true
            slider.summary = (sharedPrefs.getInt("prefSlider_minViews", 0) * 500).toString()
            slider.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    slider.summary = (newValue as Int * 500).toString()
                    true
                }
        }

        findPreference<SeekBarPreference>("prefSlider_minimumWidth")?.let {
            it.updatesContinuously = true
            it.summary = (sharedPrefs.getInt("prefSlider_minimumWidth", 0) * 10).toString() + "px"
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    it.summary = (newValue as Int * 10).toString() + "px"
                    true
                }
        }

        findPreference<SeekBarPreference>("prefSlider_minimumHeight")?.let {
            it.updatesContinuously = true
            it.summary =
                (sharedPrefs.getInt("prefSlider_minimumHeight", 0) * 10).toString() + "px"
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    it.summary = (newValue as Int * 10).toString() + "px"
                    true
                }
        }

        // Switch that controls if the cache periodically auto clears itself
        findPreference<SwitchPreference>("pref_autoClearMode")?.let {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                if (newValue as Boolean) {
                    // Calculates the hours to midnight
                    @SuppressLint("SimpleDateFormat") val simpleDateFormat = SimpleDateFormat("kk")
                    val hoursToMidnight = 24 - simpleDateFormat.format(Date()).toInt()

                    // Builds and submits the work request
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val request = PeriodicWorkRequest.Builder(
                        ClearCacheWorker::class.java,
                        24,
                        TimeUnit.HOURS
                    )
                        .setInitialDelay(hoursToMidnight.toLong(), TimeUnit.HOURS)
                        .addTag("PIXIV_CACHE_AUTO")
                        .setConstraints(constraints)
                        .build()
                    WorkManager.getInstance(requireContext())
                        .enqueueUniquePeriodicWork(
                            "PIXIV_CACHE_AUTO",
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                            request
                        )
                } else {
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag("PIXIV_CACHE_AUTO")
                }
                true
            }
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

        // If more than one storage is detected to be mounted, display the preference that allows the user to
        //  select where to download pictures to
        // Also only visible if the option to download artwork to user storage is enabled
        val selectWhichExternalStoragePref =
            findPreference<DropDownPreference>("pref_selectWhichExtStorage")
        selectWhichExternalStoragePref?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    isMoreThanOneStorage() &&
                    sharedPrefs.getBoolean("pref_storeInExtStorage", false)


        // Requests the WRITE_EXTERNAL_STORAGE permission
        // is needed if the user has checked the option to store artworks into external storage
        // These artworks are not cleared when the Android cache is cleared
        val externalStoragePref = findPreference<Preference>("pref_storeInExtStorage")
        externalStoragePref!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
            true
        }
        externalStoragePref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                // Dynamically hides and reveals select storage preference as the store into external storage preference is enabled and disabled
                if (isMoreThanOneStorage()) {
                    selectWhichExternalStoragePref?.isVisible = newValue as Boolean
                }
                true
            }
//        externalStoragePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
//            (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
//
//        }

        // Slider that lets the user adjust how many artworks to download at a time
        // Draws and updates the slider position number as the user drags
        findPreference<SeekBarPreference>("prefSlider_numToDownload")?.let { slider ->
            slider.updatesContinuously = true
            slider.summary = sharedPrefs.getInt("prefSlider_numToDownload", 2).toString()
            slider.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    slider.summary = (newValue as Int).toString()
                    true
                }
        }

        findPreference<SwitchPreference>("pref_enableNetworkBypass")?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                OkHttpSingleton.refreshInstance() // Renew a instance with sslSocketFactory by this
                PixivArtWorker.enqueueLoad(false, context)
                true
            }
        }

        // Hides the post processing switch when API level is too low
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            preferenceScreen = findPreference(resources.getString(R.string.preferenceScreen))
            findPreference<PreferenceCategory>("prefCat_postProcess")?.let {
                preferenceScreen.removePreference(it)
            }
        }

        // This preference immediately sets the night mode option
        // Without it, the app needs to restart to apply the new setting
        // Needs to be implemented as an interface as the setting can only be applied within an Adtivity
        findPreference<ListPreference>("pref_nightMode")?.let {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                nightModePreferenceListener.nightModeOptionSelected((newValue as String).toInt())
                true
            }
        }
    }

    private fun isMoreThanOneStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(requireContext()).size > 1
        } else {
            // Android P or lower
            requireContext().getExternalFilesDirs(null).size > 1
        }
    }
}
