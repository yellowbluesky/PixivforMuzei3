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
data class RankingArtwork(
    val attr: String,
    val date: String,
    val height: Int,
    val illust_book_style: Int,
    val illust_content_type: Illust_Content_Type,
    val illust_id: Int,
    val illust_page_count: Int,
    val illust_type: Int,
    val illust_upload_timestamp: Int,
    val profile_img: String,
    val rank: Int,
    val rating_count: Int,
    val taks: List<String>,
    val title: String,
    val url: String,
    val user_id: Int,
    val user_name: String,
    val view_count: Int,
    val width: Int,
    val yes_rank: Int
)

@JsonClass(generateAdapter = true)
data class Illust_Content_Type(
    val antisocial: Boolean,
    val bl: Boolean,
    val drug: Boolean,
    val furry: Boolean,
    val grotesque: Boolean,
    val homosexual: Boolean,
    val lo: Boolean,
    val original: Boolean,
    val religion: Boolean,
    val sexual: Int,
    val thoughts: Boolean,
    val violent: Boolean,
    val yuri: Boolean,
)
