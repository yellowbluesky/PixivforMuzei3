package com.antony.muzei.pixiv.provider.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class FallbackDns(private vararg val dns: Dns) : Dns {
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val results = ArrayList<InetAddress>(5)
        val failures = arrayListOf<UnknownHostException>()

        dns.forEach {
            try {
                results += it.lookup(hostname)
                if (results.isNotEmpty()) return results
            } catch (e: UnknownHostException) {
                failures += e
            }
        }

        // Throw all exception
        val exception = UnknownHostException(hostname)
        if (failures.isNotEmpty()) {
            exception.initCause(failures[0])
            for (i in 1 until failures.size) {
                exception.addSuppressed(failures[i])
            }
        }

        throw exception
    }
}
