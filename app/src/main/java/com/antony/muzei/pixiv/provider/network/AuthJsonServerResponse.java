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

package com.antony.muzei.pixiv.provider.network;

import com.antony.muzei.pixiv.provider.network.moshi.Illusts;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface AuthJsonServerResponse {

    @GET("/v2/illust/follow?restrict=public")
    Call<Illusts> getFollowJson();

    @GET("v1/user/bookmarks/illust?restrict=public")
    Call<Illusts> getBookmarkJson(@Query("user_id") String userId);

    @GET("v1/search/illust?search_target=partial_match_for_tags&sort=date_desc&filter=for_ios")
    Call<Illusts> getTagSearchJson(@Query("word") String tag);

    @GET("v1/user/illusts?filter=for_ios")
    Call<Illusts> getArtistJson(@Query("user_id") String artist_id);

    // filter=for_ios here, !?(･_･;?
    @GET("v1/illust/recommended?content_type=illust&include_ranking_label=true&include_ranking_illusts=true&filter=for_ios")
    Call<Illusts> getRecommendedJson();

    @GET
    Call<Illusts> getNextUrl(@Url String url);

}
