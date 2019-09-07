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
