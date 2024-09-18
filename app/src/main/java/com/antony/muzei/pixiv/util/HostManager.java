package com.antony.muzei.pixiv.util;


import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.PixivMuzei;

import java.net.InetAddress;
import java.util.List;
import java.util.Random;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import retrofit2.Call;
import retrofit2.Callback;

/*
This class is called everytime an image is downloaded
 */
public class HostManager {

    public static final String HOST_OLD = "i.pximg.net";
    //    public static final String HOST_OLD = "app-api.pixiv.net";
    public static final String HOST_NEW = "i.pixiv.cat";
    private static final String HTTP_HEAD = "http://";

    public static HostManager get() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final HostManager INSTANCE = new HostManager();
    }

    public String replaceUrl(String before) {
        // See https://pixiv.cat/reverseproxy.html
        // Its ISP is Cloudflare
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PixivMuzei.Companion.getContext().getApplicationContext());

        boolean usePixivCatProxy = false;
        String pixivProxyHost = "";

        if(PixivMuzei.Companion.getContext() != null){
            usePixivCatProxy = prefs.getBoolean("pref_usePixivCat",false);
        }

        if (usePixivCatProxy) {
            pixivProxyHost = prefs.getString("pref_pixivProxyHost", HOST_NEW);
            return before.replace(HOST_OLD, pixivProxyHost);
        } else {
            return resizeUrl(before);
        }
    }

    private String resizeUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            return HTTP_HEAD + HOST_OLD + uri.getPath();
        } catch (Exception e) {
            e.printStackTrace();
            return HTTP_HEAD + HOST_OLD + url.substring(19);
        }
    }

    public static int flatRandom(int left, int right) {
        Random r = new Random();
        return r.nextInt(right - left) + left;
    }

    public static int flatRandom(int right) {
        return flatRandom(0, right);
    }
}
