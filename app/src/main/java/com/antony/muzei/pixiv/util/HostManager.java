package com.antony.muzei.pixiv.util;


import android.net.Uri;
import android.text.TextUtils;

import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;

public class HostManager {

    public static final String HOST_OLD = "i.pximg.net";
    //    public static final String HOST_OLD = "app-api.pixiv.net";
    public static final String HOST_NEW = "i.pixiv.cat";
    private static final String HTTP_HEAD = "http://";

    private String host;

    private HostManager() {
    }

    public static HostManager get() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final HostManager INSTANCE = new HostManager();
    }

    public void init() {
        host = randomHost();
        updateHost();
    }

    private String randomHost() {
        String[] already = new String[]{
                "210.140.92.145",
                "210.140.92.141",
                "210.140.92.138",
                "210.140.92.143",
                "210.140.92.146",
                "210.140.92.142",
                "210.140.92.147",
                "210.140.92.139",
                "210.140.92.140",
                "210.140.92.144"
        };
        return already[flatRandom(already.length)];
    }

    private void updateHost() {
        CloudFlareDNSService.Companion.invoke().query(HOST_OLD, "application/dns-json", "A")
                .enqueue(new Callback<CloudFlareDNSResponse>() {
                    @Override
                    public void onResponse(Call<CloudFlareDNSResponse> call, retrofit2.Response<CloudFlareDNSResponse> response) {
                        try {
                            CloudFlareDNSResponse cloudFlareDNSResponse = response.body();
                            if (cloudFlareDNSResponse != null) {
                                if (cloudFlareDNSResponse.getAnswer() != null && cloudFlareDNSResponse.getAnswer().size() != 0) {
                                    int position = flatRandom(cloudFlareDNSResponse.getAnswer().size());
                                    host = cloudFlareDNSResponse.getAnswer().get(position).getData();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(Call<CloudFlareDNSResponse> call, Throwable t) {
                        t.printStackTrace();
                    }
                });
    }

    public String replaceUrl(String before) {
        boolean usePixivCatProxy = false;
        if (usePixivCatProxy) {
            return before.replace(HOST_OLD, HOST_NEW);
        } else {
            return resizeUrl(before);
        }
    }

    private String resizeUrl(String url) {
        if (TextUtils.isEmpty(host)) {
            host = randomHost();
        }
        try {
            Uri uri = Uri.parse(url);
            return HTTP_HEAD + host + uri.getPath();
        } catch (Exception e) {
            e.printStackTrace();
            return HTTP_HEAD + host + url.substring(19);
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
