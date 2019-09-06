package com.antony.muzei.pixiv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;
/**
 * @Author: Perol_Notsfsssf
 */
public class RubyHttpDns implements Dns {
    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> list = new ArrayList<>();
        if (!hostname.contains("i.pximg")){

            list.add(InetAddress.getByName("210.140.131.222"));
            list.add(InetAddress.getByName("210.140.131.219"));
        }
        else {
            list.add(InetAddress.getByName("210.140.92.141"));
            list.add(InetAddress.getByName("210.140.92.138"));
            list.add(InetAddress.getByName("210.140.92.139"));
        }

        return list;
    }
}
