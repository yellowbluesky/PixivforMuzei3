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

package com.antony.muzei.pixiv.ui.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.PixivArtWorker;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.gson.OauthResponse;
import com.antony.muzei.pixiv.network.OAuthResponseService;
import com.antony.muzei.pixiv.network.RestClient;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pixiv_sign_in);
		((EditText) findViewById(R.id.loginPassword)).addTextChangedListener(new TextWatcher()
		{
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count)
			{

			}

			@Override
			public void afterTextChanged(Editable s)
			{
				// this monster of a logical expression needs to be extracted out
				if (!((EditText) findViewById(R.id.loginUsername)).getText().toString().isEmpty() && !s.toString().isEmpty())
				{
					findViewById(R.id.loginButton).setEnabled(true);
				}
			}
		});

		findViewById(R.id.loginButton).setOnClickListener(v -> executeLogin());
	}

	private void executeLogin()
	{
		Map<String, String> fieldParams = new HashMap<>();
		fieldParams.put("get_secure_url", "1");
		fieldParams.put("client_id", "MOBrBDS8blbauoSck0ZfDbtuzpyT");
		fieldParams.put("client_secret", "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj");
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// If no refresh token present
		if (sharedPrefs.getString("refreshToken", "").isEmpty())
		{
			EditText usernameInput = findViewById(R.id.loginUsername);
			String username = usernameInput.getText().toString();

			EditText passwordInput = findViewById(R.id.loginPassword);
			String password = passwordInput.getText().toString();
			fieldParams.put("grant_type", "password");
			fieldParams.put("username", username);
			fieldParams.put("password", password);
		} else
		{
			fieldParams.put("grant_type", "refresh_token");
			fieldParams.put("refresh_token", sharedPrefs.getString("refreshToken", ""));
		}

		OAuthResponseService service = RestClient.getRetrofitOauthInstance().create(OAuthResponseService.class);
		Call<OauthResponse> call = service.postRefreshToken(fieldParams);
		call.enqueue(new Callback<OauthResponse>()
		{
			@Override
			public void onResponse(Call<OauthResponse> call, Response<OauthResponse> response)
			{
				if (response.isSuccessful())
				{
					PixivArtWorker.storeTokens(sharedPrefs, response.body());
					Intent username = new Intent()
							.putExtra("username", response.body().getPixivOauthResponse().getUser().getName());
					setResult(RESULT_OK, username);
					finish();

					// TODO store tokens
				}
			}

			@Override
			public void onFailure(Call<OauthResponse> call, Throwable t)
			{
				// TODO toast
			}
		});
	}
}
