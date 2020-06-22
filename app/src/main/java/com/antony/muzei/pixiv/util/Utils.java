package com.antony.muzei.pixiv.util;

import android.os.Build;
import android.os.Looper;

/**
 * Created by alvince on 2020/6/22
 *
 * @author alvince.zy@gmail.com
 */
public final class Utils {

    public static boolean checkMainThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Looper.getMainLooper().isCurrentThread();
        }
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

}
