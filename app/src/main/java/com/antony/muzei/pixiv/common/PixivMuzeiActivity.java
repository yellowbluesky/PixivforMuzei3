package com.antony.muzei.pixiv.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.antony.muzei.pixiv.PixivInstrumentation;
import com.antony.muzei.pixiv.util.HostManager;

public class PixivMuzeiActivity extends AppCompatActivity {

    private PixivInstrumentation mPixivInstrumentation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mPixivInstrumentation = new PixivInstrumentation();
        HostManager.get().init();
        super.onCreate(savedInstanceState);
    }

    @NonNull
    protected PixivInstrumentation requireInstrumentation() {
        PixivInstrumentation instrumentation = getInstrumentation();
        if (instrumentation == null) {
            throw new IllegalStateException("Activity " + this + "not prepared.");
        }
        return instrumentation;
    }

    @Nullable
    protected PixivInstrumentation getInstrumentation() {
        return mPixivInstrumentation;
    }

}
