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

import java.util.List;

public class RankingArtwork
{
	private String title;
	private String date;
	private List<String> tags;
	private String url;
	private int illust_type;
	private int illust_book_style;
	private int illust_page_count;
	private String user_name;
	private String profile_img;
	private Illust_Content_Type illust_content_type;
	//private boolean illust_series;
	private int illust_id;
	private int width;
	private int height;
	private int user_id;
	private int rank;
	private int yes_rank;
	private int rating_count;
	private int view_count;
	private int illust_upload_timestamp;
	private String attr;

	public String getTitle()
	{
		return title;
	}

	public String getDate()
	{
		return date;
	}

	public List<String> getTags()
	{
		return tags;
	}

	public String getUrl()
	{
		return url;
	}

	public int getIllust_type()
	{
		return illust_type;
	}

	public int getIllust_book_style()
	{
		return illust_book_style;
	}

	public int getIllust_page_count()
	{
		return illust_page_count;
	}

	public String getUser_name()
	{
		return user_name;
	}

	public String getProfile_img()
	{
		return profile_img;
	}

	public Illust_Content_Type getIllust_content_type()
	{
		return illust_content_type;
	}

//	public boolean isIllust_series()
//	{
//		return illust_series;
//	}

	public int getIllust_id()
	{
		return illust_id;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	public int getUser_id()
	{
		return user_id;
	}

	public int getRank()
	{
		return rank;
	}

	public int getYes_rank()
	{
		return yes_rank;
	}

	public int getRating_count()
	{
		return rating_count;
	}

	public int getView_count()
	{
		return view_count;
	}

	public int getIllust_upload_timestamp()
	{
		return illust_upload_timestamp;
	}

	public String getAttr()
	{
		return attr;
	}
}
