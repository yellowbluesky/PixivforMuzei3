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

package com.antony.muzei.pixiv.ui;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AutoFitGridLayoutManager extends GridLayoutManager
{
	private int columnWidth;
	private boolean columnWidthChanged = true;

	public AutoFitGridLayoutManager(Context context, int columnWidth)
	{
		super(context, 1);

		setColumnWidth(columnWidth);
	}

	public void setColumnWidth(int newColumnWidth)
	{
		if (newColumnWidth > 0 && newColumnWidth != columnWidth)
		{
			columnWidth = newColumnWidth;
			columnWidthChanged = true;
		}
	}

	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state)
	{
		if (columnWidthChanged && columnWidth > 0)
		{
			int totalSpace;
			if (getOrientation() == VERTICAL)
			{
				totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
			} else
			{
				totalSpace = getHeight() - getPaddingTop() - getPaddingBottom();
			}
			int spanCount = Math.max(1, totalSpace / columnWidth);
			setSpanCount(spanCount);
			columnWidthChanged = false;
		}
		super.onLayoutChildren(recycler, state);
	}
}
