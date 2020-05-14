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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.antony.muzei.pixiv.R;
import com.antony.muzei.pixiv.ui.fragments.SectionsPagerAdapter;
import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
		ViewPager viewPager = findViewById(R.id.view_pager);
		viewPager.setAdapter(sectionsPagerAdapter);
		TabLayout tabs = findViewById(R.id.tabs);
		tabs.setupWithViewPager(viewPager);

		// If Muzei is not installed, this will redirect the user to Muzei's Play Store listing
		if (!isMuzeiInstalled())
		{
			// TODO licalize these strings
			new AlertDialog.Builder(this)
					.setTitle("Muzei is not installed")
					.setMessage("Would you like to install Muzei?")

					// Specifying a listener allows you to take an action before dismissing the dialog.
					// The dialog is automatically dismissed when a dialog button is clicked.
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int which)
						{
							final String appPackageName = "net.nurik.roman.muzei"; // getPackageName() from Context or Activity object
							try
							{
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
							} catch (android.content.ActivityNotFoundException ex)
							{
								startActivity(new Intent(Intent.ACTION_VIEW,
										Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
							}
						}
					})

					// A null listener allows the button to dismiss the dialog and take no further action.
					.setNegativeButton(android.R.string.no, null)
					.show();
		}
//		FloatingActionButton fab = findViewById(R.id.fab);
//
//		fab.setOnClickListener(new View.OnClickListener()
//		{
//			@Override
//			public void onClick(View view)
//			{
//				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//						.setAction("Action", null).show();
//			}
//		});
	}

	// Redirects the user to Muzei's Play Store listing if it's not detected to be installed
	// TODO have a nicer dialog that explains why Muzei needs to be installed, instead of forcing them to the store
	private boolean isMuzeiInstalled()
	{
		boolean found = true;
		try
		{
			getApplicationContext().getPackageManager().getPackageInfo("net.nurik.roman.muzei", 0);
		} catch (PackageManager.NameNotFoundException ex)
		{
			found = false;
		}
		return found;
	}
}