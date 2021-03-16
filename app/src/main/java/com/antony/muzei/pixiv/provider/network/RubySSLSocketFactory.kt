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
package com.antony.muzei.pixiv.provider.network

import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.jvm.internal.Intrinsics

/**
 * @Author: Perol_Notsfsssf
 */
// With contributions by CeuiLiSA
class RubySSLSocketFactory : SSLSocketFactory() {
    @Throws(IOException::class)
    override fun createSocket(paramSocket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val inetAddress = paramSocket!!.inetAddress
        Log.d("ANTONY_SSL", "Connecting with " + inetAddress.hostAddress)
        if (autoClose) {
            paramSocket.close()
        }
        val sslSocket = (getDefault().createSocket(inetAddress, port) as SSLSocket).apply { enabledProtocols = supportedProtocols }
        val sslSession = sslSocket.session

        Log.i("ANTONY_SSL", "Setting SNI hostname")
        val stringBuilder = StringBuilder()
        stringBuilder.append("Established ")
        Intrinsics.checkExpressionValueIsNotNull(sslSession, "session")
        stringBuilder.append(sslSession.protocol)
        stringBuilder.append(" connection with ")
        stringBuilder.append(sslSession.peerHost)
        stringBuilder.append(" using ")
        stringBuilder.append(sslSession.cipherSuite)
        Log.d("ANTONY_SSL", stringBuilder.toString())
        return sslSocket

    }

    override fun createSocket(paramString: String?, paramInt: Int): Socket? = null

    override fun createSocket(paramString: String?, paramInt1: Int, paramInetAddress: InetAddress?, paramInt2: Int): Socket? = null

    override fun createSocket(paramInetAddress: InetAddress?, paramInt: Int): Socket? = null

    override fun createSocket(paramInetAddress1: InetAddress?, paramInt1: Int, paramInetAddress2: InetAddress?, paramInt2: Int): Socket? = null

    override fun getDefaultCipherSuites(): Array<String> {
        return arrayOf()
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return arrayOf()
    }
}
