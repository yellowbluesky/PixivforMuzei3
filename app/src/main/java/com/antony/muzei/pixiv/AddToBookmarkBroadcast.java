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

package com.antony.muzei.pixiv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.exceptions.AccessTokenAcquisitionException;
import com.antony.muzei.pixiv.moshi.OauthResponse;
import com.antony.muzei.pixiv.network.OAuthResponseService;
import com.antony.muzei.pixiv.network.RestClient;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

class AddToBookmarkBroadcast extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.v("BOOKMARK", "bookmark broadcast");
		String artworkId = intent.getStringExtra("artworkId");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		String accessToken;
		try
		{
			accessToken = PixivArtService.refreshAccessToken(sharedPrefs);
		} catch (AccessTokenAcquisitionException e)
		{
			return;
		}

		Map<String, String> fieldParams = new HashMap<>();
		fieldParams.put("illust_id", artworkId);
		fieldParams.put("restrict", "public");

		OAuthResponseService service = RestClient.getRetrofitBookmarkInstance(false).create(OAuthResponseService.class);
		Call<OauthResponse> call = service.postArtworkBookmark("Bearer " + accessToken, fieldParams);

		call.enqueue(new Callback<OauthResponse>()
		{
			@Override
			public void onResponse(Call<OauthResponse> call, Response<OauthResponse> response)
			{

			}

			@Override
			public void onFailure(Call<OauthResponse> call, Throwable t)
			{

			}
		});
	}
}