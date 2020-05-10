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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;

import com.antony.muzei.pixiv.PixivArtProviderDefines;
import com.antony.muzei.pixiv.PixivArtWorker;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.ui.activity.LoginActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainPreferenceFragment extends PreferenceFragmentCompat
{
	private static final int REQUEST_CODE_LOGIN = 1;
	private String oldUpdateMode, newUpdateMode;
	private String oldTag, newTag;
	private String oldArtist, newArtist;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.main_preference_layout);

		// If Muzei is not installed, this will redirect the user to Muzei's Play Store listing
		if (!isMuzeiInstalled())
		{
			final String appPackageName = "net.nurik.roman.muzei"; // getPackageName() from Context or Activity object
			try
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
			} catch (android.content.ActivityNotFoundException ex)
			{
				startActivity(new Intent(Intent.ACTION_VIEW,
						Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
			}
		}

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

		// Stores user toggleable variables into a temporary store for later comparison in onStop()
		// If the value of the preference on Activity creation is different to Activity stop, then take certain action
		oldUpdateMode = sharedPrefs.getString("pref_updateMode", "");
		newUpdateMode = oldUpdateMode;

		oldTag = sharedPrefs.getString("pref_tagSearch", "");
		newTag = oldTag;

		oldArtist = sharedPrefs.getString("pref_artistId", "");
		newArtist = oldArtist;

		// Ensures that the user has logged in first before selecting any update mode requiring authentication
		// Reveals UI elements as needed depending on Update Mode selection
		findPreference("pref_updateMode").setOnPreferenceChangeListener((preference, newValue) ->
		{
			boolean isAuthUpdateMode = Arrays.asList(PixivArtProviderDefines.AUTH_MODES)
					.contains(newValue.toString());
			// User has selected an authenticated feed mode, but has not yet logged in as evidenced
			// by the lack of an access token
			if (isAuthUpdateMode && sharedPrefs.getString("accessToken", "").isEmpty())
			{
				Toast.makeText(getContext(), getString(R.string.toast_loginFirst), Toast.LENGTH_SHORT).show();
				return false;
			}
			// If any of the auth feed modes, reveal login Preference Category, reveal the auth NSFW filtering,
			// and hide the ranking NSFW filtering
			boolean authFeedModeSelected = Arrays.asList(PixivArtProviderDefines.AUTH_MODES)
					.contains(newValue);

			findPreference("pref_authFilterSelect").setVisible(authFeedModeSelected);
			findPreference("pref_rankingFilterSelect").setVisible(!authFeedModeSelected);

			findPreference("pref_tagSearch").setVisible(newValue.equals("tag_search"));
			findPreference("pref_artistId").setVisible(newValue.equals("artist"));
			return true;
		});

		// All this is needed for the arbitrary selection NSFW filtering
		// Resets to default SFW filtering is all options are unchecked
		// Prints a summary string based on selection
		// Updates authFilterSelectPref summary as user updates it
		MultiSelectListPreference authFilterSelectPref = findPreference("pref_authFilterSelect");
		authFilterSelectPref.setOnPreferenceChangeListener((preference, newValue) ->
		{
			// Resets to SFW on empty selection
			// for some reason a length of 2 is an empty selection
			if (newValue.toString().length() == 2)
			{
				Set<String> defaultSet = new HashSet<>();
				defaultSet.add("2");
				authFilterSelectPref.setValues(defaultSet);

				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.putStringSet("pref_authFilterSelect", defaultSet);
				editor.commit();
				authFilterSelectPref.setSummary("SFW");
				return false;
			}

			// Prints a comma delimited string of user selections. There is no trailing comma
			// TODO there's gotta be a better wau of doing this
			String str = newValue.toString();
			ArrayList<Integer> arrayList = new ArrayList<>();
			for (int i = 0; i < str.length(); i++)
			{
				if (Character.isDigit(str.charAt(i)))
				{
					arrayList.add(Character.getNumericValue(str.charAt(i)));
				}
			}
			String[] authFilterEntriesPossible = getResources().getStringArray(R.array.pref_authFilterLevel_entries);
			StringBuilder stringBuilderAuth = new StringBuilder();
			for (int i = 0; i < arrayList.size(); i++)
			{
				stringBuilderAuth.append(authFilterEntriesPossible[(arrayList.get(i) - 2) / 2]);
				if (i != arrayList.size() - 1)
				{
					stringBuilderAuth.append(", ");
				}
			}
			String summaryAuth = stringBuilderAuth.toString();

			authFilterSelectPref.setSummary(summaryAuth);

			return true;
		});

		// updates ranking nsfw select summary on preference change
		MultiSelectListPreference rankingFilterSelectPref = findPreference("pref_rankingFilterSelect");
		rankingFilterSelectPref.setOnPreferenceChangeListener((preference, newValue) ->
		{
			// for some reason 2 is an empty selection
			if (newValue.toString().length() == 2)
			{
				Log.v("MANUAL", "pref change empty set");
				Set<String> defaultSet = new HashSet<>();
				defaultSet.add("0");
				rankingFilterSelectPref.setValues(defaultSet);

				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.putStringSet("pref_rankingFilterSelect", defaultSet);
				editor.commit();
				rankingFilterSelectPref.setSummary("SFW");
				return false;
			}

			String str = newValue.toString();
			ArrayList<Integer> arrayList = new ArrayList<>();
			for (int i = 0; i < str.length(); i++)
			{
				if (Character.isDigit(str.charAt(i)))
				{
					arrayList.add(Character.getNumericValue(str.charAt(i)));
				}
			}
			String[] rankingEntriesAvailable = getResources().getStringArray(R.array.pref_rankingFilterLevel_entries);
			StringBuilder stringBuilderRanking = new StringBuilder();
			for (int i = 0; i < arrayList.size(); i++)
			{
				stringBuilderRanking.append(rankingEntriesAvailable[arrayList.get(i)]);
				if (i != arrayList.size() - 1)
				{
					stringBuilderRanking.append(", ");
				}
			}
			String summaryRanking = stringBuilderRanking.toString();

			rankingFilterSelectPref.setSummary(summaryRanking);

			return true;
		});

		// Generates the ranking NSFW filter summary during activity startup
		Set<String> chosenLevelsSetRanking = sharedPrefs.getStringSet("pref_rankingFilterSelect", null);
		String[] chosenLevelsRanking = chosenLevelsSetRanking.toArray(new String[0]);
		String[] entriesAvailableRanking = getResources().getStringArray(R.array.pref_rankingFilterLevel_entries);
		StringBuilder stringBuilderRanking = new StringBuilder();
		for (int i = 0; i < chosenLevelsRanking.length; i++)
		{
			stringBuilderRanking.append(entriesAvailableRanking[Integer.parseInt(chosenLevelsRanking[i])]);
			if (i != chosenLevelsRanking.length - 1)
			{
				stringBuilderRanking.append(", ");
			}
		}
		String summaryRanking = stringBuilderRanking.toString();
		rankingFilterSelectPref.setSummary(summaryRanking);

		// Generates the authFilterSelectPref summary during activity startup
		// TODO combine this with the above summary setting code section
		Set<String> chosenLevelsSet = sharedPrefs.getStringSet("pref_authFilterSelect", null);
		String[] chosenLevels = chosenLevelsSet.toArray(new String[0]);
		String[] entriesAvailableAuth = getResources().getStringArray(R.array.pref_authFilterLevel_entries);
		StringBuilder stringBuilderAuth = new StringBuilder();
		for (int i = 0; i < chosenLevels.length; i++)
		{
			stringBuilderAuth.append(entriesAvailableAuth[(Integer.parseInt(chosenLevels[i]) - 2) / 2]);
			if (i != chosenLevels.length - 1)
			{
				stringBuilderAuth.append(", ");
			}
		}
		String summaryAuth = stringBuilderAuth.toString();
		authFilterSelectPref.setSummary(summaryAuth);

		// Reveal the tag_search or artist_id EditTextPreference and write the summary if update mode matches
		String updateMode = sharedPrefs.getString("pref_updateMode", "daily");
		if (Arrays.asList(PixivArtProviderDefines.AUTH_MODES)
				.contains(updateMode))
		{
			findPreference("pref_authFilterSelect").setVisible(true);
			findPreference("prefCat_loginSettings").setVisible(true);
			if (updateMode.equals("tag_search"))
			{
				Preference tagSearch = findPreference("pref_tagSearch");
				tagSearch.setVisible(true);
				tagSearch.setSummary(sharedPrefs.getString("pref_tagSearch", ""));
			} else if (updateMode.equals("artist"))
			{
				Preference artistId = findPreference("pref_artistId");
				artistId.setVisible(true);
				artistId.setSummary(sharedPrefs.getString("pref_artistId", ""));
			}
		} else
		{
			findPreference("pref_rankingFilterSelect").setVisible(true);
		}

		// Preference that immediately clears Muzei's image cache when pressed
		findPreference(getString(R.string.button_clearCache)).setOnPreferenceClickListener(preference ->
		{
			WorkManager.getInstance().cancelUniqueWork("ANTONY");
			File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

			String[] children = dir.list();
			for (String child : children)
			{
				new File(dir, child).delete();
			}
			PixivArtWorker.enqueueLoad(true);
			Toast.makeText(getContext(), getString(R.string.toast_clearingCache), Toast.LENGTH_SHORT).show();
			return true;
		});

		// On app launch set the Preference to show to appropriate text if logged in
		Preference loginActivityPreference = findPreference("pref_login");
		if (sharedPrefs.getString("accessToken", "").isEmpty())
		{
			loginActivityPreference.setTitle(R.string.prefTitle_loginButton);
			loginActivityPreference.setSummary(R.string.prefSummary_notLoggedIn);
		} else
		{
			loginActivityPreference.setTitle(R.string.prefTitle_logoutButton);
			loginActivityPreference.setSummary(getString(R.string.prefSummary_LoggedIn) + " " + sharedPrefs.getString("name", ""));
		}
		// Users click this preference to execute the login or logout
		loginActivityPreference.setOnPreferenceClickListener(preference ->
		{
			if (sharedPrefs.getString("accessToken", "").isEmpty())
			{
				Intent intent = new Intent(getContext(), LoginActivity.class);
				startActivityForResult(intent, REQUEST_CODE_LOGIN);
			} else
			{
				// Logging out
				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.remove("accessTokenIssueTime");
				editor.remove("name");
				editor.remove("accessToken");
				editor.remove("userId");
				editor.remove("refreshToken");

				loginActivityPreference.setTitle(R.string.prefTitle_loginButton);
				loginActivityPreference.setSummary(R.string.prefSummary_notLoggedIn);
				// If the user has an authenticated feed mode, reset it to daily ranking on logout
				if (Arrays.asList(PixivArtProviderDefines.AUTH_MODES)
						.contains(updateMode))
				{
					editor.putString("pref_updateMode", "daily");
				}
				editor.commit();
			}
			return true;
		});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		// Currently only used by LoginActivity
		if (requestCode == REQUEST_CODE_LOGIN && resultCode == Activity.RESULT_OK)
		{
			Preference loginButtonMain = findPreference("pref_login");

			loginButtonMain.setSummary(R.string.prefSummary_LoggedIn + data.getStringExtra("username"));
			loginButtonMain.setTitle(R.string.prefTitle_logoutButton);
		}
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
	{
	}

	// Functions in here action only on app exit
	@Override
	public void onStop()
	{
		super.onStop();

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		newUpdateMode = sharedPrefs.getString("pref_updateMode", "");
		newTag = sharedPrefs.getString("pref_tagSearch", "");
		newArtist = sharedPrefs.getString("pref_artistId", "");

		// If user has changed update, filter mode, or search tag:
		// Immediately stop any pending work, clear the Provider of any Artwork, and then toast
		if (!oldUpdateMode.equals(newUpdateMode) || !oldTag.equals(newTag)
				|| !oldArtist.equals(newArtist))
		{
			WorkManager.getInstance().cancelUniqueWork("ANTONY");
			File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
			String[] children = dir.list();
			for (String child : children)
			{
				new File(dir, child).delete();
			}
			PixivArtWorker.enqueueLoad(true);
			if (!oldUpdateMode.equals(newUpdateMode))
			{
				Toast.makeText(getContext(), getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT).show();
			} else if (!oldArtist.equals(newArtist))
			{
				Toast.makeText(getContext(), getString(R.string.toast_newArtist), Toast.LENGTH_SHORT).show();
			} else if (!oldTag.equals(newTag))
			{
				Toast.makeText(getContext(), getString(R.string.toast_newTag), Toast.LENGTH_SHORT).show();
			} else
			{
				Toast.makeText(getContext(), getString(R.string.toast_newFilterSelect), Toast.LENGTH_SHORT).show();
			}
		}
	}

	// Redirects the user to Muzei's Play Store listing if it's not detected to be installed
	// TODO have a nicer dialog that explains why Muzei needs to be installed, instead of forcing them to the store
	private boolean isMuzeiInstalled()
	{
		boolean found = true;
		try
		{
			getContext().getPackageManager().getPackageInfo("net.nurik.roman.muzei", 0);
		} catch (PackageManager.NameNotFoundException ex)
		{
			found = false;
		}
		return found;
	}
}
