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
data class OAuth(
    val response: OAuthResponse,
)

@JsonClass(generateAdapter = true)
data class OAuthResponse(
    val access_token: String,
    val expires_in: Int,
    val token_type: String,
    val scope: String,
    val refresh_token: String,
    val user: OAuthUser,
)

@JsonClass(generateAdapter = true)
data class OAuthUser(
    val profile_image_urls: OAuthUserProfileImageUrls,
    val id: String,
    val name: String,
    val mail_address: String,
    val is_premium: Boolean,
    val x_restrict: Int,
    val is_mail_authorized: Boolean,
)

@JsonClass(generateAdapter = true)
data class OAuthUserProfileImageUrls(
    val px_16x16: String,
    val px_50x50: String,
    val px_170x170: String
)
