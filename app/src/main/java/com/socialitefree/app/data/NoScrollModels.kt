package com.socialitefree.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class BlockerPlan(
    val id: String,
    val title: String,
    val shortTitle: String,
    val description: String
) {
    BLOCK_ALL(
        id = "BLOCK_ALL",
        title = "Block All",
        shortTitle = "Block All",
        description = "Blocks all Reels, Shorts & Spotlights instantly across supported apps."
    ),
    CURIOUS(
        id = "CURIOUS",
        title = "Curious Mode",
        shortTitle = "Curious",
        description = "Allows a timed short-video session, then enforces a cooldown lock."
    ),
    ONE_REEL(
        id = "ONE_REEL",
        title = "One Reel Mode",
        shortTitle = "One Reel",
        description = "Watch 1 opened or friend-sent reel freely; exits immediately if you scroll."
    ),
    PAUSED(
        id = "PAUSED",
        title = "Paused",
        shortTitle = "Paused",
        description = "Temporarily paused and automatically resumes when the timer ends."
    );

    companion object {
        fun fromId(id: String): BlockerPlan =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: BLOCK_ALL
    }
}

enum class BlockActionMode(
    val id: String,
    val label: String,
    val description: String
) {
    PRESS_BACK("PRESS_BACK", "Smart Back", "Exits the Reel/Short player back to feed"),
    GO_HOME("GO_HOME", "Minimize App", "Returns straight to your phone home screen"),
    LOCK_SCREEN("LOCK_SCREEN", "Lock Screen", "Locks your device screen (Android 9+)");

    companion object {
        fun fromId(id: String): BlockActionMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: PRESS_BACK
    }
}

enum class PinType(
    val id: String,
    val label: String,
    val description: String
) {
    NONE("NONE", "Off", "No PIN protection"),
    CUSTOM("CUSTOM", "Custom PIN", "Your own 4–8 digit secret PIN"),
    TIME_12H("TIME_12H", "Live Time (12h)", "Current clock HHMM (e.g. 0945). Changes every minute!"),
    TIME_24H("TIME_24H", "Live Time (24h)", "24-hour clock HHMM (e.g. 2145). Changes every minute!"),
    DATE_DDMM("DATE_DDMM", "Today's Date (DDMM)", "Current day & month (e.g. 2511). Changes daily!"),
    DEVICE_LOCK("DEVICE_LOCK", "Device Screen Lock", "Use phone fingerprint, PIN, or pattern");

    companion object {
        fun fromId(id: String): PinType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class WebMatchType(
    val id: String,
    val label: String,
    val description: String
) {
    DOMAIN("DOMAIN", "Entire Site", "Blocks domain & all subpages (e.g. instagram.com)"),
    WILDCARD("WILDCARD", "Path Prefix", "Blocks URLs starting with path (e.g. youtube.com/shorts)"),
    EXACT("EXACT", "Exact URL", "Blocks only the exact matching URL"),
    KEYWORD("KEYWORD", "Keyword", "Blocks any page or search containing this word");

    companion object {
        fun fromId(id: String): WebMatchType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: DOMAIN
    }
}

data class BlockedSiteRule(
    val pattern: String,
    val matchType: WebMatchType = WebMatchType.DOMAIN,
    val enabled: Boolean = true,
    val action: BlockActionMode = BlockActionMode.PRESS_BACK
) {
    fun matchesUrl(rawUrl: String): Boolean {
        if (!enabled) return false
        val cleanRule = pattern.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
            .lowercase()
        if (cleanRule.isBlank()) return false

        val normalizedUrl = rawUrl.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .lowercase()

        return when (matchType) {
            WebMatchType.KEYWORD -> normalizedUrl.contains(cleanRule)
            WebMatchType.EXACT -> normalizedUrl.trimEnd('/') == cleanRule
            WebMatchType.WILDCARD -> normalizedUrl.contains(cleanRule)
            WebMatchType.DOMAIN -> {
                val host = normalizedUrl.substringBefore('/').substringBefore('?').substringBefore(':')
                host == cleanRule || host.endsWith(".$cleanRule") || normalizedUrl.startsWith("$cleanRule/")
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("pattern", pattern)
        put("matchType", matchType.id)
        put("enabled", enabled)
        put("action", action.id)
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockedSiteRule? {
            val pattern = obj.optString("pattern", "").trim()
            if (pattern.isBlank()) return null
            return BlockedSiteRule(
                pattern = pattern,
                matchType = WebMatchType.fromId(obj.optString("matchType", WebMatchType.DOMAIN.id)),
                enabled = obj.optBoolean("enabled", true),
                action = BlockActionMode.fromId(obj.optString("action", BlockActionMode.PRESS_BACK.id))
            )
        }
    }
}

data class BlockedAppRule(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 0, // 0 = Always ask / Strict Lockdown
    val usedTodaySeconds: Int = 0,
    val usedDate: String = "",
    val unlockUntilMs: Long = 0L,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("dailyLimitMinutes", dailyLimitMinutes)
        put("usedTodaySeconds", usedTodaySeconds)
        put("usedDate", usedDate)
        put("unlockUntilMs", unlockUntilMs)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockedAppRule? {
            val pkg = obj.optString("packageName", "").trim()
            if (pkg.isBlank()) return null
            return BlockedAppRule(
                packageName = pkg,
                appName = obj.optString("appName", pkg),
                dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 0),
                usedTodaySeconds = obj.optInt("usedTodaySeconds", 0),
                usedDate = obj.optString("usedDate", ""),
                unlockUntilMs = obj.optLong("unlockUntilMs", 0L),
                enabled = obj.optBoolean("enabled", true)
            )
        }
    }
}

data class BlockHistoryEvent(
    val timestampMs: Long,
    val platformName: String,
    val packageName: String,
    val actionMode: String,
    val planUsed: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestampMs)
        put("platform", platformName)
        put("pkg", packageName)
        put("action", actionMode)
        put("plan", planUsed)
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockHistoryEvent? {
            val ts = obj.optLong("ts", 0L)
            if (ts <= 0L) return null
            return BlockHistoryEvent(
                timestampMs = ts,
                platformName = obj.optString("platform", "Unknown"),
                packageName = obj.optString("pkg", ""),
                actionMode = obj.optString("action", BlockActionMode.PRESS_BACK.label),
                planUsed = obj.optString("plan", BlockerPlan.BLOCK_ALL.shortTitle)
            )
        }
    }
}

