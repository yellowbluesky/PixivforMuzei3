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

public class PixivArtProviderDefines
{
	public static final String[] AUTH_MODES = {"follow", "bookmark", "tag_search", "artist", "recommended"};
	static final String[] RANKING_MODES = {"daily", "weekly", "monthly", "rookie", "original", "male", "female"};
	// browser strings
	static final String BROWSER_USER_AGENT =
			"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0";

	// app strings
	static final String APP_OS = "ios";
	static final String APP_OS_VERSION = "12.2";
	static final String APP_VERSION = "7.6.2";
	static final String APP_USER_AGENT =
			"PixivIOSApp/7.6.2 (iOS 12.2; iPhone9,1)";
	static final String CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT";
	static final String CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj";
	static final String HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c";

	// urls
	static final String PIXIV_HOST = "https://www.pixiv.net";
	static final String MEMBER_ILLUST_URL =
			PIXIV_HOST + "/member_illust.php?mode=medium&illust_id=";
	static final String OAUTH_URL =
			"https://oauth.secure.pixiv.net/auth/token";
}