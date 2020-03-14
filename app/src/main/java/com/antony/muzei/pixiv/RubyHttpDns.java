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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

/**
 * @Author: Perol_Notsfsssf
 */
public class RubyHttpDns implements Dns
{
	@Override
	public List<InetAddress> lookup(String hostname) throws UnknownHostException
	{
		List<InetAddress> list = new ArrayList<>();
		if (!hostname.contains("i.pximg"))
		{

			list.add(InetAddress.getByName("210.140.131.222"));
			list.add(InetAddress.getByName("210.140.131.219"));
		} else
		{
			list.add(InetAddress.getByName("210.140.92.141"));
			list.add(InetAddress.getByName("210.140.92.138"));
			list.add(InetAddress.getByName("210.140.92.139"));
		}

		return list;
	}
}