object PinValidator {
    fun expectedDynamicCode(pinType: PinType): String {
        return when (pinType) {
            PinType.TIME_12H -> LocalTime.now().format(DateTimeFormatter.ofPattern("hhmm"))
            PinType.TIME_24H -> LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm"))
            PinType.DATE_DDMM -> LocalDate.now().format(DateTimeFormatter.ofPattern("ddMM"))
            else -> ""
        }
    }

    fun verify(input: String, pinType: PinType, customPin: String): Boolean {
        val clean = input.trim()
        return when (pinType) {
            PinType.NONE, PinType.DEVICE_LOCK -> true
            PinType.CUSTOM -> customPin.length >= 4 && clean == customPin
            PinType.TIME_12H, PinType.TIME_24H, PinType.DATE_DDMM ->
                clean == expectedDynamicCode(pinType)
        }
    }
}

data class EmojiMoodFeedback(
    val emoji: String,
    val title: String,
    val description: String
)

object MindfulContent {
    private val fallbackQuotes = listOf(
        "This pause is the point. Resisting the urge is the win.",
        "Reels can wait. Your goals absolutely cannot.",
        "Every second you hold on, you take back what the algorithm stole.",
        "The urge peaks, then fades. You're almost through it.",
        "One reel leads to forty. You already know this. Stay.",
        "Urges have a lifespan of 90 seconds. You're almost through it.",
        "Habits aren't broken in dramatic moments. They break in quiet ones like this."
    )

