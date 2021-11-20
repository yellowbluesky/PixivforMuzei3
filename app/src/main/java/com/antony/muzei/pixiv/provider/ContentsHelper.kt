package com.antony.muzei.pixiv.provider

import com.antony.muzei.pixiv.provider.network.PixivRankingFeedJsonService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.Contents

class ContentsHelper(_updateMode: String) {
    private val updateMode = _updateMode
    val service = RestClient.getRetrofitRankingInstance().create(
        PixivRankingFeedJsonService::class.java
    )
    private var contents: Contents = Contents()
    private var pageNumber = 1
    private lateinit var date: String
    lateinit var prevDate: String

    fun getNewContents(): Contents {
        val call = service.getRankingJson(updateMode)
        contents = call.execute().body()!!
        date = contents.date
        prevDate = contents.prev_date
        return contents
    }

    fun getNextContents(): Contents {
        if (pageNumber != 9) {
            pageNumber++
            val call = service.getRankingJson(updateMode, pageNumber, date)
            contents = call.execute().body()!!
        } else {
            // If we for some reason cannot find enough artwork to satisfy the filter
            // from the top 450, then we can look at the previous day's ranking
            pageNumber = 1
            val call = service.getRankingJson(updateMode, pageNumber, prevDate)
            contents = call.execute().body()!!
            date = contents.date
            prevDate = contents.prev_date
        }
        return contents
    }
}
