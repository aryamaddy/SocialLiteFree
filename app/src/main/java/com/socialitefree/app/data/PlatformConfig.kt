package com.socialitefree.app.data

import androidx.annotation.DrawableRes
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.R
import org.json.JSONArray
import org.json.JSONObject

enum class Platform(
    val id: String,
    val displayName: String,
    val url: String,
    val host: String,
    val jsScriptAsset: String,
    @DrawableRes val paintedIconRes: Int,
    @DrawableRes val classicIconRes: Int,
    @DrawableRes val gearIconRes: Int,
    @DrawableRes val settingsBannerRes: Int?,
    val nativePackages: List<String>,
    val defaultPreferNative: Boolean = false,
    val isBeta: Boolean = false,
    val defaultVisible: Boolean = true
) {
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        url = "https://www.instagram.com",
        host = "instagram.com",
        jsScriptAsset = "js/instagram_feed_filter.js",
        paintedIconRes = R.drawable.ic_instagram_painted,
        classicIconRes = R.drawable.ic_instagram_classic,
        gearIconRes = R.drawable.gear_instagram,
        settingsBannerRes = R.drawable.settings_banner_instagram,
        nativePackages = listOf("com.instagram.android", "com.instagram.lite", "com.instapro.android", "com.instapro2.android"),
        defaultPreferNative = BuildConfig.SUPPORTS_NATIVE_BLOCKER,
        isBeta = false,
        defaultVisible = true
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        url = "https://m.youtube.com",
        host = "youtube.com",
        jsScriptAsset = "js/youtube_personal.js",
        paintedIconRes = R.drawable.ic_youtube_painted,
        classicIconRes = R.drawable.ic_youtube_classic,
        gearIconRes = R.drawable.gear_youtube,
        settingsBannerRes = R.drawable.settings_banner_youtube,
        nativePackages = listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.biomes.vanced", "com.google.android.apps.youtube.music"),
        defaultPreferNative = false,
        isBeta = false,
        defaultVisible = true
    ),
    TWITTER(
        id = "twitter",
        displayName = "X",
        url = "https://x.com",
        host = "x.com",
        jsScriptAsset = "js/twitter_controls.js",
        paintedIconRes = R.drawable.ic_twitter_painted,
        classicIconRes = R.drawable.ic_twitter_classic,
        gearIconRes = R.drawable.gear_x,
        settingsBannerRes = R.drawable.settings_banner_x,
        nativePackages = listOf("com.twitter.android"),
        defaultPreferNative = false,
        isBeta = true,
        defaultVisible = true
    ),
    SNAPCHAT(
        id = "snapchat",
        displayName = "Snapchat",
        url = "https://web.snapchat.com",
        host = "snapchat.com",
        jsScriptAsset = "js/kid_snapchat_polish.js",
        paintedIconRes = R.drawable.ic_snapchat_painted,
        classicIconRes = R.drawable.ic_snapchat_classic,
        gearIconRes = R.drawable.gear_snapchat,
        settingsBannerRes = null,
        nativePackages = listOf("com.snapchat.android"),
        defaultPreferNative = false,
        isBeta = true,
        defaultVisible = true
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        url = "https://m.facebook.com",
        host = "facebook.com",
        jsScriptAsset = "js/kid_facebook_controls.js",
        paintedIconRes = R.drawable.ic_facebook_painted,
        classicIconRes = R.drawable.ic_facebook_classic,
        gearIconRes = R.drawable.gear_facebook,
        settingsBannerRes = null,
        nativePackages = listOf("com.facebook.katana"),
        defaultPreferNative = false,
        isBeta = true,
        defaultVisible = true
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        url = "https://www.tiktok.com",
        host = "tiktok.com",
        jsScriptAsset = "js/tiktok_controls.js",
        paintedIconRes = R.drawable.ic_tiktok_painted,
        classicIconRes = R.drawable.ic_tiktok_classic,
        gearIconRes = R.drawable.gear_tiktok,
        settingsBannerRes = null,
        nativePackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        defaultPreferNative = false,
        isBeta = true,
        defaultVisible = true
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        url = "https://www.reddit.com",
        host = "reddit.com",
        jsScriptAsset = "js/kid_reddit_controls.js",
        paintedIconRes = R.drawable.reddit_ios,
        classicIconRes = R.drawable.reddit_ios,
        gearIconRes = R.drawable.gear_painted,
        settingsBannerRes = null,
        nativePackages = listOf("com.reddit.frontpage"),
        defaultPreferNative = false,
        isBeta = false,
        defaultVisible = false
    ),
    LINKEDIN(
        id = "linkedin",
        displayName = "LinkedIn",
        url = "https://www.linkedin.com",
        host = "linkedin.com",
        jsScriptAsset = "js/common_controls.js",
        paintedIconRes = R.drawable.ic_linkedin_painted,
        classicIconRes = R.drawable.ic_linkedin_classic,
        gearIconRes = R.drawable.gear_painted,
        settingsBannerRes = null,
        nativePackages = listOf("com.linkedin.android"),
        defaultPreferNative = false,
        isBeta = false,
        defaultVisible = false
    );

    fun iconRes(useClassic: Boolean): Int = if (useClassic) classicIconRes else paintedIconRes

    companion object {
        fun fromId(id: String): Platform? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}

