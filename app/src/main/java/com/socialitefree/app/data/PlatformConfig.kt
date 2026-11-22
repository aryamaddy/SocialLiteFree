package com.socialitefree.app.data

import org.json.JSONObject

enum class Platform(
    val id: String,
    val displayName: String,
    val iconEmoji: String,
    val url: String,
    val host: String,
    val jsScriptAsset: String
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        iconEmoji = "▶️",
        url = "https://m.youtube.com",
        host = "youtube.com",
        jsScriptAsset = "js/youtube_controls.js"
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        iconEmoji = "📸",
        url = "https://www.instagram.com",
        host = "instagram.com",
        jsScriptAsset = "js/instagram_controls.js"
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        iconEmoji = "🎵",
        url = "https://www.tiktok.com",
        host = "tiktok.com",
        jsScriptAsset = "js/tiktok_controls.js"
    ),
    TWITTER(
        id = "twitter",
        displayName = "X / Twitter",
        iconEmoji = "🐦",
        url = "https://mobile.twitter.com",
        host = "twitter.com",
        jsScriptAsset = "js/twitter_controls.js"
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        iconEmoji = "👥",
        url = "https://m.facebook.com",
        host = "facebook.com",
        jsScriptAsset = "js/common_controls.js"
    ),
    SNAPCHAT(
        id = "snapchat",
        displayName = "Snapchat",
        iconEmoji = "👻",
        url = "https://web.snapchat.com",
        host = "snapchat.com",
        jsScriptAsset = "js/common_controls.js"
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        iconEmoji = "🤖",
        url = "https://www.reddit.com",
        host = "reddit.com",
        jsScriptAsset = "js/common_controls.js"
    );

    companion object {
        fun fromId(id: String): Platform? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}

data class AppSettings(
    // YouTube
    val ytBlockAds: Boolean = true,
    val ytHideShorts: Boolean = true,
    val ytHideComments: Boolean = false,
    val ytHideRecommendations: Boolean = true,
    val ytBackgroundPlay: Boolean = true,
    val ytSearchOnly: Boolean = false,
    val ytBlockFeedAutoplay: Boolean = true,

    // Instagram
    val igHideAds: Boolean = true,
    val igHideSuggested: Boolean = true,
    val igHideComments: Boolean = false,
    val igBlockPosting: Boolean = false,
    val igReelsBudget: Int = 30,

    // TikTok
    val ttBlockExplore: Boolean = true,
    val ttBlockLive: Boolean = true,
    val ttBlockShop: Boolean = true,
    val ttBlockUpload: Boolean = true,
    val ttBlockSearch: Boolean = false,
    val ttBlockComments: Boolean = false,

    // Twitter
    val twHideForYou: Boolean = true,
    val twHideAds: Boolean = true,

    // General
    val grayscaleMode: Boolean = false
) {
    fun toPlatformConfigJson(platform: Platform): String {
        val json = JSONObject()
        when (platform) {
            Platform.YOUTUBE -> {
                json.put("blockAds", ytBlockAds)
                json.put("hideShorts", ytHideShorts)
                json.put("hideComments", ytHideComments)
                json.put("hideRecommendations", ytHideRecommendations)
                json.put("backgroundPlay", ytBackgroundPlay)
                json.put("searchOnly", ytSearchOnly)
                json.put("blockFeedAutoplay", ytBlockFeedAutoplay)
            }
            Platform.INSTAGRAM -> {
                json.put("hideAds", igHideAds)
                json.put("hideSuggested", igHideSuggested)
                json.put("hideComments", igHideComments)
                json.put("blockPosting", igBlockPosting)
                json.put("reelsBudgetMinutes", igReelsBudget)
            }
            Platform.TIKTOK -> {
                json.put("blockExplore", ttBlockExplore)
                json.put("blockLive", ttBlockLive)
                json.put("blockShop", ttBlockShop)
                json.put("blockUpload", ttBlockUpload)
                json.put("blockSearch", ttBlockSearch)
                json.put("blockComments", ttBlockComments)
            }
            Platform.TWITTER -> {
                json.put("hideForYou", twHideForYou)
                json.put("hideAds", twHideAds)
            }
            else -> { /* Common handles it */ }
        }
        return json.toString()
    }

    fun toCommonConfigJson(): String {
        val json = JSONObject()
        json.put("grayscale", grayscaleMode)
        return json.toString()
    }
}
