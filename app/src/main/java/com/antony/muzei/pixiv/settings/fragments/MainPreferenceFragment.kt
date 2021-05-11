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
import android.widget.Toast
import androidx.preference.*
import androidx.work.WorkManager
import com.antony.muzei.pixiv.PixivProviderConst.PREFERENCE_PIXIV_ACCESS_TOKEN
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.login.LoginActivityWebview
import com.antony.muzei.pixiv.provider.PixivArtProviderDefines
import com.antony.muzei.pixiv.provider.PixivArtWorker.Companion.enqueueLoad
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.*


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
        updateModePref!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                val isAuthUpdateMode = PixivArtProviderDefines.AUTH_MODES.contains(newValue.toString())
                // User has selected an authenticated feed mode, but has not yet logged in as evidenced
                // by the lack of an access token
                if (isAuthUpdateMode && sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
                    Snackbar.make(
                        requireView(), R.string.toast_loginFirst,
                        Snackbar.LENGTH_SHORT
                    ).show()
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
        authFilterSelectPref!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                // Reset to SFW on empty selection
                if ((newValue as HashSet<*>).isEmpty()) {
                    authFilterSelectPref.values = setOf("2")
                    sharedPrefs.edit().apply {
                        putStringSet("pref_authFilterSelect", setOf("2"))
                        apply()
                    }
                    authFilterSelectPref.summary = "SFW"
                    return@setOnPreferenceChangeListener false
                }
                authFilterSelectPref.summary = authSummaryStringGenerator(newValue as HashSet<String>)

                true
            }
        authFilterSelectPref.summary =
            authSummaryStringGenerator(sharedPrefs.getStringSet("pref_authFilterSelect", null) as HashSet<String>)

        // Updates ranking SFW filtering preference
        // Same manner as above, the auth modes
        val rankingFilterSelectPref = findPreference<MultiSelectListPreference>("pref_rankingFilterSelect")
        rankingFilterSelectPref!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                // For some reason a length of 2 is an empty selection
                if ((newValue as HashSet<*>).isEmpty()) {
                    rankingFilterSelectPref.values = setOf("0")
                    sharedPrefs.edit().apply {
                        putStringSet("pref_rankingFilterSelect", setOf("0"))
                        apply()
                    }
                    rankingFilterSelectPref.summary = "SFW"
                    return@setOnPreferenceChangeListener false
                }

                rankingFilterSelectPref.summary = rankingSummaryStringGenerator(newValue as HashSet<String>)

                true
            }
        // Generates the ranking NSFW filter summary during activity startup
        rankingFilterSelectPref.summary =
            rankingSummaryStringGenerator(sharedPrefs.getStringSet("pref_rankingFilterSelect", null) as HashSet<String>)


        // Reveal the tag_search or artist_id EditTextPreference and write the summary if update mode matches
        val updateMode = sharedPrefs.getString("pref_updateMode", "daily")
        if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
            findPreference<Preference>("pref_authFilterSelect")!!.isVisible = true
            findPreference<Preference>("prefCat_loginSettings")!!.isVisible = true
            if (updateMode == "tag_search") {
                val tagSearch = findPreference<Preference>("pref_tagSearch")
                tagSearch!!.isVisible = true
                tagSearch.summary = sharedPrefs.getString("pref_tagSearch", "")
            } else if (updateMode == "artist") {
                val artistId = findPreference<Preference>("pref_artistId")
                artistId!!.isVisible = true
                artistId.summary = sharedPrefs.getString("pref_artistId", "")
            }
        } else {
            findPreference<Preference>("pref_rankingFilterSelect")!!.isVisible = true
        }

        // Preference that immediately clears Muzei's image cache when pressed
        findPreference<Preference>(getString(R.string.button_clearCache))!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
                val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val children = dir!!.list()
                for (child in children) {
                    File(dir, child).delete()
                }
                enqueueLoad(true, context)
                Snackbar.make(
                    requireView(), R.string.toast_clearingCache,
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                newUpdateMode = oldUpdateMode
                true
            }

        // On app launch set the Preference to show to appropriate text if logged in
        val loginActivityPreference = findPreference<Preference>("pref_login")
        if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
            loginActivityPreference!!.title = getString(R.string.prefTitle_loginButton)
            loginActivityPreference.summary = getString(R.string.prefSummary_notLoggedIn)
        } else {
            loginActivityPreference!!.title = getString(R.string.prefTitle_logoutButton)
            loginActivityPreference.summary =
                getString(R.string.prefSummary_LoggedIn) + " " + sharedPrefs.getString("name", "")
        }

        // Users click this preference to execute the login or logout
        loginActivityPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityForResult(
                Intent(activity, LoginActivityWebview::class.java),
                REQUEST_CODE_LOGIN
            )
