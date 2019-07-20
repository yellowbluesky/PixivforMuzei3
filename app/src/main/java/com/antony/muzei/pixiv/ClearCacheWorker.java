package com.antony.muzei.pixiv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;

public class ClearCacheWorker extends Worker
{
    public ClearCacheWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        // All this is needed to toast
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                Toast.makeText(getApplicationContext(), "auto clear cache toast within", Toast.LENGTH_SHORT).show();
            }
        });
        ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
        return Result.success();
    }
}
