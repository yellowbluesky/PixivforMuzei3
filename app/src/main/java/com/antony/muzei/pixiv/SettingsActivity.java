package com.antony.muzei.pixiv;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.antony.muzei.pixiv.ClearCacheWorker;
import com.antony.muzei.pixiv.PixivArtProvider;
import com.antony.muzei.pixiv.PixivArtWorker;
import com.antony.muzei.pixiv.R;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import java.util.concurrent.TimeUnit;

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

        // Stores user toggleable variables into a temporary store for later comparison in onDestroy()
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
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
    }

    // Functions in here action only on app exit
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        // If new user credentials were entered and saved, then clear and invalidate existing stored user credentials
        if (!oldCreds.equals(newCreds))
        {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString("accessToken", "");
            editor.putString("refreshToken", "");
            editor.putString("deviceToken", "");
            editor.putLong("accessTokenIssueTime", 0);
            editor.commit();
            Toast.makeText(getApplicationContext(), getString(R.string.toast_newCredentials), Toast.LENGTH_SHORT).show();
        }

        // TODO
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPrefs.getBoolean("pref_autoClearMode", false))
        {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ClearCacheWorker.class, 24, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .addTag("PIXIV_CACHE")
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork("PIXIV_CACHE", ExistingPeriodicWorkPolicy.KEEP, request);
        } else
        {
            WorkManager.getInstance((getApplicationContext())).cancelAllWorkByTag("PIXIV_CACHE");
        }

        if (!oldUpdateMode.equals(newUpdateMode))
        {
            //WorkManager manager = WorkManager.getInstance();
            //manager.cancelAllWorkByTag("PIXIV");
            ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
            Toast.makeText(getApplicationContext(), getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT).show();
        } else if (!oldFilter.equals(newFilter))
        {
            //WorkManager manager = WorkManager.getInstance();
            //manager.cancelAllWorkByTag("PIXIV");
            ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
            Toast.makeText(getApplicationContext(), getString(R.string.toast_newFilterMode), Toast.LENGTH_SHORT).show();
        }


    }

    // Functions in here action immediately on user interaction
    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.feed_preferences_layout, rootKey);

            // Immediately clear cache
            Preference buttonClearCache = findPreference(getString(R.string.button_clearCache));
            buttonClearCache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    ProviderContract.getProviderClient(getContext(), PixivArtProvider.class).setArtwork(new Artwork());
                    Toast.makeText(getContext(), getString(R.string.toast_clearingCache), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            // Manually force pull a new unage
            Preference buttonForcePull = findPreference(getString(R.string.button_forcePull));
            buttonForcePull.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    PixivArtWorker.enqueueLoad();
                    return true;
                }
            });

            Preference loginId = findPreference("pref_loginId");
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (sharedPrefs.getString("accessToken", "").isEmpty())
            {
                loginId.setSummary(getString(R.string.prefSummary_authFail));
            } else
            {
                String summaryString = getString(R.string.prefSummary_authSuccess) + " " + sharedPrefs.getString("pref_loginId", "");
                loginId.setSummary(summaryString);
//                Uri profileImageUri = Uri.parse(sharedPrefs.getString("profileImageUri", ""));
//                loginId.setIcon();
            }
        }
    }
}