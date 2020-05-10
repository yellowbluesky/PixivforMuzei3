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

package com.antony.muzei.pixiv.gson;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Contents
{
	@SerializedName("contents")
	private List<RankingArtwork> artworks;
	private String mode;
	private String content;
	private int page;
	private boolean prev;
	private int next;
	private String date;
	private boolean next_date;
	private int rank_total;

	public List<RankingArtwork> getArtworks()
	{
		return artworks;
	}

	public String getMode()
	{
		return mode;
	}

	public String getContent()
	{
		return content;
	}

	public int getPage()
	{
		return page;
	}

	public boolean isPrev()
	{
		return prev;
	}

	public int getNext()
	{
		return next;
	}

	public String getDate()
	{
		return date;
	}

	public boolean isNext_date()
	{
		return next_date;
	}

	public int getRank_total()
	{
		return rank_total;
	}
}
