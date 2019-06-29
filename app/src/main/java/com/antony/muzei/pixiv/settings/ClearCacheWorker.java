package com.antony.muzei.pixiv.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.antony.muzei.pixiv.PixivArtProvider;
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
        ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).setArtwork(new Artwork());
        return Result.success();
    }
}
