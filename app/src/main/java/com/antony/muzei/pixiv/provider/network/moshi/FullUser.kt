package com.antony.muzei.pixiv.provider.network.moshi

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FullUser(
    val user: UserDetail,
    val profile: Profile,
    val profile_publicity: profile_publicity,
    val workspace: Workspace
)

@JsonClass(generateAdapter = true)
data class UserDetail(
    val id: Int,
    val name: String,
    val account: String,
    val profile_image_urls: UserProfileImage,
    val comment: String,
    val is_followed: Boolean,
)

@JsonClass(generateAdapter = true)
data class UserProfileImage(
    val medium: String,
)

@JsonClass(generateAdapter = true)
data class Profile(
    val webpage: String?,
    val gender: String,
    val birth: String,
    val birth_day: String,
    val birth_year: Int,
    val region: String,
    val address_id: Int,
    val country_code: String,
    val job: String,
    val job_id: Int,
    val total_follow_users: Int,
    val total_mypixiv_users: Int,
    val total_illusts: Int,
    val total_manga: Int,
    val total_novels: Int,
    val total_illust_bookmarks_public: Int,
    val total_illust_series: Int,
    val total_novel_series: Int,
    val background_image_url: String,
    val twitter_account: String,
    val twitter_url: String?,
    val pawoo_url: String?,
    val is_premium: Boolean,
    val is_using_custom_profile_image: Boolean,
)

@JsonClass(generateAdapter = true)
data class profile_publicity(
    val gender: String,
    val region: String,
    val birth_day: String,
    val birth_year: String,
    val job: String,
    val pawoo: Boolean,
)

@JsonClass(generateAdapter = true)
data class Workspace(
    val pc: String,
    val monitor: String,
    val tool: String,
    val scanner: String,
    val tablet: String,
    val mouse: String,
    val printer: String,
    val desktop: String,
    val music: String,
    val desk: String,
    val chair: String,
    val comment: String,
    val workspace_image_url: String?,

)