data class AppSettings(
    // Appearance & Launch
    val useClassicIcons: Boolean = false,
    val autoAppLaunch: Boolean = false,
    val favoritePlatformId: String = "instagram",

    // Native App Blocker (Original Installed Apps)
    val nativeBlockIgReels: Boolean = true,
    val nativeAllowSentReels: Boolean = true,
    val nativeLockIgReelsTab: Boolean = true,
    val nativeBlockIgExplore: Boolean = false,
    val nativeBlockIgStories: Boolean = false,
    val nativeBlockYtShorts: Boolean = true,
    val nativeBlockFbReels: Boolean = true,
    val nativeBlockSnapSpotlight: Boolean = true,
    val nativeBlockSnapDiscover: Boolean = false,
    val nativeBlockSnapStories: Boolean = false,
    val nativeBlockTiktok: Boolean = true,
    val nativeBlockTwitterVideo: Boolean = true,
    val nativeBlockRedditVideo: Boolean = true,
    val nativeBlockLinkedinVideo: Boolean = true,
    val nativeBlockWhatsAppStatus: Boolean = false,
    val nativeBlockBrowserShorts: Boolean = true,
    val blockerPlan: BlockerPlan = BlockerPlan.BLOCK_ALL,
    val blockActionMode: BlockActionMode = BlockActionMode.PRESS_BACK,
    val curiousSessionMinutes: Int = 5,
    val curiousCooldownMinutes: Int = 2,
    val curiousSessionStartMs: Long = 0L,
    val curiousCooldownUntilMs: Long = 0L,
    val curiousLockSwitchInCooldown: Boolean = true,
    val curiousAllowOneInCooldown: Boolean = false,
    val pauseUntilMs: Long = 0L,
    val pauseDurationMinutes: Int = 10,
    val pauseWaitSeconds: Int = 10,
    val pauseCooldownMinutes: Int = 5,
    val pauseCooldownUntilMs: Long = 0L,
    val reelCounterEnabled: Boolean = true,
    val reelCounterDetailed: Boolean = true,
    val reelCounterHaptics: Boolean = true,
    val blockHaptics: Boolean = true,
    val blockedApps: List<BlockedAppRule> = emptyList(),
    val blockedSites: List<BlockedSiteRule> = emptyList(),
    val pinType: PinType = PinType.NONE,
    val customPin: String = "",
    val pinProtectSettings: Boolean = true,
    val pinProtectPlanSwitch: Boolean = true,
    val pinProtectAppLocks: Boolean = true,
    val pinProtectSystemSettings: Boolean = false,

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
    val igBlockReels: Boolean = true,
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

    // Twitter / X
    val twHideForYou: Boolean = true,
    val twHideAds: Boolean = true,

    // Facebook
    val fbBlockReels: Boolean = true,
    val fbHidePeople: Boolean = true,
    val fbHideMarketplace: Boolean = false,

    // Reddit
    val redditBlockAds: Boolean = true,
    val redditHideSuggested: Boolean = true,
    val redditBlockPopular: Boolean = false,

    // General & Sleep Mode
    val grayscaleMode: Boolean = false,
    val sleepModeEnabled: Boolean = false,
    val sleepStartHour: Int = 22,
    val sleepStartMinute: Int = 0,
    val sleepEndHour: Int = 7,
    val sleepEndMinute: Int = 0
) {
    fun toPlatformConfigJson(platform: Platform): String {
        val json = JSONObject()
        when (platform) {
            Platform.YOUTUBE -> {
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
                json.put("hideAds", igHideAds)
                json.put("hideSuggested", igHideSuggested)
                json.put("blockReels", igBlockReels)
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
            Platform.FACEBOOK -> {
                json.put("hosts", JSONArray(listOf("m.facebook.com", "www.facebook.com", "facebook.com", "mbasic.facebook.com")))
                json.put("bridge", "__NS__")
                val effective = JSONObject()
                effective.put("fb_hideHome", false)
                effective.put("fb_hidePeople", fbHidePeople)
                effective.put("fb_hideMessenger", false)
                effective.put("fb_hideNotifications", false)
                effective.put("fb_hideMarketplace", fbHideMarketplace)
                effective.put("fb_marketplaceMode", false)
                effective.put("fb_blockReels", fbBlockReels)
                json.put("effective", effective)
            }
            Platform.REDDIT -> {
                json.put("hosts", JSONArray(listOf("www.reddit.com", "reddit.com", "old.reddit.com", "m.reddit.com")))
                val rules = JSONObject()
                rules.put("reddit_ad_blocker", redditBlockAds)
                rules.put("reddit_hide_suggested", redditHideSuggested)
                rules.put("reddit_only_my_communities", false)
                rules.put("reddit_block_popular", redditBlockPopular)
                rules.put("reddit_block_news", false)
                rules.put("reddit_block_latest", false)
                json.put("rules", rules)
            }
            Platform.SNAPCHAT -> {
                json.put("hosts", JSONArray(listOf("web.snapchat.com", "www.snapchat.com", "snapchat.com")))
            }
            Platform.LINKEDIN -> {
                json.put("grayscale", grayscaleMode)
            }
        }
        return json.toString()
    }

    fun toCommonConfigJson(): String {
        val json = JSONObject()
        json.put("grayscale", grayscaleMode)
        return json.toString()
    }

    fun isInSleepWindow(currentHour: Int, currentMinute: Int): Boolean {
        if (!sleepModeEnabled) return false
        val nowMins = currentHour * 60 + currentMinute
        val startMins = sleepStartHour * 60 + sleepStartMinute
        val endMins = sleepEndHour * 60 + sleepEndMinute
        return if (startMins <= endMins) {
            nowMins in startMins until endMins
        } else {
            nowMins >= startMins || nowMins < endMins
        }
    }
}
