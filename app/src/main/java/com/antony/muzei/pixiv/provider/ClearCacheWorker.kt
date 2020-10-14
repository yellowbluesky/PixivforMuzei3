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
package com.antony.muzei.pixiv.provider

import android.content.Context
import android.os.Environment
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.antony.muzei.pixiv.provider.PixivArtWorker.Companion.enqueueLoad
import java.io.File

class ClearCacheWorker(
        context: Context,
        params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        for (child in dir!!.list()) {
            File(dir, child).delete()
        }
        enqueueLoad(true, applicationContext)
        return Result.success()
    }
}
