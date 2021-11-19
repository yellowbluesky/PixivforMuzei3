package com.antony.muzei.pixiv.provider

import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Illusts
import retrofit2.Call

class IllustsManager(_updateMode: String, _data: String) {
    private var illusts: Illusts = Illusts()
    private val updateMode: String = _updateMode
    private val data: String = _data
    private val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
        .create(PixivAuthFeedJsonService::class.java)


    fun getNewIllusts(): Illusts {
        val call: Call<Illusts?> = when (updateMode) {
            "follow" -> service.followJson
            "bookmark" -> service.getBookmarkJson(data)
            "recommended" -> service.recommendedJson
            "artist" -> service.getArtistJson(data)
            "tag_search" -> service.getTagSearchJson(data)
            else -> throw IllegalStateException("Unexpected value: $updateMode")
        }
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNextIllusts(): Illusts {
        val call = service.getNextUrl(illusts.nextUrl)
        illusts = call.execute().body()!!
        return illusts
    }
}
