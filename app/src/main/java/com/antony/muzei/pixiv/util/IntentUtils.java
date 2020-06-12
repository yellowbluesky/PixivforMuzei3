package com.antony.muzei.pixiv.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

/**
 * Created by alvince on 2020/6/11
 *
 * @author alvince.zy@gmail.com
 */
public class IntentUtils {

    /**
     * Launch {@link Activity} safely with {@link Context}
     *
     * @see #launchActivity(Context, Intent, int)
     */
    public static boolean launchActivity(@NonNull Context context, @Nullable Intent intent) {
        return launchActivity(context, intent, -1);
    }

    /**
     * Launch {@link Activity} safely with {@link Context}
     *
     * @see #launchActivity(Context, Intent, int, CharSequence)
     */
    public static boolean launchActivity(@NonNull Context context, @Nullable Intent intent, int requestCode) {
        return launchActivity(context, intent, requestCode, "");
    }

    /**
     * @see #launchActivity(Context, Intent, int, CharSequence, Bundle)
     */
    public static boolean launchActivity(@NonNull Context context, @Nullable Intent intent, int requestCode,
                                         @Nullable CharSequence title) {
        return launchActivity(context, intent, requestCode, title, null);
    }

    /**
     * Launch {@link Activity} safely with {@link Context}
     *
     * @param requestCode req-code for {@link Activity#startActivityForResult(Intent, int)}, or -1
     * @param title       start activity chooser title
     * @param options     options for start activity
     * @return true if start succeed, else false
     */
    public static boolean launchActivity(@NonNull Context context, @Nullable Intent intent, int requestCode,
                                         @Nullable CharSequence title, @Nullable Bundle options) {
        Objects.requireNonNull(context);

        if (intent == null
                || context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false;
        }

        if (!(context instanceof ContextThemeWrapper)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        Intent pending = TextUtils.isEmpty(title) ? intent : Intent.createChooser(intent, title);

        if (context instanceof Activity && requestCode != -1) {
            ((Activity) context).startActivityForResult(pending, requestCode, options);
        } else {
            context.startActivity(pending, options);
        }
        return true;
    }

    /**
     * Launch {@link Activity} safely with {@link Fragment}
     *
     * @see #launchActivity(Fragment, Intent, int)
     */
    public static boolean launchActivity(@NonNull Fragment fragment, @Nullable Intent intent) {
        return launchActivity(fragment, intent, -1);
    }

    /**
     * Launch {@link Activity} safely with {@link Fragment}
     *
     * @see #launchActivity(Fragment, Intent, int, CharSequence)
     */
    public static boolean launchActivity(@NonNull Fragment fragment, @Nullable Intent intent, int requestCode) {
        return launchActivity(fragment, intent, requestCode, "");
    }

    /**
     * @see #launchActivity(Fragment, Intent, int, CharSequence, Bundle)
     */
    public static boolean launchActivity(@NonNull Fragment fragment, @Nullable Intent intent, int requestCode,
                                         @Nullable CharSequence title) {
        return launchActivity(fragment, intent, requestCode, title, null);
    }

    /**
     * Launch {@link Activity} safely with {@link Fragment}
     *
     * @param requestCode req-code for {@link Activity#startActivityForResult(Intent, int)}, or -1
     * @param title       intent chooser title if needed
     * @param options     options for start activity
     * @return true if start succeed, else false
     */
    public static boolean launchActivity(@NonNull Fragment fragment, @Nullable Intent intent, int requestCode,
                                         @Nullable CharSequence title, @Nullable Bundle options) {
        Objects.requireNonNull(fragment);

        Activity activity = fragment.getActivity();
        if (intent == null || activity == null) {
            return false;
        }
        if (activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false;
        }

        Intent pending = TextUtils.isEmpty(title) ? intent : Intent.createChooser(intent, title);

        if (requestCode != -1) {
            fragment.startActivityForResult(pending, requestCode, options);
        } else {
            fragment.startActivity(pending, options);
        }
        return true;
    }

}
