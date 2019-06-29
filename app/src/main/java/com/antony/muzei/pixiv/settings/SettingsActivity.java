package com.antony.muzei.pixiv.settings;

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
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.antony.muzei.pixiv.PixivArtProvider;
import com.antony.muzei.pixiv.R;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
                switch (key)
                {
                    case "pref_loginPassword":
                        newCreds = sharedPrefs.getString("pref_loginPassword", "");
                        break;
                    case "pref_updateMode":
                        newUpdateMode = sharedPrefs.getString("oldUpdateMode", "");
                        break;
                    case "pref_nsfwFilterLevel":
                        newFilter = sharedPrefs.getString("pref_nsfwFilterLevel", "");
                        break;
                }
//                if (key.equals("pref_loginPassword"))
//                {
//                    newCreds = sharedPrefs.getString("pref_loginPassword", "");
//                } else if (key.equals("pref_updateMode"))
//                {
//                    newUpdateMode = sharedPrefs.getString("oldUpdateMode", "");
//                } else if (key.equals("pref_nsfwFilterLevel"))
//                {
//                    newFilter = sharedPrefs.getString("pref_nsfwFilterLevel", "");
//                }
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

        if (!oldUpdateMode.equals(newUpdateMode))
        {
            //WorkManager.getInstance().cancelAllWorkByTag("PIXIV");
            ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
            Toast.makeText(getApplicationContext(), getString(R.string.toast_newUpdateMode), Toast.LENGTH_SHORT).show();
        } else if (!oldFilter.equals(newFilter))
        {
            //WorkManager.getInstance().cancelAllWorkByTag("PIXIV");
            ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
            Toast.makeText(getApplicationContext(), getString(R.string.toast_newFilterMode), Toast.LENGTH_SHORT).show();
        }
        if (PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()).getBoolean("pref_autoClearMode", false))
        {
            WorkManager manager = WorkManager.getInstance(getApplicationContext());
            ListenableFuture<List<WorkInfo>> future = manager.getWorkInfosByTag("PIXIV_CACHECLEAR");
            try
            {
                List<WorkInfo> list = future.get();
                if ((list == null) || (list.size() == 0))
                {
                    // Calculating the number of hours until 4AM
                    Calendar timeNow = Calendar.getInstance();
                    int hoursNow = timeNow.get(Calendar.HOUR_OF_DAY);
                    int hoursUntilFour = hoursNow > 23 ? 4 - hoursNow : hoursNow - 4;

                    Constraints constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();
                    PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ClearCacheWorker.class, 24, TimeUnit.HOURS)
                            .setInitialDelay(hoursUntilFour, TimeUnit.HOURS)
                            .addTag("PIXIV_CACHECLEAR")
                            .setConstraints(constraints)
                            .build();
                    manager.enqueueUniquePeriodicWork("PIXIV_CACHECLEAR", ExistingPeriodicWorkPolicy.KEEP, request);
                    Toast.makeText(getApplicationContext(), "Automatically clearing cache", Toast.LENGTH_SHORT).show();
                }
            } catch (InterruptedException | ExecutionException ex)
            {
                ex.printStackTrace();
            }
        } else
        {
            WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("PIXIV_CACHECLEAR");
            Toast.makeText(getApplicationContext(), "Not automatically clearing cache", Toast.LENGTH_SHORT).show();
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
                    ProviderContract.getProviderClient(getContext(), PixivArtProvider.class).setArtwork(new Artwork());
                    Toast.makeText(getContext(), getString(R.string.toast_clearingCache), Toast.LENGTH_SHORT).show();
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