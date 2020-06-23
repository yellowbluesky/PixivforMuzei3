package com.antony.muzei.pixiv.util;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Fast predicates
 * <p/>
 * Created by alvince on 2020/6/22
 *
 * @author alvince.zy@gmail.com
 */
public final class Predicates {

    public static void requireMainThread() {
        requireMainThread("Current thread " + Thread.currentThread().getName() + " is not the main-thread");
    }

    public static void requireMainThread(@NonNull String message) {
        Objects.requireNonNull(message);

        if (!Utils.checkMainThread()) {
            throw new IllegalStateException(message);
        }
    }

}
