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

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import com.antony.muzei.pixiv.BuildConfig;
import com.antony.muzei.pixiv.RubyHttpDns;
import com.antony.muzei.pixiv.RubySSLSocketFactory;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

public class RestClient
{
	private static final String HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c";
	// Prints detailed network logs if built type is debug
	private static HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(s ->
			Log.v("ANTONY_REST", "message===" + s))
			.setLevel(BuildConfig.BUILD_TYPE.contentEquals("debug") ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
	private static X509TrustManager x509TrustManager = new X509TrustManager()
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
	};

	// Used for acquiring Ranking JSON
	public static Retrofit getRetrofitRankingInstance(boolean bypass)
	{
		OkHttpClient.Builder okHttpClientRankingBuilder = new OkHttpClient.Builder()
				// Debug logging interceptor
				.addNetworkInterceptor(httpLoggingInterceptor)
				// This interceptor only adds in the "format" "json" query parameter
				.addInterceptor(chain ->
				{
					Request original = chain.request();
					HttpUrl originalHttpUrl = original.url();
					HttpUrl url = originalHttpUrl.newBuilder()
							.addQueryParameter("format", "json")
							.build();
					Request request = original.newBuilder()
							// Using the Android User-Agent returns a HTML of the ranking page, instead of the JSON I need
							.header("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0")
							.header("Referer", "https://www.pixiv.net")
							.url(url)
							.build();
					return chain.proceed(request);
				})
				.connectTimeout(60L, TimeUnit.SECONDS)
				.readTimeout(60L, TimeUnit.SECONDS)
				.writeTimeout(60L, TimeUnit.SECONDS);
		if (bypass)
		{
			okHttpClientRankingBuilder
					.sslSocketFactory(new RubySSLSocketFactory(), x509TrustManager)
					.hostnameVerifier((s, sslSession) -> true)
					.dns(new RubyHttpDns());
		}
		return new Retrofit.Builder()
				.client(okHttpClientRankingBuilder.build())
				.baseUrl("https://www.pixiv.net")
				.addConverterFactory(MoshiConverterFactory.create())
				.build();
	}

	// Used for acquiring auth feed mode JSON
	public static Retrofit getRetrofitAuthInstance(boolean bypass)
	{
		OkHttpClient.Builder okHttpClientAuthBuilder = new OkHttpClient.Builder()
				.addNetworkInterceptor(httpLoggingInterceptor)
				// This adds the necessary headers minus the auth header
				// The auth header is a (for the moment) dynamic header in RetrofitClientAuthJson
				.addInterceptor(chain ->
				{
					String rfc3339Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ").format(new Date());
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
				});
		if (bypass)
		{
			okHttpClientAuthBuilder
					.sslSocketFactory(new RubySSLSocketFactory(), x509TrustManager)
					.hostnameVerifier((s, sslSession) -> true)
					.dns(new RubyHttpDns());
		}
		return new Retrofit.Builder()
				.client(okHttpClientAuthBuilder.build())
				.baseUrl("https://app-api.pixiv.net")
				.addConverterFactory(MoshiConverterFactory.create())
				.build();
	}

	// Downloads images from any source
	public static Retrofit getRetrofitImageInstance(boolean bypass)
	{
		OkHttpClient.Builder imageHttpClientBuilder = new OkHttpClient.Builder()
				.addInterceptor(chain ->
				{
					Request original = chain.request();
					Request request = original.newBuilder()
							.header("User-Agent", "PixivAndroidApp/5.0.155 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")")
							.header("Referer", "https://www.pixiv.net")
							.build();
					return chain.proceed(request);
				})
				.addNetworkInterceptor(httpLoggingInterceptor);
		if (bypass)
		{
			imageHttpClientBuilder
					.sslSocketFactory(new RubySSLSocketFactory(), x509TrustManager)
					.hostnameVerifier((s, sslSession) -> true)
					.dns(new RubyHttpDns());
		}
		return new Retrofit.Builder()
				.client(imageHttpClientBuilder.build())
				.baseUrl("https://i.pximg.net")
				.addConverterFactory(MoshiConverterFactory.create())
				.build();
	}

	// Used for getting an accessToken from a refresh token or username / password
	public static Retrofit getRetrofitOauthInstance(boolean bypass)
	{
		OkHttpClient.Builder okHttpClientAuthBuilder = new OkHttpClient.Builder()
				.addNetworkInterceptor(httpLoggingInterceptor)
				// This adds the necessary headers minus the auth header
				// The auth header is a (for the moment) dynamic header in RetrofitClientAuthJson
				.addInterceptor(chain ->
				{
					String rfc3339Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ").format(new Date());
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
				});
		if (bypass)
		{
			Log.v("REST", "bypass active");
			okHttpClientAuthBuilder
					.sslSocketFactory(new RubySSLSocketFactory(), x509TrustManager)
					.hostnameVerifier((s, sslSession) -> true)
					.dns(new RubyHttpDns());
		}
		return new Retrofit.Builder()
				.baseUrl("https://oauth.secure.pixiv.net")
				.client(okHttpClientAuthBuilder.build())
				.addConverterFactory(MoshiConverterFactory.create())
				.build();
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
}
