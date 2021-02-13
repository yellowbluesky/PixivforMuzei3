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

package com.antony.muzei.pixiv.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.preference.PreferenceManager;

import com.antony.muzei.pixiv.BuildConfig;
import com.antony.muzei.pixiv.PixivInstrumentation;
import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.common.PixivMuzeiActivity;
import com.antony.muzei.pixiv.common.text.SimpleTextWatcher;
import com.antony.muzei.pixiv.databinding.LoginPixivSignInActivityBinding;
import com.antony.muzei.pixiv.provider.network.RestClient;
import com.antony.muzei.pixiv.util.TextViewKt;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends PixivMuzeiActivity {

    private LoginPixivSignInActivityBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = LoginPixivSignInActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.editUid.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@Nullable Editable s) {
                checkLoginEnable();
            }
        });
        mBinding.editPwd.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@Nullable Editable s) {
                checkLoginEnable();
            }
        });
        mBinding.btnLogin.setOnClickListener(v -> loginPassword());

        mBinding.btnLoginRefresh.setOnClickListener(v -> loginRefresh());
    }

    @UiThread
    private void checkLoginEnable() {
        boolean accountTyped = !TextUtils.isEmpty(mBinding.editUid.getText());
        boolean passwordTyped = !TextUtils.isEmpty(mBinding.editPwd.getText());
        mBinding.btnLogin.setEnabled(accountTyped && passwordTyped);
    }

    private void loginRefresh() {
        // Builds the header fields for the OAuth POST request
        Map<String, String> fieldParams = new HashMap<>();
        fieldParams.put("get_secure_url", "1");
        fieldParams.put("client_id", BuildConfig.PIXIV_CLIENT_ID);
        fieldParams.put("client_secret", BuildConfig.PIXIV_CLIENT_SEC);

        // When a new user is logging in, they cannot be an existing valid refresh token
        String refreshToken = TextViewKt.text(mBinding.editRefresh, true).toString();
        fieldParams.put("grant_type", "refresh_token");
        fieldParams.put("refresh_token", refreshToken);

        executeLogin(fieldParams);
    }

    private void loginPassword() {
        // Builds the header fields for the OAuth POST request
        Map<String, String> fieldParams = new HashMap<>();
        fieldParams.put("get_secure_url", "1");
        fieldParams.put("client_id", BuildConfig.PIXIV_CLIENT_ID);
        fieldParams.put("client_secret", BuildConfig.PIXIV_CLIENT_SEC);

        // When a new user is logging in, they cannot be an existing valid refresh token
        String account = TextViewKt.text(mBinding.editUid, true).toString();
        String password = TextViewKt.text(mBinding.editPwd, true).toString();
        fieldParams.put("grant_type", "password");
        fieldParams.put("username", account);
        fieldParams.put("password", password);

        executeLogin(fieldParams);
    }

    private void executeLogin(Map<String, String> fieldParams) {
        // Enables the indeterminate progress bar (spinning circle)
        final ProgressBar progressBar = mBinding.loginLoading;
        progressBar.setVisibility(View.VISIBLE);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        boolean bypassActive = sharedPreferences.getBoolean("pref_enableNetworkBypass", false);

        OAuthResponseService service = RestClient.getRetrofitOauthInstance(bypassActive).create(OAuthResponseService.class);
        Call<OauthResponse> call = service.postRefreshToken(fieldParams);
        // Callback because we are on a main UI thread, Android will throw a NetworkOnMainThreadException
        call.enqueue(new Callback<OauthResponse>() {
            @Override
            public void onResponse(@NonNull Call<OauthResponse> call, @NonNull Response<OauthResponse> response) {
                if (response.isSuccessful()) {
                    // Store the recently received tokens
                    OauthResponse body = response.body();
                    OauthResponse.PixivOauthResponse pixivAuthResp = body != null ? body.getPixivOauthResponse() : null;
                    if (pixivAuthResp != null) {
                        PixivInstrumentation.updateTokenLocal(LoginActivity.this, pixivAuthResp);
                    }
                    // Returns the username for immediate consumption by MainPreferenceFragment
                    // Sets the "Logged in as XXX" preference summary
                    Intent username = new Intent()
                            .putExtra("username", response.body().getPixivOauthResponse().getUser().getName());
                    setResult(RESULT_OK, username);
                    finish();
                } else {
                    progressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_authFailed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<OauthResponse> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(), getString(R.string.toast_authFailed), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
