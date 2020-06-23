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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.preference.*
import androidx.work.WorkManager
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.login.LoginActivity
import com.antony.muzei.pixiv.provider.PixivArtProviderDefines
import com.antony.muzei.pixiv.provider.PixivArtWorker.Companion.enqueueLoad
import com.antony.muzei.pixiv.PixivProviderConst
import com.antony.muzei.pixiv.util.IntentUtils
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.*

import com.antony.muzei.pixiv.PixivProviderConst.PREFERENCE_PIXIV_ACCESS_TOKEN


class MainPreferenceFragment : PreferenceFragmentCompat() {
    private var oldUpdateMode: String? = null
    private var newUpdateMode: String? = null
    private var oldTag: String? = null
    private var newTag: String? = null
    private var oldArtist: String? = null
    private var newArtist: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference_layout, rootKey)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        // Stores user toggleable variables into a temporary store for later comparison in onStop()
        // If the value of the preference on Activity creation is different to Activity stop, then take certain action
        oldUpdateMode = sharedPrefs.getString("pref_updateMode", "daily")
        newUpdateMode = oldUpdateMode
        oldTag = sharedPrefs.getString("pref_tagSearch", "")
        newTag = oldTag
        oldArtist = sharedPrefs.getString("pref_artistId", "")
        newArtist = oldArtist

        // Ensures that the user has logged in first before selecting any update mode requiring authentication
        // Reveals UI elements as needed depending on Update Mode selection
        val updateModePref = findPreference<DropDownPreference>("pref_updateMode")
        updateModePref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
            val isAuthUpdateMode = PixivArtProviderDefines.AUTH_MODES.contains(newValue.toString())
            // User has selected an authenticated feed mode, but has not yet logged in as evidenced
            // by the lack of an access token
            if (isAuthUpdateMode && sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
                Snackbar.make(requireView(), R.string.toast_loginFirst,
                        Snackbar.LENGTH_SHORT)
                        .show()
                return@setOnPreferenceChangeListener false
            }
            // If any of the auth feed modes, reveal login Preference Category, reveal the auth NSFW filtering,
            // and hide the ranking NSFW filtering
            val authFeedModeSelected = PixivArtProviderDefines.AUTH_MODES.contains(newValue)
            findPreference<Preference>("pref_authFilterSelect")!!.isVisible = authFeedModeSelected
            findPreference<Preference>("pref_rankingFilterSelect")!!.isVisible = !authFeedModeSelected
            findPreference<Preference>("pref_tagSearch")!!.isVisible = newValue == "tag_search"
            findPreference<Preference>("pref_artistId")!!.isVisible = newValue == "artist"
            true
        }

        // All this is needed for the arbitrary selection NSFW filtering
        // Will default to only SFW if no filtering modes are selected
        // Prints a summary string based on selection
        // Updates authFilterSelectPref summary as user updates it
        val authFilterSelectPref = findPreference<MultiSelectListPreference>("pref_authFilterSelect")
        authFilterSelectPref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
            // Resets to SFW on empty selection
            // for some reason a length of 2 is an empty selection
            if (newValue.toString().length == 2) {
                val defaultSet: MutableSet<String> = HashSet()
                defaultSet.add("2")
                authFilterSelectPref.values = defaultSet
                val editor = sharedPrefs.edit()
                editor.putStringSet("pref_authFilterSelect", defaultSet)
                editor.apply()
                authFilterSelectPref.summary = "SFW"
                return@setOnPreferenceChangeListener false
            }

            // Prints a comma delimited string of user selections. There is no trailing comma
            // TODO there's gotta be a better wau of doing this
            val str = newValue.toString()
            val arrayList = ArrayList<Int>()
            for (i in str.indices) {
                if (Character.isDigit(str[i])) {
                    arrayList.add(Character.getNumericValue(str[i]))
                }
            }
            val authFilterEntriesPossible = resources.getStringArray(R.array.pref_authFilterLevel_entries)
            val stringBuilderAuth = StringBuilder()
            for (i in arrayList.indices) {
                stringBuilderAuth.append(authFilterEntriesPossible[(arrayList[i] - 2) / 2])
                if (i != arrayList.size - 1) {
                    stringBuilderAuth.append(", ")
                }
            }
            val summaryAuth = stringBuilderAuth.toString()
            authFilterSelectPref.summary = summaryAuth
            true
        }

        // Updates ranking SFW filtering preference
        // Same manner as above, the auth modes
        val rankingFilterSelectPref = findPreference<MultiSelectListPreference>("pref_rankingFilterSelect")
        rankingFilterSelectPref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
            // For some reason a length of 2 is an empty selection
            if (newValue.toString().length == 2) {
                Log.v("MANUAL", "pref change empty set")
                val defaultSet: MutableSet<String> = HashSet()
                defaultSet.add("0")
                rankingFilterSelectPref.values = defaultSet
                val editor = sharedPrefs.edit()
                editor.putStringSet("pref_rankingFilterSelect", defaultSet)
                editor.apply()
                rankingFilterSelectPref.summary = "SFW"
                return@setOnPreferenceChangeListener false
            }
            val str = newValue.toString()
            val arrayList = ArrayList<Int>()
            for (i in str.indices) {
                if (Character.isDigit(str[i])) {
                    arrayList.add(Character.getNumericValue(str[i]))
                }
            }
            val rankingEntriesAvailable = resources.getStringArray(R.array.pref_rankingFilterLevel_entries)
            val stringBuilderRanking = StringBuilder()
            for (i in arrayList.indices) {
                stringBuilderRanking.append(rankingEntriesAvailable[arrayList[i]])
                if (i != arrayList.size - 1) {
                    stringBuilderRanking.append(", ")
                }
            }
            val summaryRanking = stringBuilderRanking.toString()
            rankingFilterSelectPref.summary = summaryRanking
            true
        }

        // Generates the ranking NSFW filter summary during activity startup
        val chosenLevelsSetRanking = sharedPrefs.getStringSet("pref_rankingFilterSelect", null)
        val chosenLevelsRanking = chosenLevelsSetRanking!!.toTypedArray()
        val entriesAvailableRanking = resources.getStringArray(R.array.pref_rankingFilterLevel_entries)
        val stringBuilderRanking = StringBuilder()
        for (i in chosenLevelsRanking.indices) {
            stringBuilderRanking.append(entriesAvailableRanking[chosenLevelsRanking[i].toInt()])
            if (i != chosenLevelsRanking.size - 1) {
                stringBuilderRanking.append(", ")
            }
        }
        val summaryRanking = stringBuilderRanking.toString()
        rankingFilterSelectPref.summary = summaryRanking

        // Generates the authFilterSelectPref summary during activity startup
        // TODO combine this with the above summary setting code section
        val chosenLevelsSet = sharedPrefs.getStringSet("pref_authFilterSelect", null)
        val chosenLevels = chosenLevelsSet!!.toTypedArray()
        val entriesAvailableAuth = resources.getStringArray(R.array.pref_authFilterLevel_entries)
        val stringBuilderAuth = StringBuilder()
        for (i in chosenLevels.indices) {
            stringBuilderAuth.append(entriesAvailableAuth[(chosenLevels[i].toInt() - 2) / 2])
            if (i != chosenLevels.size - 1) {
                stringBuilderAuth.append(", ")
            }
        }
        val summaryAuth = stringBuilderAuth.toString()
        authFilterSelectPref.summary = summaryAuth

        // Reveal the tag_search or artist_id EditTextPreference and write the summary if update mode matches
        val updateMode = sharedPrefs.getString("pref_updateMode", "daily")
        if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
            findPreference<Preference>("pref_authFilterSelect")!!.isVisible = true
            findPreference<Preference>("prefCat_loginSettings")!!.isVisible = true
            if (updateMode == "tag_search") {
                val tagSearch = findPreference<Preference>("pref_tagSearch")
                tagSearch!!.isVisible = true
                tagSearch.summary = sharedPrefs.getString("pref_tagSearch", "")
            }
            else if (updateMode == "artist") {
                val artistId = findPreference<Preference>("pref_artistId")
                artistId!!.isVisible = true
                artistId.summary = sharedPrefs.getString("pref_artistId", "")
            }
        }
        else {
            findPreference<Preference>("pref_rankingFilterSelect")!!.isVisible = true
        }

        // Preference that immediately clears Muzei's image cache when pressed
        findPreference<Preference>(getString(R.string.button_clearCache))!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val children = dir!!.list()
            for (child in children) {
                File(dir, child).delete()
            }
            enqueueLoad(true, context)
            Snackbar.make(requireView(), R.string.toast_clearingCache,
                    Snackbar.LENGTH_SHORT)
                    .show()
            newUpdateMode = oldUpdateMode
            true
        }

        // On app launch set the Preference to show to appropriate text if logged in
        val loginActivityPreference = findPreference<Preference>("pref_login")
        if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
            loginActivityPreference!!.title = getString(R.string.prefTitle_loginButton)
            loginActivityPreference.summary = getString(R.string.prefSummary_notLoggedIn)
        }
        else {
            loginActivityPreference!!.title = getString(R.string.prefTitle_logoutButton)
            loginActivityPreference.summary = getString(R.string.prefSummary_LoggedIn) + " " + sharedPrefs.getString("name", "")
        }
        // Users click this preference to execute the login or logout
        loginActivityPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
                val intent = Intent(context, LoginActivity::class.java)
                IntentUtils.launchActivity(this, intent, REQUEST_CODE_LOGIN)
            }
            else {
                // Logging out
                val editor = sharedPrefs.edit()
                editor.remove("accessTokenIssueTime")
                editor.remove("name")
                editor.remove(PREFERENCE_PIXIV_ACCESS_TOKEN)
                editor.remove("userId")
                editor.remove("refreshToken")
                loginActivityPreference.title = getString(R.string.prefTitle_loginButton)
                loginActivityPreference.summary = getString(R.string.prefSummary_notLoggedIn)
                // If the user has an authenticated feed mode, reset it to daily ranking on logout
                if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
                    editor.putString("pref_updateMode", "daily")
                    updateModePref.summary = resources.getStringArray(R.array.pref_updateMode_entries)[0]
                }
                editor.apply()
            }
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Currently only used by LoginActivity
        if (requestCode == REQUEST_CODE_LOGIN && resultCode == Activity.RESULT_OK) {
            val loginButtonMain = findPreference<Preference>("pref_login")
            loginButtonMain!!.summary = getString(R.string.prefSummary_LoggedIn) + " " + data!!.getStringExtra("username")
            loginButtonMain.title = getString(R.string.prefTitle_logoutButton)
        }
    }

    // Functions in here action only on app exit
    override fun onStop() {
        super.onStop()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        newUpdateMode = sharedPrefs.getString("pref_updateMode", "")
        newTag = sharedPrefs.getString("pref_tagSearch", "")
        newArtist = sharedPrefs.getString("pref_artistId", "")

        // If user has changed update, filter mode, or search tag:
        // Immediately stop any pending work, clear the Provider of any Artwork, and then toast
        if (oldUpdateMode != newUpdateMode || oldTag != newTag
                || oldArtist != newArtist) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val children = dir!!.list()
            for (child in children) {
                File(dir, child).delete()
            }
            enqueueLoad(true, context)
            if (oldUpdateMode != newUpdateMode) {
                Toast.makeText(context, getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT).show()
            }
            else if (oldArtist != newArtist) {
                Toast.makeText(context, getString(R.string.toast_newArtist), Toast.LENGTH_SHORT).show()
            }
            else if (oldTag != newTag) {
                Toast.makeText(context, getString(R.string.toast_newTag), Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(context, getString(R.string.toast_newFilterSelect), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_LOGIN = 1
    }
}
