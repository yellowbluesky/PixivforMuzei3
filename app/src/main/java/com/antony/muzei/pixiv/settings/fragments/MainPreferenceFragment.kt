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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.*
import androidx.work.WorkManager
import com.antony.muzei.pixiv.PixivProviderConst.AUTH_MODES
import com.antony.muzei.pixiv.PixivProviderConst.PREFERENCE_PIXIV_ACCESS_TOKEN
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.login.LoginActivityWebview
import com.antony.muzei.pixiv.provider.PixivArtWorker.Companion.enqueueLoad
import com.google.android.material.snackbar.Snackbar
import java.util.*


class MainPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var oldUpdateMode: String
    private lateinit var newUpdateMode: String
    private lateinit var oldTag: String
    private lateinit var newTag: String
    private lateinit var oldArtist: String
    private lateinit var newArtist: String

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference_layout, rootKey)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        // Stores user toggleable variables into a temporary store for later comparison in onStop()
        // If the value of the preference on Activity creation is different to Activity stop, then take certain action
        oldUpdateMode = sharedPrefs.getString("pref_updateMode", "daily") ?: "daily"
        oldTag = sharedPrefs.getString("pref_tagSearch", "") ?: ""
        oldArtist = sharedPrefs.getString("pref_artistId", "") ?: ""

        // Ensures that the user has logged in first before selecting any update mode requiring authentication
        // Reveals UI elements as needed depending on Update Mode selection
        val updateModePref = findPreference<DropDownPreference>("pref_updateMode")
        updateModePref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                // User has selected an authenticated feed mode, but has not yet logged in as evidenced
                // by the lack of an access token
                if (AUTH_MODES.contains(newValue) && sharedPrefs.getString(
                        PREFERENCE_PIXIV_ACCESS_TOKEN,
                        ""
                    )!!.isEmpty()
                ) {
                    Snackbar.make(
                        requireView(), R.string.toast_loginFirst,
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceChangeListener false
                }
                // If any of the auth feed modes, reveal login Preference Category, reveal the auth NSFW filtering,
                // and hide the ranking NSFW filtering
                val authFeedModeSelected = AUTH_MODES.contains(newValue)
                findPreference<Preference>("pref_authFilterSelect")?.isVisible =
                    authFeedModeSelected
                findPreference<Preference>("pref_rankingFilterSelect")?.isVisible =
                    !authFeedModeSelected
                findPreference<Preference>("pref_tagSearch")?.isVisible = newValue == "tag_search"
                findPreference<Preference>("pref_tagLanguage")?.isVisible = newValue == "tag_search"
                findPreference<Preference>("pref_artistId")?.isVisible = newValue == "artist"
                true
            }

        // All this is needed for the arbitrary selection NSFW filtering
        // Will default to only SFW if no filtering modes are selected
        // Prints a summary string based on selection
        // Updates authFilterSelectPref summary as user updates it
        // SimpleSummaryProvider does not support MultiSelectListPreference
        findPreference<MultiSelectListPreference>("pref_authFilterSelect")?.let {
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                    // Reset to SFW on empty selection
                    @Suppress("UNCHECKED_CAST")
                    if ((newValue as Set<String>).isEmpty()) {
                        it.values = setOf("2")
                        sharedPrefs.edit().apply {
                            putStringSet("pref_authFilterSelect", setOf("2"))
                            apply()
                        }
                        it.summary = "SFW"
                        return@setOnPreferenceChangeListener false
                    }
                    it.summary =
                        authSummaryStringGenerator(newValue)

                    true
                }
            it.summary = authSummaryStringGenerator(
                sharedPrefs.getStringSet(
                    "pref_authFilterSelect",
                    setOf("2")
                ) as Set<String>
            )
        }

        // Updates ranking SFW filtering preference
        // Same manner as above, the auth modes
        findPreference<MultiSelectListPreference>("pref_rankingFilterSelect")?.let {
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                    // Reset to SFW on empty selection
                    @Suppress("UNCHECKED_CAST")
                    if ((newValue as Set<String>).isEmpty()) {
                        it.values = setOf("0")
                        sharedPrefs.edit().apply {
                            putStringSet("pref_rankingFilterSelect", setOf("0"))
                            apply()
                        }
                        it.summary = "SFW"
                        return@setOnPreferenceChangeListener false
                    }

                    it.summary =
                        rankingSummaryStringGenerator(newValue)

                    true
                }
            // Generates the ranking NSFW filter summary during activity startup
            it.summary =
                rankingSummaryStringGenerator(
                    sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("0")) as Set<String>
                )
        }

        // Reveal the tag_search or artist_id EditTextPreference and write the summary if update mode matches
        val updateMode = sharedPrefs.getString("pref_updateMode", "daily")
        if (AUTH_MODES.contains(updateMode)) {
            findPreference<Preference>("pref_authFilterSelect")?.isVisible = true
            findPreference<Preference>("prefCat_loginSettings")?.isVisible = true
            if (updateMode == "tag_search") {
                findPreference<Preference>("pref_tagSearch")?.let {
                    it.isVisible = true
                }
                findPreference<Preference>("pref_tagLanguage")?.let {
                    it.isVisible = true
                }
            } else if (updateMode == "artist") {
                findPreference<Preference>("pref_artistId")?.let {
                    it.isVisible = true
                }
            }
        } else {
            findPreference<Preference>("pref_rankingFilterSelect")?.isVisible = true
        }

        // Preference that immediately clears Muzei's image cache when pressed
        findPreference<Preference>(getString(R.string.button_clearCache))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?.deleteRecursively()
                enqueueLoad(true, context)
                Snackbar.make(
                    requireView(), R.string.toast_clearingCache,
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                newUpdateMode = oldUpdateMode
                true
            }

        val loginActivityLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    val loginButtonMain = findPreference<Preference>("pref_login")
                    loginButtonMain!!.summary =
                        getString(R.string.prefSummary_LoggedIn) + " " + data!!.getStringExtra("username")
                    loginButtonMain.title = getString(R.string.prefTitle_logoutButton)
                }
            }

        findPreference<Preference>("pref_login")?.let {
            // On app launch set the Preference to show to appropriate text if logged in
            if (sharedPrefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")?.isEmpty() == true) {
                it.title = getString(R.string.prefTitle_loginButton)
                it.summary = getString(R.string.prefSummary_notLoggedIn)
            } else {
                it.title = getString(R.string.prefTitle_logoutButton)
                it.summary =
                    "${getString(R.string.prefSummary_LoggedIn)} ${
                        sharedPrefs.getString(
                            "name",
                            ""
                        )
                    }"
            }

            // Users click this preference to execute the login or logout
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                loginActivityLauncher.launch(Intent(activity, LoginActivityWebview::class.java))
                true
            }
        }
    }

    // 0 or 1 correspond to SFW or NSFW respectively
    private fun rankingSummaryStringGenerator(selection: Set<String>): String {
        val rankingFilterEntriesPossible =
            resources.getStringArray(R.array.pref_rankingFilterLevel_entries)
        return StringBuilder().let {
            selection.forEachIndexed { _, element ->
                it.append(rankingFilterEntriesPossible[element.toInt()])
                it.append(", ")
            }
            it.setLength(it.length - 2)
            it.toString()
        }
    }

    // Returns a comma delimited string of user selections. There is no trailing comma
    // newValue is a HashSet that can contain 2, 4, 6, or 8, and corresponds to
    // SFW, Slightly Ecchi, Fairly Ecchi, and R18 respectively
    // 2, 4, 6, 8 was selected to match with the values used in Pixiv JSON
    private fun authSummaryStringGenerator(selection: Set<String>): String {
        val authFilterEntriesPossible =
            resources.getStringArray(R.array.pref_authFilterLevel_entries)
        return StringBuilder().let {
            selection.forEachIndexed { _, element ->
                // Translation from {2, 4, 6, 8} to {0, 1, 2, 3}
                it.append(authFilterEntriesPossible[(element.toInt() - 2) / 2])
                it.append(", ")
            }
            it.setLength(it.length - 2)
            it.toString()
        }
    }

    // Functions in here action only on app exit
    override fun onStop() {
        super.onStop()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        newUpdateMode = sharedPrefs.getString("pref_updateMode", "") ?: ""
        newTag = sharedPrefs.getString("pref_tagSearch", "") ?: ""
        newArtist = sharedPrefs.getString("pref_artistId", "") ?: ""

        // If user has changed update, filter mode, or search tag:
        // Immediately stop any pending work, clear the Provider of any Artwork, and then toast
        if (oldUpdateMode != newUpdateMode || oldTag != newTag
            || oldArtist != newArtist
        ) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?.deleteRecursively()
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
}
