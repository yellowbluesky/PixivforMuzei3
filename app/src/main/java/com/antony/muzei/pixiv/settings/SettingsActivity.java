package com.antony.muzei.pixiv.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.antony.muzei.pixiv.R;

public class SettingsActivity extends AppCompatActivity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);
		getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.settings, new SettingsFragment())
				.commit();
//		ActionBar actionBar = getSupportActionBar();
//		if (actionBar != null)
//		{
//			actionBar.setDisplayHomeAsUpEnabled(true);
//		}
//		String sharedPrefFile = getApplicationContext().getPackageName() + "_implementation";
//		SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
	}

	public static class SettingsFragment extends PreferenceFragmentCompat
	{
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
		{
			setPreferencesFromResource(R.xml.root_preferences, rootKey);
		}
	}
}