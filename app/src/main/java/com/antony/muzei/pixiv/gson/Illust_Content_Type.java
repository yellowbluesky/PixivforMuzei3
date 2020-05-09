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

package com.antony.muzei.pixiv.gson;

public class Illust_Content_Type
{
	private int sexual;
	private boolean lo;
	private boolean grotesque;
	private boolean violent;
	private boolean homosexual;
	private boolean drug;
	private boolean thoughts;
	private boolean antisocial;
	private boolean religion;
	private boolean original;
	private boolean furry;
	private boolean bl;
	private boolean yuri;

	public int getSexual()
	{
		return sexual;
	}

	public boolean isLo()
	{
		return lo;
	}

	public boolean isGrotesque()
	{
		return grotesque;
	}

	public boolean isViolent()
	{
		return violent;
	}

	public boolean isHomosexual()
	{
		return homosexual;
	}

	public boolean isDrug()
	{
		return drug;
	}

	public boolean isThoughts()
	{
		return thoughts;
	}

	public boolean isAntisocial()
	{
		return antisocial;
	}

	public boolean isReligion()
	{
		return religion;
	}

	public boolean isOriginal()
	{
		return original;
	}

	public boolean isFurry()
	{
		return furry;
	}

	public boolean isBl()
	{
		return bl;
	}

	public boolean isYuri()
	{
		return yuri;
	}
}
