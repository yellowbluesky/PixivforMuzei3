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

public final class PixivProviderConst {

    public static final String PIXIV_RANKING_URL = "https://www.pixiv.net";
    public static final String PIXIV_API_HOST_URL = "https://app-api.pixiv.net/";
    public static final String PIXIV_ARTWORK_URL = "https://www.pixiv.net/en/artworks/";
    public static final String PIXIV_REDIRECT_URL = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback";
    public static final String PIXIV_IMAGE_URL = "https://i.pximg.net";
    public static final String MEMBER_ILLUST_URL = "https://www.pixiv.net/member_illust.php?mode=medium&illust_id=";
    public static final String OAUTH_URL = "https://oauth.secure.pixiv.net";

    // browser strings
    public static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0";

    public static final String APP_USER_AGENT = "PixivAndroidApp/6.96.0 (Android 11; Pixel 5)";

    public static final String PREFERENCE_PIXIV_ACCESS_TOKEN = "accessToken";
    public static final String PREFERENCE_PIXIV_REFRESH_TOKEN = "refreshToken";
    public static final String PREFERENCE_PIXIV_UPDATE_TOKEN_TIMESTAMP = "accessTokenIssueTime";
    public static final String PREFERENCE_OLDEST_MAX_BOOKMARK_ID = "oldestMaxBookmarkId";


    public static final String[] AUTH_MODES = {"follow", "bookmark", "tag_search", "artist", "recommended"};
    public static final String[] RANKING_MODES = {"daily", "weekly", "monthly", "rookie", "original", "male", "female"};

    public static final String SHARE_IMAGE_INTENT_CHOOSER_TITLE = "Share image using";
}
