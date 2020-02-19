package com.antony.muzei.pixiv;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import static com.antony.muzei.pixiv.PixivArtProviderDefines.HASH_SECRET;

class PixivArtService
{
	private static final String LOG_TAG = "PIXIV_DEBUG";
	private static OkHttpClient httpClient = new OkHttpClient();

	static
	{
		Log.d(LOG_TAG, "locale is : " + Locale.getDefault().getISO3Language());
		/* SNI Bypass begin */
		if (Locale.getDefault().getISO3Language().equals("zho"))
		{
			Log.d(LOG_TAG, "Bypass in effect");
			HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(
					s -> Log.v("aaa", "message====" + s));

			httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
			OkHttpClient.Builder builder = new OkHttpClient.Builder();

			builder.sslSocketFactory(new RubySSLSocketFactory(), new X509TrustManager()
			{
				@Override
				public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
				{

				}

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
			builder.hostnameVerifier(new HostnameVerifier()
			{
				@Override
				public boolean verify(String s, SSLSession sslSession)
				{
					return true;
				}
			});//disable hostnameVerifier
			builder.addInterceptor(httpLoggingInterceptor);
			builder.dns(new RubyHttpDns());//define the direct ip address
			httpClient = builder.build();
			/* SNI Bypass end */
		}
	}

	static String getAccesToken(SharedPreferences sharedPrefs)
	{
		String accessToken = sharedPrefs.getString("accessToken", "");
		long accessTokenIssueTime = sharedPrefs.getLong("accessTokenIssueTime", 0);
		if (!accessToken.isEmpty() && accessTokenIssueTime > (System.currentTimeMillis() / 1000) - 3600)
		{
			Log.i(LOG_TAG, "Existing access token found, using it");
			Log.d(LOG_TAG, "getAccessToken(): Exited");
			return accessToken;
		}
		Log.i(LOG_TAG, "Access token expired or non-existent, proceeding to acquire a new access token");

		try
		{
			MultipartBody.Builder authData = new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("get_secure_url", "1")
					.addFormDataPart("client_id", PixivArtProviderDefines.CLIENT_ID)
					.addFormDataPart("client_secret", PixivArtProviderDefines.CLIENT_SECRET);

			Response response;
			if (sharedPrefs.getString("refreshToken", "").isEmpty())
			{
				Log.i(LOG_TAG, "Using username and password to acquire an access token");
				authData.addFormDataPart("grant_type", "password")
						.addFormDataPart("username", sharedPrefs.getString("pref_loginId", ""))
						.addFormDataPart("password", sharedPrefs.getString("pref_loginPassword", ""));
			} else
			{
				Log.i(LOG_TAG, "Using refresh token to acquire an access token");
				authData.addFormDataPart("grant_type", "refresh_token")
						.addFormDataPart("refresh_token", sharedPrefs.getString("refreshToken", ""));
			}
			response = sendPostRequest(authData.build());
			JSONObject authResponseBody = new JSONObject(response.body().string());
			response.close();

			if (authResponseBody.has("has_error"))
			{
				Log.i(LOG_TAG, "Error authenticating, check username or password");
				// Clearing loginPassword is a hacky way to alerting to the user that their credentials do not work
				Log.v(LOG_TAG, authResponseBody.toString());
				sharedPrefs.edit().putString("pref_loginPassword", "").apply();
				return "";
			}

			// Authentication succeeded, storing tokens returned from Pixiv
			//Log.d(LOG_TAG, authResponseBody.toString());
//            Uri profileImageUri = storeProfileImage(authResponseBody.getJSONObject("response"));
//            sharedPrefs.edit().putString("profileImageUri", profileImageUri.toString()).apply();
			PixivArtWorker.storeTokens(sharedPrefs, authResponseBody.getJSONObject("response"));
		} catch (IOException | JSONException ex)
		{
			ex.printStackTrace();
			Log.d(LOG_TAG, "getAccessToken(): Exited with error");
			return "";
		}
		Log.d(LOG_TAG, "Acquired access token");
		Log.d(LOG_TAG, "getAccessToken(): Exited");
		return sharedPrefs.getString("accessToken", "");

	}

	// This function is used for modes that require authentication
	// Feed, bookmark, tag_search, or artist
	// Returns a Response containing a JSON within its body
	static Response sendGetRequestAuth(HttpUrl url, String accessToken) throws IOException
	{
		Request request = new Request.Builder()
				.addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
				.addHeader("App-OS", PixivArtProviderDefines.APP_OS)
				.addHeader("App-OS-Version", PixivArtProviderDefines.APP_OS_VERSION)
				.addHeader("App-Version", PixivArtProviderDefines.APP_VERSION)
				.addHeader("Authorization", "Bearer " + accessToken)
				.addHeader("Accept-Language", "en-us")
				.get()
				.url(url)
				.build();
		return httpClient.newCall(request).execute();
	}

	// This function used by modes that do not require authentication (ranking) to acquire the JSON
	// Used by all modes to download the actual image
	// Can either return either a:
	//      Response containing a JSON within its body
	//      An image to be downloaded
	// Depending on callee function
	static Response sendGetRequestRanking(HttpUrl url) throws IOException
	{
		Request request = new Request.Builder()
				.addHeader("User-Agent", PixivArtProviderDefines.BROWSER_USER_AGENT)
				.addHeader("Referer", PixivArtProviderDefines.PIXIV_HOST)
				.addHeader("Accept-Language", "en-us")
				.get()
				.url(url)
				.build();

		return httpClient.newCall(request).execute();
	}

	// Returns an access token, provided credentials are correct
	private static Response sendPostRequest(RequestBody authQuery) throws IOException
	{
		// Pixiv API update requires this to prevent replay attacks
		String rfc3339Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
		String concatSecret = rfc3339Date + HASH_SECRET;
		String hashedSecret = "";
		try
		{
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(concatSecret.getBytes());
			byte[] messageDigest = digest.digest();
			StringBuilder hexString = new StringBuilder();
			// this loop is horrifically inefficient on CPU and memory
			// but is only executed once to acquire a new access token
			// i.e. at most once per hour for normal use case
			for (byte aMessageDigest : messageDigest)
			{
				String h = Integer.toHexString(0xFF & aMessageDigest);
				while (h.length() < 2)
				{
					h = "0" + h;
				}
				hexString.append(h);
			}
			hashedSecret = hexString.toString();
		} catch (java.security.NoSuchAlgorithmException ex)
		{
			ex.printStackTrace();
		}

		Request request = new Request.Builder()
				.addHeader("Content-Type", "application/x-www-form-urlencoded")
				.addHeader("User-Agent", PixivArtProviderDefines.APP_USER_AGENT)
				.addHeader("x-client-time", rfc3339Date)
				.addHeader("x-client-hash", hashedSecret)
				.post(authQuery)
				.url(PixivArtProviderDefines.OAUTH_URL)
				.build();
		return httpClient.newCall(request).execute();
	}

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
