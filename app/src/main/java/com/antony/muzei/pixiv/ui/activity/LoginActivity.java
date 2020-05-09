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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.antony.muzei.pixiv.R;

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
				if (!((EditText) findViewById(R.id.loginUsername)).getText().toString().isEmpty() && !s.toString().isEmpty())
				{
					findViewById(R.id.loginButton).setEnabled(true);
				}
			}
		});

		findViewById(R.id.loginButton).setOnClickListener(v -> executeLogin());
		// TODO enable the button when both fields have valid inputs
	}

	private void executeLogin()
	{
		Log.v("DEMO", "attempting to login");
	}
}
