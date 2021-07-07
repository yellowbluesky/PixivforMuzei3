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

package com.antony.muzei.pixiv.settings;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.antony.muzei.pixiv.settings.deleteArtwork.ArtworkDeletionFragment;
import com.antony.muzei.pixiv.settings.fragments.AdvOptionsPreferenceFragment;
import com.antony.muzei.pixiv.settings.fragments.CreditsPreferenceFragment;
import com.antony.muzei.pixiv.settings.fragments.MainPreferenceFragment;
import com.antony.muzei.pixiv.settings.fragments.RoadmapPreferenceFragment;

import org.jetbrains.annotations.NotNull;

public class SectionsPagerAdapter extends FragmentStateAdapter {
    public SectionsPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @NotNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new MainPreferenceFragment();
            case 1:
                return new AdvOptionsPreferenceFragment();
            case 2:
                return new ArtworkDeletionFragment();
            case 3:
            default:
                return new CreditsPreferenceFragment();
            case 4:
                return new RoadmapPreferenceFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}