//            if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")!!.isEmpty()) {
//                startActivityForResult(Intent(activity, LoginActivityWebview::class.java), REQUEST_CODE_LOGIN)
//            } else {
//                // Alert that confirms the user really wants to log out
//                // Important as it is now difficult to login, due to Pixiv API changes 02/21
//                AlertDialog.Builder(requireContext())
//                        .setMessage(getString(R.string.dialog_logoutConfirm))
//                        .setPositiveButton(R.string.dialog_yes) { _, _ ->
//                            val editor = sharedPrefs.edit()
//                            editor.remove("accessTokenIssueTime")
//                            editor.remove("name")
//                            editor.remove(PREFERENCE_PIXIV_ACCESS_TOKEN)
//                            editor.remove("userId")
//                            editor.remove("refreshToken")
//                            loginActivityPreference.title = getString(R.string.prefTitle_loginButton)
//                            loginActivityPreference.summary = getString(R.string.prefSummary_notLoggedIn)
//                            // If the user has an authenticated feed mode, reset it to daily ranking on logout
//                            if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
//                                editor.putString("pref_updateMode", "daily")
//                                //updateModePref.summary = resources.getStringArray(R.array.pref_updateMode_entries)[0]
//                            }
//                            editor.apply()
//                        }
//                        .setNegativeButton(R.string.dialog_no) { dialog, _ ->
//                            // Do nothing
//                            dialog.dismiss()
//                        }
//                        .show()
//            }
            true
        }
    }

    // 0 or 1 correspond to SFW or NSFW respectively
    private fun rankingSummaryStringGenerator(selection: HashSet<String>): String {
        val rankingFilterEntriesPossible = resources.getStringArray(R.array.pref_rankingFilterLevel_entries)
        return StringBuilder().let {
            selection.forEachIndexed { index, element ->
                it.append(rankingFilterEntriesPossible[element.toInt()])
                if (index != selection.size - 1) {
                    it.append(", ")
                }
            }
            it.toString()
        }
    }

    // Returns a comma delimited string of user selections. There is no trailing comma
    // newValue is a HashSet that can contain 2, 4, 6, or 8, and corresponds to
    // SFW, Slightly Ecchi, Fairly Ecchi, and R18 respectively
    // 2, 4, 6, 8 was selected to match with the values used in Pixiv JSON
    private fun authSummaryStringGenerator(selection: HashSet<String>): String {
        val authFilterEntriesPossible = resources.getStringArray(R.array.pref_authFilterLevel_entries)
        return StringBuilder().let {
            selection.forEachIndexed { index, element ->
                it.append(authFilterEntriesPossible[(element.toInt() - 2) / 2])
                if (index != selection.size - 1) {
                    it.append(", ")
                }
            }
            it.toString()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Currently only used by LoginActivity
        if (requestCode == REQUEST_CODE_LOGIN && resultCode == Activity.RESULT_OK) {
            val loginButtonMain = findPreference<Preference>("pref_login")
            loginButtonMain!!.summary =
                getString(R.string.prefSummary_LoggedIn) + " " + data!!.getStringExtra("username")
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
            || oldArtist != newArtist
        ) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val children = dir!!.list()
            for (child in children) {
                File(dir, child).delete()
            }
            enqueueLoad(true, context)
            if (oldUpdateMode != newUpdateMode) {
                Toast.makeText(context, getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT)
                    .show()
            } else if (oldArtist != newArtist) {
                Toast.makeText(context, getString(R.string.toast_newArtist), Toast.LENGTH_SHORT)
                    .show()
            } else if (oldTag != newTag) {
                Toast.makeText(context, getString(R.string.toast_newTag), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.toast_newFilterSelect),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_LOGIN = 1
    }
}
