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

package com.antony.muzei.pixiv.provider;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException;
import com.antony.muzei.pixiv.login.OauthResponse;
import com.antony.muzei.pixiv.login.OAuthResponseService;
import com.antony.muzei.pixiv.provider.network.RestClient;
import com.antony.muzei.pixiv.provider.network.RubyHttpDns;
import com.antony.muzei.pixiv.provider.network.RubySSLSocketFactory;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;

// TODO deprecate this entire class
public class PixivArtService
{
    private static final String LOG_TAG = "ANTONY_SERVICE";
    private static OkHttpClient httpClient = new OkHttpClient();

    static
    {
        Log.d(LOG_TAG, "locale is : " + Locale.getDefault().getISO3Language());
        /* SNI Bypass begin */
        if (Locale.getDefault().getISO3Language().equals("zho"))
        {
            Log.d(LOG_TAG, "Bypass in effect");
            HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(
                    s -> Log.v("ANTONY_SERVICE", "message====" + s));

            httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.sslSocketFactory(new RubySSLSocketFactory(), new X509TrustManager()
            {
                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                {

                }

                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                {

                }

                @Override
                public X509Certificate[] getAcceptedIssuers()
                {
                    return new X509Certificate[0];
                }
            });//SNI bypass
            builder.hostnameVerifier((s, sslSession) -> true);//disable hostnameVerifier
            builder.addInterceptor(httpLoggingInterceptor);
            builder.dns(new RubyHttpDns());//define the direct ip address
            httpClient = builder.build();
            /* SNI Bypass end */
        }
    }

    static String refreshAccessToken(SharedPreferences sharedPrefs) throws AccessTokenAcquisitionException
    {
        Log.d(LOG_TAG, "getAccessToken(): Entering");
        String accessToken = sharedPrefs.getString("accessToken", "");
        long accessTokenIssueTime = sharedPrefs.getLong("accessTokenIssueTime", 0);

        if ((System.currentTimeMillis() / 1000) - accessTokenIssueTime < 3600)
        {
            Log.i(LOG_TAG, "Existing access token found, using it");
            Log.d(LOG_TAG, "getAccessToken(): Exited");
            return accessToken;
        }
        Log.i(LOG_TAG, "Access token expired, acquiring new access token using refresh token");

        try
        {
            Map<String, String> fieldParams = new HashMap<>();
            fieldParams.put("get_secure_url", "1");
            fieldParams.put("client_id", "MOBrBDS8blbauoSck0ZfDbtuzpyT");
            fieldParams.put("client_secret", "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj");
            fieldParams.put("grant_type", "refresh_token");
            fieldParams.put("refresh_token", sharedPrefs.getString("refreshToken", ""));

            boolean bypassActive = sharedPrefs.getBoolean("pref_enableNetworkBypass", false);

            OAuthResponseService service = RestClient.getRetrofitOauthInstance(bypassActive).create(OAuthResponseService.class);
            Call<OauthResponse> call = service.postRefreshToken(fieldParams);
            Response<OauthResponse> response = call.execute();
            if (!response.isSuccessful())
            {
                throw new AccessTokenAcquisitionException("Error using refresh token to get new access token");
            }
            OauthResponse oauthResponse = response.body();
            PixivArtWorker.storeTokens(sharedPrefs, oauthResponse);
            accessToken = oauthResponse.getPixivOauthResponse().getAccess_token();

            // Authentication succeeded, storing tokens returned from Pixiv
            //Log.d(LOG_TAG, authResponseBody.toString());
//            Uri profileImageUri = storeProfileImage(authResponseBody.getJSONObject("response"));
//            sharedPrefs.edit().putString("profileImageUri", profileImageUri.toString()).apply();
            //PixivArtWorker.storeTokens(sharedPrefs, authResponseBody.getJSONObject("response"));
        } catch (IOException ex)
        {
            ex.printStackTrace();
            throw new AccessTokenAcquisitionException("getAccessToken(): Error executing call");
        }
        Log.i(LOG_TAG, "Acquired access token");
        Log.d(LOG_TAG, "getAccessToken(): Exited");
        return accessToken;
    }

    // Function only used to add artworks to bookmarks on the old commands
    // this function to be removed, doesn't fit with anything other method
    static void sendPostRequest(String accessToken, String token)
    {
        HttpUrl rankingUrl = new HttpUrl.Builder()
                .scheme("https")
                .host("app-api.pixiv.net")
                .addPathSegments("v2/illust/bookmark/add")
                .build();
        RequestBody authData = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("illust_id", token)
                .addFormDataPart("restrict", "public")
                .build();
        Request request = new Request.Builder()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(authData)
                .url(rankingUrl)
                .build();
        try

        {
            httpClient.newCall(request).execute();
        } catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
