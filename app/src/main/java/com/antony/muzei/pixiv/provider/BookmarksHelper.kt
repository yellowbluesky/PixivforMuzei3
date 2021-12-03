package com.antony.muzei.pixiv.provider

import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Illusts

class BookmarksHelper(_userId: String) {
    private lateinit var illusts: Illusts
    private val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
        .create(PixivAuthFeedJsonService::class.java)
    private val userId: String = _userId

    fun getNewIllusts(maxBookmarkId: String): Illusts {
        val call = service.getBookmarkOffsetJson(userId, maxBookmarkId)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNewIllusts(): Illusts {
        val call = service.getBookmarkJson(userId)
        illusts = call.execute().body()!!
        return illusts
    }
}
