package com.antony.muzei.pixiv.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.PixivArtProvider;
import com.antony.muzei.pixiv.R;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderClient;
import com.google.android.apps.muzei.api.provider.ProviderContract;

public class SettingsActivity extends AppCompatActivity
{
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    private String newCreds, oldCreds;
    private String oldUpdateMode, newUpdateMode;
    private String oldFilter, newFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.FeedPreferencesFragment, new SettingsFragment())
                .commit();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        oldCreds = sharedPrefs.getString("pref_loginPassword", "");
        newCreds = oldCreds;

        oldUpdateMode = sharedPrefs.getString("pref_updateMode", "");
        newUpdateMode = oldUpdateMode;

        oldFilter = sharedPrefs.getString("pref_nsfwFilterLevel", "");
        newFilter = oldFilter;

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

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (!oldCreds.equals(newCreds))
        {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString("accessToken", "");
            editor.putString("refreshToken", "");
            editor.putString("deviceToken", "");
            editor.putLong("accessTokenIssueTime", 0);
            editor.commit();
            Toast.makeText(getApplicationContext(), "New credentials found", Toast.LENGTH_SHORT).show();
        }

        if (!oldUpdateMode.equals(newUpdateMode) || !oldFilter.equals(newFilter))
        {
            ProviderClient client = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class);
            client.setArtwork(new Artwork());
            Toast.makeText(getApplicationContext(), "New update or filtering mode, clearing cache", Toast.LENGTH_SHORT).show();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.feed_preferences_layout, rootKey);
            Preference buttonClearCache = findPreference(getString(R.string.button_clearCache));
            buttonClearCache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    ProviderClient client = ProviderContract.getProviderClient(getContext(), PixivArtProvider.class);
                    client.setArtwork(new Artwork());
                    Toast.makeText(getContext(), "Clearing cache", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }
}