package com.antony.muzei.pixiv.provider.network

import com.antony.muzei.pixiv.PixivProviderConst.APP_USER_AGENT
import com.antony.muzei.pixiv.provider.network.moshi.OAuth
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface PixivAddBookmarkService {
    @FormUrlEncoded
    @Headers(
        "Content-Type: application/x-www-form-urlencoded",
        "User-Agent: $APP_USER_AGENT",
        "Connection: close"
    )
    @POST("/v2/illust/bookmark/add")
    fun postArtworkBookmark(
        @Header("Authorization") accessToken: String,
        @FieldMap params: Map<String, String>
    ): Call<ResponseBody>
}
