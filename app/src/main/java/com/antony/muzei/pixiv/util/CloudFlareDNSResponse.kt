package com.antony.muzei.pixiv.util

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudFlareDNSResponse(
        val AD: Boolean,
        val Answer: List<DNSAnswer>,
        val CD: Boolean,
        val Question: List<DNSQuestion>,
        val RA: Boolean,
        val RD: Boolean,
        val Status: Int,
        val TC: Boolean
)

@JsonClass(generateAdapter = true)
data class DNSAnswer(
        val TTL: Int,
        val data: String,
        val name: String,
        val type: Int
)

@JsonClass(generateAdapter = true)
data class DNSQuestion(
        val name: String,
        val type: Int
)
