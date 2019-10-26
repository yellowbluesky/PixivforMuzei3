/*
    This file is part of PixivforMuzei3.

    PixivforMuzei3 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program  is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.antony.muzei.pixiv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class SettingsActivity extends AppCompatActivity
{
	private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
	private String newCreds, oldCreds;
	private String oldUpdateMode, newUpdateMode;
	private String oldFilter, newFilter;
	private String oldTag, newTag;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);
		getSupportFragmentManager()
				.beginTransaction()
				.add(R.id.FeedPreferencesFragment, new SettingsFragment())
				.commit();

		if (!isMuzeiInstalled())
		{
			// You must have Muzei installed for this app to work
			// Click here to install Muzei
			final String appPackageName = "net.nurik.roman.muzei"; // getPackageName() from Context or Activity object
			try
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
			} catch (android.content.ActivityNotFoundException anfe)
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
			}
		}

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Stores user toggleable variables into a temporary store for later comparison in onDestroy()
		oldCreds = sharedPrefs.getString("pref_loginPassword", "");
		newCreds = oldCreds;

		oldUpdateMode = sharedPrefs.getString("pref_updateMode", "");
		newUpdateMode = oldUpdateMode;

		oldFilter = sharedPrefs.getString("pref_nsfwFilterLevel", "");
		newFilter = oldFilter;

		oldTag = sharedPrefs.getString("pref_tagSearch", "");
		newTag = oldTag;

		prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener()
		{
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
			{
				if (key.equals("pref_loginPassword"))
				{
					newCreds = sharedPrefs.getString("pref_loginPassword", "");
				} else if (key.equals("pref_updateMode"))
				{
					newUpdateMode = sharedPrefs.getString("oldUpdateMode", "");
				} else if (key.equals("pref_nsfwFilterLevel"))
				{
					newFilter = sharedPrefs.getString("pref_nsfwFilterLevel", "");
				} else if (key.equals("pref_tagSearch"))
				{
					newTag = sharedPrefs.getString("pref_tagSearch", "");
				}
			}
		};
	}

	@Override
	public void onResume()
	{
		super.onResume();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		sharedPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
	}

	// Functions in here action only on app exit
	@Override
	public void onStop()
	{
		super.onStop();
		// If new user credentials were entered and saved, then clear and invalidate existing stored user credentials
		if (!oldCreds.equals(newCreds))
		{
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor editor = sharedPrefs.edit();
			editor.remove("accessToken");
			editor.remove("refreshToken");
			editor.remove("deviceToken");
			editor.remove("accessTokenIssueTime");
			editor.commit();
			Toast.makeText(getApplicationContext(), getString(R.string.toast_newCredentials), Toast.LENGTH_SHORT).show();
		}

		// Automatic cache clearing at 1AM every night for as long as the setting is toggled active
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if (sharedPrefs.getBoolean("pref_autoClearMode", true))
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
			WorkManager.getInstance(getApplicationContext())
					.enqueueUniquePeriodicWork("PIXIV_CACHE_AUTO", ExistingPeriodicWorkPolicy.KEEP, request);
		} else
		{
			WorkManager.getInstance((getApplicationContext())).cancelAllWorkByTag("PIXIV_CACHE_AUTO");
		}

		// If user has changed update, filter mode, or search tag
		if (!oldUpdateMode.equals(newUpdateMode) || !oldFilter.equals(newFilter) || !oldTag.equals(newTag))
		{
			WorkManager manager = WorkManager.getInstance();
			Constraints constraints = new Constraints.Builder()
					.setRequiredNetworkType(NetworkType.CONNECTED)
					.build();
			OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ClearCacheWorker.class)
					.addTag("PIXIV_CACHE")
					.setConstraints(constraints)
					.build();
			manager.enqueueUniqueWork("PIXIV_CACHE", ExistingWorkPolicy.KEEP, request);
			if (!oldUpdateMode.equals(newUpdateMode))
			{
				Toast.makeText(getApplicationContext(), getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT).show();
			} else if (!oldFilter.equals(newFilter))
			{
				Toast.makeText(getApplicationContext(), getString(R.string.toast_newFilterMode), Toast.LENGTH_SHORT).show();
			} else
			{
				Toast.makeText(getApplicationContext(), "New search tag, clearing image cache", Toast.LENGTH_SHORT).show();
			}
		}
	}

	public boolean isMuzeiInstalled()
	{
		boolean found = true;
		try
		{
			getApplicationContext().getPackageManager().getPackageInfo("net.nurik.roman.muzei", 0);
		} catch (PackageManager.NameNotFoundException ex)
		{
			found = false;
		}
		return found;
	}

	// Functions in here action immediately on user interaction
	public static class SettingsFragment extends PreferenceFragmentCompat
	{
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
		{
			setPreferencesFromResource(R.xml.feed_preferences_layout, rootKey);

			// Immediately clear cache
			findPreference(getString(R.string.button_clearCache)).setOnPreferenceClickListener(preference ->
			{
				WorkManager manager = WorkManager.getInstance();
				Constraints constraints = new Constraints.Builder()
						.setRequiredNetworkType(NetworkType.CONNECTED)
						.build();
				OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ClearCacheWorker.class)
						.addTag("PIXIV_CACHE")
						.setConstraints(constraints)
						.build();
				manager.enqueueUniqueWork("PIXIV_CACHE", ExistingWorkPolicy.KEEP, request);
				Toast.makeText(getContext(), getString(R.string.toast_clearingCache), Toast.LENGTH_SHORT).show();
				return true;
			});

			// Show authentication status as summary string below login button
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
			if (sharedPrefs.getString("accessToken", "").isEmpty())
			{
				findPreference("pref_loginId").setSummary(getString(R.string.prefSummary_authFail));
				//loginId.setSummary(Long.toString(System.currentTimeMillis()));
			} else
			{
				String summaryString = getString(R.string.prefSummary_authSuccess) + " " + sharedPrefs.getString("pref_loginId", "");
				findPreference("pref_loginId").setSummary(summaryString);
//                Uri profileImageUri = Uri.parse(sharedPrefs.getString("profileImageUri", ""));
//                loginId.setIcon();
			}

			String updateMode = sharedPrefs.getString("pref_updateMode", "daily_rank");
			// If existing update mode is tag search, reveal tag search EditTextPreference
			if (updateMode.equals("tag_search"))
			{
				findPreference("pref_tagSearch").setVisible(true);
			}
			else if(updateMode.equals("artist"))
			{
				findPreference("pref_artistId").setVisible(true);
			}

			// if existing update mode is feed, bookmark, or tag, reveal login category
			if (updateMode.equals("follow") || updateMode.equals("bookmark") || updateMode.equals("tag_search") || updateMode.equals("artist"))
			{
				findPreference("prefCat_loginSettings").setVisible(true);
			}

			// Hide or show elements depending on update mode chosen
			findPreference("pref_updateMode").setOnPreferenceChangeListener((preference, newValue) ->
			{
				if (newValue.toString().equals("follow") || newValue.toString().equals("bookmark")
						|| newValue.toString().equals("tag_search") || newValue.toString().equals("artist"))
				{
					EditTextPreference tagSearchPref = findPreference("pref_tagSearch");
					EditTextPreference artistIdPref = findPreference("pref_artistId");
					tagSearchPref.setVisible(false);
					artistIdPref.setVisible(false);
					findPreference("prefCat_loginSettings").setVisible(true);
					if (newValue.toString().equals("tag_search"))
					{
						tagSearchPref.setVisible(true);
					} else if(newValue.toString().equals("artist"))
					{
						artistIdPref.setVisible(true);
					}
				} else
				{
					findPreference("prefCat_loginSettings").setVisible(false);
				}
				return true;
			});

			// Hide app icon if switch is activated
			if (!sharedPrefs.getBoolean("pref_hideLauncherIcon", false))
			{
				PackageManager p = getContext().getPackageManager();
				ComponentName componentName = new ComponentName("com.antony.muzei.pixiv", "com.antony.muzei.pixiv.SettingsActivity"); // activity which is first time open in manifiest file which is declare as <category android:name="android.intent.category.LAUNCHER" />
				p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
			} else
			{
				PackageManager p = getContext().getPackageManager();
				ComponentName componentName = new ComponentName("com.antony.muzei.pixiv", "com.antony.muzei.pixiv.SettingsActivity");
				p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
			}
		}
	}
}