package com.antony.muzei.pixiv.provider

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.*
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.getAccessToken
import com.antony.muzei.pixiv.PixivProviderConst.AUTH_MODES
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.PixivRankingFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Illusts
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import retrofit2.Call
import java.util.concurrent.TimeUnit

class PixivArtWorker2(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    companion object {
        private const val LOG_TAG = "ANTONY_WORKER"
        private const val WORKER_TAG = "ANTONY"

        // Variable that tracks if the artwork cache needs to be cleared
        private var clearArtwork = false

        internal fun enqueueLoad(clearArtworkRequested: Boolean, context: Context) {
            if (clearArtworkRequested) {
                clearArtwork = true
            }

            Constraints.Builder().apply {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }.let { builder ->
                OneTimeWorkRequest.Builder(PixivArtWorker2::class.java)
                    .setConstraints(builder.build())
                    .addTag(WORKER_TAG)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                    .build()
            }.let { request ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(WORKER_TAG, ExistingWorkPolicy.KEEP, request)
            }
            // The Work must be a UniqueWork
            // If not unique work, multiple works can be submitted at the processed at the same time
            // This can lead to race conditions if a new access token is needed
            // Additionally, we definitely do not want to spam the API
        }
    }

    // First determines with an auth update mode is possible
    // Then executes the network calls to get a JSON
    // Passes work off the the filters
    // Returns a list of Artwork
    // TODO new name for this function
    private fun getArtworks(): MutableList<Artwork>? {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var updateMode = sharedPrefs.getString("pref_updateMode", "daily")

        // Gets an up to date access token if required
        if (AUTH_MODES.contains(updateMode)) {
            try {
                getAccessToken()
            } catch (e: AccessTokenAcquisitionException) {
                updateMode = authHandleAuthFailure(sharedPrefs) ?: return null
            }
        }

        // App has functionality to temporarily or permanently change the update mode if authentication fails
        // i.e. update mode can change between last if block and this if block
        // Thus two identical if statements are required
        val artworks: MutableList<Artwork>
        if (AUTH_MODES.contains(updateMode)) {

        } else {

        }
        Log.i(
            LOG_TAG, "Submitting " + sharedPrefs.getInt("prefSlider_numToDownload", 2) +
                    " artworks"
        )
        return mutableListOf<Artwork>()
    }

    private fun authHandleAuthFailure(sharedPrefs: SharedPreferences): String? {
        Log.i(LOG_TAG, "Failed to acquire access token")
        when (sharedPrefs.getString("pref_authFailAction", "changeDaily")) {
            "changeDaily" -> {
                Log.i(LOG_TAG, "Changing mode to daily")
                sharedPrefs.edit().putString("pref_updateMode", "daily").apply()
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(
                        applicationContext,
                        R.string.toast_authFailedSwitch,
                        Toast.LENGTH_SHORT
                    ).show()
                })
                return "daily"
            }
            "doNotChange_downDaily" -> {
                Log.i(LOG_TAG, "Downloading a single daily")
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(
                        applicationContext,
                        R.string.toast_authFailedDown,
                        Toast.LENGTH_SHORT
                    ).show()
                })
                return "daily"
            }
            "doNotChange_doNotDown" -> {
                Log.i(LOG_TAG, "Retrying with no changes")
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(
                        applicationContext,
                        R.string.toast_authFailedRetry,
                        Toast.LENGTH_SHORT
                    ).show()
                })
                return null
            }
        }
        return null
    }

    override fun doWork(): Result {
        val providerClient = getProviderClient(applicationContext, PixivArtProvider::class.java)

        val artworks = getArtworks() ?: return Result.retry()

        if (clearArtwork) {
            clearArtwork = false
            providerClient.setArtwork(artworks)
        } else {
            providerClient.addArtwork(artworks)
        }

        return Result.success()
    }
}
