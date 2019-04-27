package com.antony.muzei.pixiv;

public class PixivArtProviderDefines {
	// browser strings
	public static final String BROWSER_USER_AGENT =
			"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0";

	// app strings
	public static final String APP_OS = "ios";
	public static final String APP_OS_VERSION = "10.3.1";
	public static final String APP_VERSION = "6.9.0";
	public static final String APP_USER_AGENT =
			"PixivIOSApp/6.7.1 (iOS 10.3.1; iPhone8,1)";
	public static final String CLIENT_ID = "bYGKuGVw91e0NMfPGp44euvGt59s";
	public static final String CLIENT_SECRET = "HP3RmkgAmEGro0gn1x9ioawQE8WMfvLXDz3ZqxpK";

	// urls
	public static final String PIXIV_HOST = "https://www.pixiv.net";
	private static final String PIXIV_API_HOST = "https://app-api.pixiv.net";
	private static final String PIXIV_UNAUTH_RANKING_URL = PIXIV_HOST + "/ranking.php";
	private static final String PIXIV_RANKING_URL = PIXIV_API_HOST + "/v1/illust/ranking";

	public static final String MEMBER_ILLUST_URL =
			PIXIV_HOST + "/member_illust.php?mode=medium&illust_id=";

	public static final String DAILY_RANKING_URL =
			PIXIV_UNAUTH_RANKING_URL + "?mode=daily&format=json";
	public static final String WEEKLY_RANKING_URL =
			PIXIV_UNAUTH_RANKING_URL + "?mode=weekly&format=json";
	public static final String MONTHLY_RANKING_URL =
			PIXIV_UNAUTH_RANKING_URL + "?mode=monthly&format=json";

	public static final String BOOKMARK_URL =
			PIXIV_API_HOST + "/v1/user/bookmarks/illust";
	public static final String FOLLOW_URL =
			PIXIV_API_HOST + "/v2/illust/follow";
	public static final String OAUTH_URL =
			"https://oauth.secure.pixiv.net/auth/token";
}