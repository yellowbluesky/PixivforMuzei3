package com.antony.muzei.pixiv.provider

import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Illusts

class BookmarksHelper(_userId: String) {
    private lateinit var illusts: Illusts
    private val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
        .create(PixivAuthFeedJsonService::class.java)
    private val userId: String = _userId

    fun getNewPublicBookmarks(maxBookmarkId: String): Illusts {
        val call = service.getPublicBookmarkOffsetJson(userId, maxBookmarkId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPrivateIllusts(maxBookmarkId: String): Illusts {
        val call = service.getPrivateBookmarkOffsetJson(userId, maxBookmarkId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPublicBookmarks(): Illusts {
        val call = service.getPublicBookmarkJson(userId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewPrivateIllusts(): Illusts {
        val call = service.getPrivateBookmarkJson(userId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNextBookmarks(): Illusts {
        val call = service.getNextUrl(illusts.next_url)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getBookmarks() = illusts
}
