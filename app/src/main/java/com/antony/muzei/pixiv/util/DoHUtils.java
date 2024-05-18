package com.antony.muzei.pixiv.util;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;


public class DoHUtils {
    private static final String DOH_URL = "https://1.0.0.1/dns-query";

    public static Dns createDohDnsClient() {
        return new DnsOverHttps.Builder().client(new OkHttpClient())
                .url(HttpUrl.get(DOH_URL))
                .build();
    }
}