    fun loadQuotes(context: Context): List<String> {
        return try {
            val raw = context.assets.open("mindful_timer_quotes.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONObject(raw).optJSONArray("quotes") ?: return fallbackQuotes
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val q = arr.optString(i, "").trim()
                    if (q.isNotEmpty()) add(q)
                }
            }
            list.ifEmpty { fallbackQuotes }
        } catch (_: Exception) {
            fallbackQuotes
        }
    }

    fun dailyLimitMood(minutes: Int): EmojiMoodFeedback = when {
        minutes <= 0 -> EmojiMoodFeedback("🛑", "Total Lockdown!", "Monk mode activated. Locked unless explicitly unlocked.")
        minutes <= 30 -> EmojiMoodFeedback("🚀", "Laser Focus", "Amazing! You are taking back control of your day.")
        minutes <= 120 -> EmojiMoodFeedback("⚖️", "Healthy Balance", "A reasonable limit. Good job setting boundaries.")
        minutes <= 240 -> EmojiMoodFeedback("🕰️", "Slipping Away...", "Careful, you're dedicating a large chunk of your day.")
        else -> EmojiMoodFeedback("🧟", "Doomscroller Mode", "Consider lowering this limit to protect your focus.")
    }

    fun curiousMood(sessionMinutes: Int): EmojiMoodFeedback = when {
        sessionMinutes <= 2 -> EmojiMoodFeedback("🎯", "Quick Check-In", "Short burst only — great impulse control.")
        sessionMinutes <= 5 -> EmojiMoodFeedback("⏳", "Balanced Curious Window", "Enjoy a brief break, then cooldown kicks in automatically.")
        sessionMinutes <= 10 -> EmojiMoodFeedback("👀", "Stay Mindful", "Watch with intention. Cooldown will stop infinite scrolling.")
        else -> EmojiMoodFeedback("📉", "Slippery Slope", "Longer sessions make it easier to get pulled in.")
    }
}

object BlockerPackages {
    // 30+ Android browsers supported for external Shorts & Website blocking
    val BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_dev",
        "com.brave.browser_nightly",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.kiwibrowser.browser.dev",
        "com.ecosia.android",
        "org.bromite.bromite",
        "com.jio.web",
        "com.android.browser",
        "org.torproject.torbrowser",
        "com.mycompany.app.soulbrowser",
        "com.mmbox.xbrowser"
    )

    val URL_BAR_HINTS = listOf(
        ":id/url_bar",
        ":id/url_bar_title",
        ":id/mozac_browser_toolbar_url_view",
        ":id/location_bar_edit_text",
        ":id/addressbarEdit",
        ":id/addressbar_url",
        ":id/address_bar_edit_text",
        ":id/url_edittext",
        ":id/url_field",
        ":id/url_text",
        ":id/omnibarTextInput",
        ":id/search_box"
    )

    // Family packages per platform (from NoScroll platforms_config.json)
    val INSTAGRAM_PACKAGES = setOf(
        "com.instagram.android",
        "com.instagram.lite",
        "com.instapro.android",
        "com.instapro2.android"
    )
    val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "com.biomes.vanced",
        "com.google.android.apps.youtube.music",
        "com.snaptube.premium"
    )
    val FACEBOOK_PACKAGES = setOf("com.facebook.katana", "com.facebook.lite")
    val SNAPCHAT_PACKAGES = setOf("com.snapchat.android")
    val TIKTOK_PACKAGES = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme")
    val TWITTER_PACKAGES = setOf("com.twitter.android")
    val REDDIT_PACKAGES = setOf("com.reddit.frontpage")
    val LINKEDIN_PACKAGES = setOf("com.linkedin.android")
    val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    val EXTRA_SHORT_VIDEO_PACKAGES = setOf(
        "com.vk.clips", "com.vkontakte.android",
        "com.xingin.xhs",
        "tv.danmaku.bili", "com.bstar.intl", "com.bilibili.app.in"
    )

    val ALL_SHORT_VIDEO_PACKAGES = INSTAGRAM_PACKAGES + YOUTUBE_PACKAGES + FACEBOOK_PACKAGES +
        SNAPCHAT_PACKAGES + TIKTOK_PACKAGES + TWITTER_PACKAGES + REDDIT_PACKAGES +
        LINKEDIN_PACKAGES + WHATSAPP_PACKAGES + EXTRA_SHORT_VIDEO_PACKAGES
}

object SiteListSerializer {
    fun encodeSites(list: List<BlockedSiteRule>): String =
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

    fun decodeSites(raw: String): List<BlockedSiteRule> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { BlockedSiteRule.fromJson(it) }?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeApps(list: List<BlockedAppRule>): String =
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

    fun decodeApps(raw: String): List<BlockedAppRule> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { BlockedAppRule.fromJson(it) }?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeHistory(list: List<BlockHistoryEvent>): String =
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

    fun decodeHistory(raw: String): List<BlockHistoryEvent> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { BlockHistoryEvent.fromJson(it) }?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
