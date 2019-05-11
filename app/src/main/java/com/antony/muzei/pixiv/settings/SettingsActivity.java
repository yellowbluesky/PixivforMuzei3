package com.antony.muzei.pixiv.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.PixivArtProvider;
import com.antony.muzei.pixiv.R;

public class SettingsActivity extends AppCompatActivity
{
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    private String oldCreds;
    private String newCreds;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.FeedPreferencesFragment, new SettingsFragment())
                .commit();

        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        oldCreds = sharedPrefs.getString("pref_loginPassword", "");
        Log.d("PIXIV", oldCreds);

        prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener()
        {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
            {
                if (!key.equals("pref_loginPassword"))
                {
                    return;
                }
                newCreds = sharedPrefs.getString("pref_loginPassword", "");
                Log.d("PIXIV", newCreds);
            }
        };
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
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.feed_preferences_layout, rootKey);
        }
    }
}