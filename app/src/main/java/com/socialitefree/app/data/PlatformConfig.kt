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
        jsScriptAsset = "js/youtube_personal.js"
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        iconEmoji = "📸",
        url = "https://www.instagram.com",
        host = "instagram.com",
        jsScriptAsset = "js/instagram_feed_filter.js"
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
        url = "https://x.com",
        host = "x.com",
        jsScriptAsset = "js/twitter_controls.js"
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        iconEmoji = "👥",
        url = "https://m.facebook.com",
        host = "facebook.com",
        jsScriptAsset = "js/kid_facebook_controls.js"
    ),
    SNAPCHAT(
        id = "snapchat",
        displayName = "Snapchat",
        iconEmoji = "👻",
        url = "https://web.snapchat.com",
        host = "snapchat.com",
        jsScriptAsset = "js/kid_snapchat_polish.js"
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        iconEmoji = "🤖",
        url = "https://www.reddit.com",
        host = "reddit.com",
        jsScriptAsset = "js/kid_reddit_controls.js"
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
    val ytHideRecommendations: Boolean = false,
    val ytBackgroundPlay: Boolean = true,
    val ytSearchOnly: Boolean = false,
    val ytBlockFeedAutoplay: Boolean = false,

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
                // Config keys matching personal.js
                json.put("ads", ytBlockAds)
                json.put("shorts", ytHideShorts)
                json.put("comments", ytHideComments)
                json.put("watchRecs", ytHideRecommendations)
                json.put("background", ytBackgroundPlay)
                json.put("searchOnly", ytSearchOnly)
                json.put("feedAutoplay", ytBlockFeedAutoplay)
                json.put("profiles", false)
                json.put("quality", "auto")
                json.put("captions", false)
                json.put("sleep", JSONObject.NULL)
            }
            Platform.INSTAGRAM -> {
                // Config keys matching feed_response_filter.js
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
