package com.antony.muzei.pixiv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PixivArtWorker extends Worker
{
	public PixivArtWorker(
			@NonNull Context context,
			@NonNull WorkerParameters params)
	{
		super(context, params);
	}

	static void enqueueLoad()
	{
		WorkManager workManager = WorkManager.getInstance();
	}

	@NonNull
	@Override
	public Result doWork()
	{
		return null;
	}
}
