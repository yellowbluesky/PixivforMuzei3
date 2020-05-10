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

package com.antony.muzei.pixiv.network;

import com.antony.muzei.pixiv.gson.Illusts;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface AuthJsonService
{
	// TODO access token is set as dynamic header
	// TODO see if the access token can be set on the retrofit client instance
	@GET("/v2/illust/follow?restrict=public")
	Call<Illusts> getFollowJson(@Header("Authorization") String accessToken);

	@GET("v1/user/bookmarks/illust?restrict=public")
	Call<Illusts> getBookmarkJson(@Header("Authorization") String accessToken, @Query("user_id") String userId);

	@GET("v1/search/illust?search_target=partial_match_for_tags&sort=date_desc&filter=for_ios")
	Call<Illusts> getTagSearchJson(@Header("Authorization") String accessToken, @Query("word") String tag);

	@GET("v1/user/illust?filter=for_ios")
	Call<Illusts> getArtistJson(@Header("Authorization") String accessToken, @Query("user_id") String userId);

	@GET("v1/illust/recommended?content_type=illust&include_ranking_label=true&include_ranking_illusts=true&filter=for_ios")
	Call<Illusts> getRecommendedJson(@Header("Authorization") String accessToken);
}
