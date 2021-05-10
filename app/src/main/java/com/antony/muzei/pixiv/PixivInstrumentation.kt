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
import android.util.Log
import androidx.preference.PreferenceManager
import com.antony.muzei.pixiv.PixivProviderConst.*
import com.antony.muzei.pixiv.annotation.IOThread
import com.antony.muzei.pixiv.login.OAuthResponseService
import com.antony.muzei.pixiv.login.OauthResponse
import com.antony.muzei.pixiv.provider.PixivArtProviderDefines
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

        @JvmStatic
        fun login(verifierCode: String, authorizationCode: String): OauthResponse {
            // Building the parameters
            val formBody = mapOf(
                    "client_id" to BuildConfig.PIXIV_CLIENT_ID,
                    "client_secret" to BuildConfig.PIXIV_CLIENT_SEC,
                    "grant_type" to "authorization_code",
                    "code_verifier" to verifierCode,
                    "code" to authorizationCode,
                    "redirect_uri" to PixivArtProviderDefines.PIXIV_REDIRECT_URL,
                    "include_policy" to "true"
            )

            // Building and executing the network call
            val service = RestClient.getRetrofitOauthInstance().create(OAuthResponseService::class.java)
            try {
                val response = service.postRefreshToken(formBody).execute()
                if (!response.isSuccessful) {
                    throw AccessTokenAcquisitionException("Error using refresh token to get new access token")
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, response.toString())
                }

                // If we've gotten to this point, we have avoided any network errors, authentication errors, and
                // have on hand a set of tokens to use
                return response.body()!!
            } catch (ex: IOException) {
                ex.printStackTrace()
                throw AccessTokenAcquisitionException("getAccessToken(): Error executing call")
            }
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
                        RestClient.getRetrofitOauthInstance().create(OAuthResponseService::class.java)
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
