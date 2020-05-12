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

package com.antony.muzei.pixiv.moshi;

import com.squareup.moshi.Json;

import java.util.List;

public class Illusts
{
	@Json(name = "illusts")
	private List<AuthArtwork> artworks;
	private String next_url;

	public List<AuthArtwork> getArtworks()
	{
		return artworks;
	}

	public String getNext_url()
	{
		return next_url;
	}
}
