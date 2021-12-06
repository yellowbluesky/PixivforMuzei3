package com.antony.muzei.pixiv.provider

import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Illusts
import retrofit2.Call

class IllustsHelper(_updateMode: String, _searchOption: String = "", _language: String = "") {
    private lateinit var illusts: Illusts
    private val updateMode: String = _updateMode
    private val searchOption: String = _searchOption
    private val language: String = _language
    private val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
        .create(PixivAuthFeedJsonService::class.java)


    fun getNewIllusts(): Illusts {
        val call: Call<Illusts?> = when (updateMode) {
            "follow" -> service.followJson
            "recommended" -> service.recommendedJson
            "artist" -> service.getArtistJson(searchOption)
            "tag_search" -> service.getTagSearchJson(language, searchOption)
            else -> throw IllegalStateException("Unexpected value: $updateMode")
        }
        illusts = call.execute().body()!!
        return illusts
    }

    fun getNextIllusts(): Illusts {
        val call = service.getNextUrl(illusts.next_url)
        illusts = call.execute().body()!!
        return illusts
    }

    fun getIllusts() = illusts
}
