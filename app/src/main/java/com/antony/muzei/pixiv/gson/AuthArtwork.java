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

public class AuthArtwork
{
	private int id;
	private String title;
	private String type;
	private Image_Urls image_urls;
	private String caption;
	private int restrict;
	private User user;
	private List<Tags> tags;
	private List<String> tools;
	private String create_date;
	private int page_count;
	private int width;
	private int height;
	private int sanity_level;
	private int x_restrict;
	//private String series;
	private Meta_Single_Page meta_single_page;
	private List<Meta_Pages> meta_pages;
	private int total_view;
	private int total_bookmarks;
	private boolean is_bookmarked;
	private boolean visible;
	private boolean is_muted;

	public int getId()
	{
		return id;
	}

	public String getTitle()
	{
		return title;
	}

	public String getType()
	{
		return type;
	}

	public Image_Urls getImage_urls()
	{
		return image_urls;
	}

	public String getCaption()
	{
		return caption;
	}

	public int getRestrict()
	{
		return restrict;
	}

	public User getUser()
	{
		return user;
	}

	public List<Tags> getTags()
	{
		return tags;
	}

	public List<String> getTools()
	{
		return tools;
	}

	public String getCreate_date()
	{
		return create_date;
	}

	public int getPage_count()
	{
		return page_count;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	public int getSanity_Level()
	{
		return sanity_level;
	}

	public int getX_restrict()
	{
		return x_restrict;
	}

	public Meta_Single_Page getMeta_single_page()
	{
		return meta_single_page;
	}

	public List<Meta_Pages> getMeta_pages()
	{
		return meta_pages;
	}

	public int getTotal_view()
	{
		return total_view;
	}

	public int getTotal_bookmarks()
	{
		return total_bookmarks;
	}

	public boolean isIs_bookmarked()
	{
		return is_bookmarked;
	}

	public boolean isVisible()
	{
		return visible;
	}

	public boolean isIs_muted()
	{
		return is_muted;
	}

	public static class Image_Urls
	{
		private String square_medium;
		private String medium;
		private String large;
		private String original;

		public String getSquare_medium()
		{
			return square_medium;
		}

		public String getMedium()
		{
			return medium;
		}

		public String getLarge()
		{
			return large;
		}

		public String getOriginal()
		{
			return original;
		}
	}

	public static class User
	{
		private int id;
		private String name;
		private String account;

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public String getAccount()
		{
			return account;
		}

		public static class profile_image_urls
		{
			private String medium;

			public String getMedium()
			{
				return medium;
			}
		}
	}

	public static class Tags
	{
		private String name;
		private String translated_name;

		public String getName()
		{
			return name;
		}

		public String getTranslated_name()
		{
			return translated_name;
		}
	}

	public static class Meta_Single_Page
	{
		private String original_image_url;

		public String getOriginal_image_url()
		{
			return original_image_url;
		}
	}

	public static class Meta_Pages
	{
		private Image_Urls image_urls;

		public Image_Urls getImage_urls()
		{
			return image_urls;
		}
	}
}
