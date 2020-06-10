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

import com.antony.muzei.pixiv.provider.network.moshi.Contents;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface RankingJsonServerResponse
{
	@GET("/ranking.php")
	Call<Contents> getRankingJson(@Query("mode") String mode);

	@GET("/ranking.php")
	Call<Contents> getRankingJson(@Query("mode") String mode, @Query("p") int page, @Query("date") String date);
}
