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

package com.antony.muzei.pixiv.network;

import android.os.Build;
import android.util.Log;

import com.antony.muzei.pixiv.BuildConfig;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RestClient
{
	// Prints detailed network logs if built type is debug
	private static HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(s ->
			Log.v("ANTONY_REST", "message===" + s))
			.setLevel(BuildConfig.BUILD_TYPE.contentEquals("debug") ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
	private static final String HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c";
	private static Retrofit retrofitRanking;

	private static OkHttpClient okHttpClientRanking = new OkHttpClient.Builder()
			// Debug logging interceptor
			// TODO make this only work on DEBUG flavor builds
			.addInterceptor(httpLoggingInterceptor)
			// This interceptor only adds in the "format" "json" query parameter
			.addInterceptor(chain ->
			{
				Request original = chain.request();
				HttpUrl originalHttpUrl = original.url();
				HttpUrl url = originalHttpUrl.newBuilder()
						.addQueryParameter("format", "json")
						.build();
				Request request = original.newBuilder()
						.url(url)
						.build();
				return chain.proceed(request);
			})
			.build();

	private static Retrofit retrofitAuth;
	private static OkHttpClient okHttpClientAuth = new OkHttpClient.Builder()
			.addInterceptor(httpLoggingInterceptor)
			// This adds the necessary headers minus the auth header
			// The auth header is a (for the moment) dynamic header in RetrofitClientAuthJson
			.addInterceptor(chain ->
			{
				String rfc3339Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
				String dateSecretConcat = rfc3339Date + HASH_SECRET;
				String hashSecret = getHashSecret(dateSecretConcat);

				Request original = chain.request();
				Request request = original.newBuilder()
						.header("User-Agent", "PixivAndroidApp/5.0.155 (Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.MODEL + ")")
						.header("App-OS", "Android")
						.header("App-OS-Version", Build.VERSION.RELEASE)
						.header("App-Version", "5.0.166")
						//.header("Accept-Language", Locale.getDefault().toString())
						.header("X-Client-Time", rfc3339Date)
						.header("X-Client-Hash", hashSecret)
						.build();
				return chain.proceed(request);
			})
			.build();

	private static Retrofit retrofitImages;
	private static OkHttpClient imageHttpClient = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				Request original = chain.request();
				Request request = original.newBuilder()
						.header("User-Agent", "PixivAndroidApp/5.0.155 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")")
						.header("Referer", "https://www.pixiv.net")
						.build();
				return chain.proceed(request);
			})
			.addInterceptor(httpLoggingInterceptor)
			.build();
	private static Retrofit retrofitOauth;

	public static Retrofit getRetrofitRankingInstance()
	{
		if (retrofitRanking == null)
		{
			retrofitRanking = new Retrofit.Builder()
					.client(okHttpClientRanking)
					.baseUrl("https://www.pixiv.net")
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofitRanking;
	}

	public static Retrofit getRetrofitAuthInstance()
	{
		if (retrofitAuth == null)
		{
			retrofitAuth = new Retrofit.Builder()
					.client(okHttpClientAuth)
					.baseUrl("https://app-api.pixiv.net")
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofitAuth;
	}

	public static Retrofit getRetrofitImageInstance()
	{
		if (retrofitImages == null)
		{
			retrofitImages = new Retrofit.Builder()
					.client(imageHttpClient)
					.baseUrl("https://i.pximg.net")
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofitImages;
	}

	private static String getHashSecret(String dateSecretConcat)
	{
		try
		{
			MessageDigest digestInstance = MessageDigest.getInstance("MD5");
			byte[] messageDigest = digestInstance.digest(dateSecretConcat.getBytes());
			StringBuilder hexString = new StringBuilder();
			// this loop is horrifically inefficient on CPU and memory
			// but is only executed once to acquire a new access token
			// i.e. at most once per hour for normal use case
			for (byte aMessageDigest : messageDigest)
			{
				StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & aMessageDigest));
				while (h.length() < 2)
				{
					h.insert(0, "0");
				}
				hexString.append(h);
			}
			return hexString.toString();
		} catch (java.security.NoSuchAlgorithmException ex)
		{
			ex.printStackTrace();
		}
		// TODO replace this place holder
		return "";
	}

	public static Retrofit getRetrofitOauthInstance()
	{
		if (retrofitOauth == null)
		{
			retrofitOauth = new Retrofit.Builder()
					.baseUrl("https://oauth.secure.pixiv.net")
					.client(okHttpClientAuth)
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofitOauth;
	}
}
