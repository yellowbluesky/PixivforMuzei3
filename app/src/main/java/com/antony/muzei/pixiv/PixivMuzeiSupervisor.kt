/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.antony.muzei.pixiv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.antony.muzei.pixiv.annotation.IOThread
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.util.Predicates

/**
 * Created by alvince on 2020/6/16
 *
 * @author alvince.zy@gmail.com
 */
object PixivMuzeiSupervisor {

    const val INTENT_CAT_LOCAL = BuildConfig.APPLICATION_ID + ".intent.category.LOCAL_BROADCAST"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private lateinit var appContext: Context
    private lateinit var appInstrumentation: PixivInstrumentation

    private var start: Boolean = false

    @MainThread
    fun start(context: Context) {
        Predicates.requireMainThread()

        appContext = context.applicationContext
        appInstrumentation = PixivInstrumentation()
        start = true
    }

    @Throws(AccessTokenAcquisitionException::class)
    @IOThread
    fun getAccessToken(): String {
        if (!start) {
            return ""
        }
        return appInstrumentation.getAccessToken(appContext)
    }

    fun broadcastLocal(intent: Intent) {
        require(intent.action?.isNotEmpty() == true)

        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
    }

    @JvmStatic
    fun post(action: Runnable) {
        mainHandler.post(action)
    }

}
