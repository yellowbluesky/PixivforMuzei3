package com.antony.muzei.pixiv

import android.app.Application
import android.content.Context

class PixivMuzei : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object {
        var context: Context? = null
    }
}
