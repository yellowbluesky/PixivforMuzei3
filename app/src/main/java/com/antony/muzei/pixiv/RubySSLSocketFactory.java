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

package com.antony.muzei.pixiv;

import android.net.SSLCertificateSocketFactory;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * @Author: Perol_Notsfsssf
 */
public class RubySSLSocketFactory extends SSLSocketFactory
{
	@Override
	public String[] getDefaultCipherSuites()
	{
		return new String[0];
	}

	@Override
	public String[] getSupportedCipherSuites()
	{
		return new String[0];
	}

	@Override
	public Socket createSocket(Socket plainSocket, String host, int port, boolean autoClose) throws IOException
	{
		InetAddress address = plainSocket.getInetAddress();
		Log.i("!", "Address: " + address.getHostAddress());
		if (autoClose)
		{
			plainSocket.close();
		}
		SSLCertificateSocketFactory sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0);
		SSLSocket ssl = (SSLSocket) sslSocketFactory.createSocket(address, port);
		ssl.setEnabledProtocols(ssl.getSupportedProtocols());
		SSLSession session = ssl.getSession();
		Log.i("!", "Protocol " + session.getProtocol() + " PeerHost " + session.getPeerHost() +
				" CipherSuite " + session.getCipherSuite());
		return ssl;
	}//disable sni

	@Override
	public Socket createSocket(String s, int i)
	{
		return null;
	}

	@Override
	public Socket createSocket(String s, int i, InetAddress inetAddress, int i1)
	{
		return null;
	}

	@Override
	public Socket createSocket(InetAddress inetAddress, int i)
	{
		return null;
	}

	@Override
	public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1)
	{
		return null;
	}
}
