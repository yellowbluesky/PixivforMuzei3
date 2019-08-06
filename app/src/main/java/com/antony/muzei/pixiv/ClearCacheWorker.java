package com.antony.muzei.pixiv;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;

import org.apache.commons.io.FileUtils;

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
        Uri conResUri = ProviderContract.getProviderClient(getApplicationContext(), PixivArtProvider.class).getContentUri();
        ContentResolver conRes = getApplicationContext().getContentResolver();
        conRes.delete(conResUri, null, null);
        PixivArtWorker.enqueueLoad(true);
        FileUtils.deleteQuietly(getApplicationContext().getCacheDir());
        return Result.success();
    }
}
