package com.antony.muzei.pixiv

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.PixivProviderConst.*
import com.antony.muzei.pixiv.annotation.IOThread
import com.antony.muzei.pixiv.login.OAuthResponseService
import com.antony.muzei.pixiv.login.OauthResponse
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.provider.network.RestClient
import java.io.IOException

/**
 * Instrumentation for Pixiv service
 *
 * Created by alvince on 2020/6/13
 *
 * @author alvince.zy@gmail.com
 */
class PixivInstrumentation {

    companion object {
        const val INTENT_ACTION_ACCESS_TOKEN_MISSING = BuildConfig.APPLICATION_ID + ".intent.action.ACCESS_TOKEN_MISSING"

        private const val TAG = "PixivInstrumentation"

        @JvmStatic
        fun updateTokenLocal(context: Context, response: OauthResponse.PixivOauthResponse) {
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit()
                .apply {
                    putString(PREFERENCE_PIXIV_ACCESS_TOKEN, response.access_token)
                    putString(PREFERENCE_PIXIV_REFRESH_TOKEN, response.refresh_token)
                    putLong(PREFERENCE_PIXIV_UPDATE_TOKEN_TIMESTAMP, System.currentTimeMillis().div(1000))

                    response.user?.also { user ->
                        putString("userId", user.id)
                        putString("name", user.name)
                    }
                }
                .apply()
        }
    }

    /**
     * Get pixiv access-token for requests
     */
    @IOThread
    fun getAccessToken(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).let { prefs ->
            prefs.getLong(PREFERENCE_PIXIV_UPDATE_TOKEN_TIMESTAMP, 0L)
                .takeIf { accessTokenIssueTime ->
                    System.currentTimeMillis().div(1000) - accessTokenIssueTime < 3600
                }
                ?.let {
                    prefs.getString(PREFERENCE_PIXIV_ACCESS_TOKEN, "")
                }
                ?: refreshAccessToken(context)
        }

    /**
     * Schedule refresh pixiv accessToken, and then return it
     */
    @IOThread
    fun refreshAccessToken(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return doFetchPixivToken(
            context,
            prefs.getString(PREFERENCE_PIXIV_REFRESH_TOKEN, ""),
            prefs.getBoolean("pref_enableNetworkBypass", false)
        ) ?: ""
    }

    @Throws(AccessTokenAcquisitionException::class)
    private fun doFetchPixivToken(context: Context, refreshToken: String?, bypassActive: Boolean = false) =
        mutableMapOf(
            "get_secure_url" to 1.toString(),
            "client_id" to BuildConfig.PIXIV_CLIENT_ID,
            "client_secret" to BuildConfig.PIXIV_CLIENT_SEC,
            "grant_type" to "refresh_token"
        ).apply {
            refreshToken
                ?.takeIf { it.isNotEmpty() }
                ?.also { put("refresh_token", it) }
        }.let { params ->
            val service =
                RestClient.getRetrofitOauthInstance(bypassActive).create(OAuthResponseService::class.java)
            try {
                val call = service.postRefreshToken(params)
                val response = call.execute()
                if (!response.isSuccessful) {
                    throw AccessTokenAcquisitionException("Error using refresh token to get new access token")
                }
                response
            } catch (ex: IOException) {
                ex.printStackTrace()
                throw AccessTokenAcquisitionException("getAccessToken(): Error executing call")
            }
        }.let { response ->
            response.body()?.let { resp ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, resp.toString())
                }
                resp.pixivOauthResponse?.let {
                    updateTokenLocal(context, it)
                    it.access_token
                }
            }
            /*
            Uri profileImageUri = storeProfileImage(authResponseBody.getJSONObject("response"));
            sharedPrefs.edit().putString("profileImageUri", profileImageUri.toString()).apply();
            */
        }

}
