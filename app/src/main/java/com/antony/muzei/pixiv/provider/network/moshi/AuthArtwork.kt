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
package com.antony.muzei.pixiv.provider.network.moshi

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthArtwork(
    val caption: String,
    val create_date: String,
    val height: Int,
    val id: Int,
    val image_urls: Image_Urls,
    val is_bookmarked: Boolean,
    val is_muted: Boolean,
    val meta_pages: List<Image_Urls>,
    val meta_single_page: Meta_Single_Page,
    val page_count: Int,
    val restrict: Int,
    val sanity_level: Int,
    val tags: List<Tags>,
    val title: String,
    val tools: List<String>,
    val total_bookmarks: Int,
    val total_view: Int,
    val type: String,
    val user: User,
    val visible: Boolean,
    val width: Int,
    val x_restrict: Int
)

@JsonClass(generateAdapter = true)
data class Image_Urls(
    val large: String?,
    val medium: String?,
    val original: String?,
    val square_medium: String?,
)

@JsonClass(generateAdapter = true)
data class Meta_Single_Page(val original_image_url: String?)

@JsonClass(generateAdapter = true)
data class Tags(val name: String)

@JsonClass(generateAdapter = true)
data class User(
    val account: String,
    val id: Int,
    val name: String
)
