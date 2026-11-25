package com.socialitefree.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "socialitefree_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Appearance & Auto Launch
        val USE_CLASSIC_ICONS = booleanPreferencesKey("use_classic_icons")
        val AUTO_APP_LAUNCH = booleanPreferencesKey("auto_app_launch")
        val DEFAULT_PLATFORM = stringPreferencesKey("default_platform")

        // Visibility & Native App Launch preferences
        fun showPlatformKey(platformId: String) = booleanPreferencesKey("show_platform_$platformId")
        fun preferNativeKey(platformId: String) = booleanPreferencesKey("prefer_native_$platformId")

        // Native Blocker rules (for original installed apps)
        val NATIVE_BLOCK_IG_REELS = booleanPreferencesKey("native_block_ig_reels")
        val NATIVE_ALLOW_SENT_REELS = booleanPreferencesKey("native_allow_sent_reels")
        val NATIVE_LOCK_IG_REELS_TAB = booleanPreferencesKey("native_lock_ig_reels_tab")
        val NATIVE_BLOCK_IG_EXPLORE = booleanPreferencesKey("native_block_ig_explore")
        val NATIVE_BLOCK_IG_STORIES = booleanPreferencesKey("native_block_ig_stories")
        val NATIVE_BLOCK_YT_SHORTS = booleanPreferencesKey("native_block_yt_shorts")
        val NATIVE_BLOCK_FB_REELS = booleanPreferencesKey("native_block_fb_reels")
        val NATIVE_BLOCK_SNAP_SPOTLIGHT = booleanPreferencesKey("native_block_snap_spotlight")
        val NATIVE_BLOCK_SNAP_DISCOVER = booleanPreferencesKey("native_block_snap_discover")
        val NATIVE_BLOCK_SNAP_STORIES = booleanPreferencesKey("native_block_snap_stories")
        val NATIVE_BLOCK_TIKTOK = booleanPreferencesKey("native_block_tiktok")
        val NATIVE_BLOCK_TWITTER_VIDEO = booleanPreferencesKey("native_block_twitter_video")
        val NATIVE_BLOCK_REDDIT_VIDEO = booleanPreferencesKey("native_block_reddit_video")
        val NATIVE_BLOCK_LINKEDIN_VIDEO = booleanPreferencesKey("native_block_linkedin_video")
        val NATIVE_BLOCK_WHATSAPP_STATUS = booleanPreferencesKey("native_block_whatsapp_status")
        val NATIVE_BLOCK_BROWSER_SHORTS = booleanPreferencesKey("native_block_browser_shorts")
        val NATIVE_BLOCKED_COUNT = intPreferencesKey("native_blocked_count")
        val LAST_NOTIFIED_UPDATE_CODE = intPreferencesKey("last_notified_update_code")

        // NoScroll Plan & Modes
        val BLOCKER_PLAN = stringPreferencesKey("blocker_plan")
        val PLAN_BEFORE_PAUSE = stringPreferencesKey("plan_before_pause")
        val BLOCK_ACTION_MODE = stringPreferencesKey("block_action_mode")

        // Curious Mode
        val CURIOUS_SESSION_MINUTES = intPreferencesKey("curious_session_minutes")
        val CURIOUS_COOLDOWN_MINUTES = intPreferencesKey("curious_cooldown_minutes")
        val CURIOUS_SESSION_START_MS = longPreferencesKey("curious_session_start_ms")
        val CURIOUS_COOLDOWN_UNTIL_MS = longPreferencesKey("curious_cooldown_until_ms")
        val CURIOUS_LOCK_SWITCH_IN_COOLDOWN = booleanPreferencesKey("curious_lock_switch_in_cooldown")
        val CURIOUS_ALLOW_ONE_IN_COOLDOWN = booleanPreferencesKey("curious_allow_one_in_cooldown")

        // Pause & Mindful Wait
        val PAUSE_UNTIL_MS = longPreferencesKey("pause_until_ms")
        val PAUSE_DURATION_MINUTES = intPreferencesKey("pause_duration_minutes")
        val PAUSE_WAIT_SECONDS = intPreferencesKey("pause_wait_seconds")
        val PAUSE_COOLDOWN_MINUTES = intPreferencesKey("pause_cooldown_minutes")
        val PAUSE_COOLDOWN_UNTIL_MS = longPreferencesKey("pause_cooldown_until_ms")

        // Floating Scroll Counter Overlay & Haptics
        val REEL_COUNTER_ENABLED = booleanPreferencesKey("reel_counter_enabled")
        val REEL_COUNTER_DETAILED = booleanPreferencesKey("reel_counter_detailed")
        val REEL_COUNTER_HAPTICS = booleanPreferencesKey("reel_counter_haptics")
        val BLOCK_HAPTICS = booleanPreferencesKey("block_haptics")

        // App Blocker, Website Blocker, PIN Lock
        val BLOCKED_APPS_JSON = stringPreferencesKey("blocked_apps_json")
        val BLOCKED_SITES_JSON = stringPreferencesKey("blocked_sites_json")
        val PIN_TYPE = stringPreferencesKey("pin_type")
        val CUSTOM_PIN = stringPreferencesKey("custom_pin")
        val PIN_PROTECT_SETTINGS = booleanPreferencesKey("pin_protect_settings")
        val PIN_PROTECT_PLAN_SWITCH = booleanPreferencesKey("pin_protect_plan_switch")
        val PIN_PROTECT_APP_LOCKS = booleanPreferencesKey("pin_protect_app_locks")
        val PIN_PROTECT_SYSTEM_SETTINGS = booleanPreferencesKey("pin_protect_system_settings")

        // Analytics & History
        val TODAY_BLOCKS_DATE = stringPreferencesKey("today_blocks_date")
        val TODAY_BLOCKS_COUNT = intPreferencesKey("today_blocks_count")
        val TODAY_REELS_SCROLLED = intPreferencesKey("today_reels_scrolled")
        val BLOCK_HISTORY_JSON = stringPreferencesKey("block_history_json")

        // Usage tracking per platform (seconds)
        fun usageSecondsKey(platformId: String) = intPreferencesKey("usage_sec_$platformId")
        val USAGE_DATE = stringPreferencesKey("usage_date")

        // YouTube keys
        val YT_BLOCK_ADS = booleanPreferencesKey("yt_block_ads")
        val YT_HIDE_SHORTS = booleanPreferencesKey("yt_hide_shorts")
        val YT_HIDE_COMMENTS = booleanPreferencesKey("yt_hide_comments")
        val YT_HIDE_RECOMMENDATIONS = booleanPreferencesKey("yt_hide_recommendations")
        val YT_BACKGROUND_PLAY = booleanPreferencesKey("yt_background_play")
        val YT_SEARCH_ONLY = booleanPreferencesKey("yt_search_only")
        val YT_BLOCK_FEED_AUTOPLAY = booleanPreferencesKey("yt_block_feed_autoplay")

        // Instagram keys
        val IG_HIDE_ADS = booleanPreferencesKey("ig_hide_ads")
        val IG_HIDE_SUGGESTED = booleanPreferencesKey("ig_hide_suggested")
        val IG_BLOCK_REELS = booleanPreferencesKey("ig_block_reels")
        val IG_HIDE_COMMENTS = booleanPreferencesKey("ig_hide_comments")
        val IG_BLOCK_POSTING = booleanPreferencesKey("ig_block_posting")
        val IG_REELS_BUDGET = intPreferencesKey("ig_reels_budget")

        // TikTok keys
        val TT_BLOCK_EXPLORE = booleanPreferencesKey("tt_block_explore")
        val TT_BLOCK_LIVE = booleanPreferencesKey("tt_block_live")
        val TT_BLOCK_SHOP = booleanPreferencesKey("tt_block_shop")
        val TT_BLOCK_UPLOAD = booleanPreferencesKey("tt_block_upload")
        val TT_BLOCK_SEARCH = booleanPreferencesKey("tt_block_search")
        val TT_BLOCK_COMMENTS = booleanPreferencesKey("tt_block_comments")

        // Twitter keys
        val TW_HIDE_FOR_YOU = booleanPreferencesKey("tw_hide_for_you")
        val TW_HIDE_ADS = booleanPreferencesKey("tw_hide_ads")

        // Facebook keys
        val FB_BLOCK_REELS = booleanPreferencesKey("fb_block_reels")
        val FB_HIDE_PEOPLE = booleanPreferencesKey("fb_hide_people")
        val FB_HIDE_MARKETPLACE = booleanPreferencesKey("fb_hide_marketplace")

        // Reddit keys
        val REDDIT_BLOCK_ADS = booleanPreferencesKey("reddit_block_ads")
        val REDDIT_HIDE_SUGGESTED = booleanPreferencesKey("reddit_hide_suggested")
        val REDDIT_BLOCK_POPULAR = booleanPreferencesKey("reddit_block_popular")

        // General & Sleep Mode
        val GRAYSCALE_MODE = booleanPreferencesKey("grayscale_mode")
        val SLEEP_MODE_ENABLED = booleanPreferencesKey("sleep_mode_enabled")
        val SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val SLEEP_START_MINUTE = intPreferencesKey("sleep_start_minute")
        val SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val SLEEP_END_MINUTE = intPreferencesKey("sleep_end_minute")
    }

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = true): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    fun getInt(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    fun getLong(key: Preferences.Key<Long>, default: Long = 0L): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    fun isPlatformVisible(platform: Platform): Flow<Boolean> =
        getBoolean(showPlatformKey(platform.id), platform.defaultVisible)

    fun preferNativeApp(platform: Platform): Flow<Boolean> =
        getBoolean(preferNativeKey(platform.id), platform.defaultPreferNative)

    suspend fun incrementNativeBlockedCount() {
        recordBlockEvent("Short Video", "", BlockActionMode.PRESS_BACK.label, BlockerPlan.BLOCK_ALL.shortTitle)
    }

    suspend fun recordBlockEvent(
        platformName: String,
        packageName: String,
        actionMode: String,
        planUsed: String
    ) {
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            prefs[NATIVE_BLOCKED_COUNT] = (prefs[NATIVE_BLOCKED_COUNT] ?: 0) + 1
            if (prefs[TODAY_BLOCKS_DATE] != today) {
                prefs[TODAY_BLOCKS_DATE] = today
                prefs[TODAY_BLOCKS_COUNT] = 1
                prefs[TODAY_REELS_SCROLLED] = 0
            } else {
                prefs[TODAY_BLOCKS_COUNT] = (prefs[TODAY_BLOCKS_COUNT] ?: 0) + 1
            }

            val existing = SiteListSerializer.decodeHistory(prefs[BLOCK_HISTORY_JSON] ?: "")
            val updated = (listOf(
                BlockHistoryEvent(now, platformName, packageName, actionMode, planUsed)
            ) + existing).take(45)
            prefs[BLOCK_HISTORY_JSON] = SiteListSerializer.encodeHistory(updated)
        }
    }

    suspend fun incrementReelsScrolled() {
        val today = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            if (prefs[TODAY_BLOCKS_DATE] != today) {
                prefs[TODAY_BLOCKS_DATE] = today
                prefs[TODAY_BLOCKS_COUNT] = 0
                prefs[TODAY_REELS_SCROLLED] = 1
            } else {
                prefs[TODAY_REELS_SCROLLED] = (prefs[TODAY_REELS_SCROLLED] ?: 0) + 1
            }
        }
    }

    suspend fun clearBlockHistory() {
        context.dataStore.edit { prefs ->
            prefs[BLOCK_HISTORY_JSON] = "[]"
        }
    }

    suspend fun startPauseSession(minutes: Int) {
        val now = System.currentTimeMillis()
        val durationMs = minutes * 60_000L
        context.dataStore.edit { prefs ->
            val currentPlan = prefs[BLOCKER_PLAN] ?: BlockerPlan.BLOCK_ALL.id
            if (currentPlan != BlockerPlan.PAUSED.id) {
                prefs[PLAN_BEFORE_PAUSE] = currentPlan
            }
            val cooldownMins = prefs[PAUSE_COOLDOWN_MINUTES] ?: 5
            prefs[PAUSE_DURATION_MINUTES] = minutes
            prefs[PAUSE_UNTIL_MS] = now + durationMs
            prefs[PAUSE_COOLDOWN_UNTIL_MS] = now + durationMs + cooldownMins * 60_000L
            prefs[BLOCKER_PLAN] = BlockerPlan.PAUSED.id
        }
    }

    suspend fun resumeFromPause() {
        context.dataStore.edit { prefs ->
            val prev = prefs[PLAN_BEFORE_PAUSE] ?: BlockerPlan.BLOCK_ALL.id
            prefs[BLOCKER_PLAN] = if (prev == BlockerPlan.PAUSED.id) BlockerPlan.BLOCK_ALL.id else prev
            prefs[PAUSE_UNTIL_MS] = 0L
        }
    }

    suspend fun switchPlan(plan: BlockerPlan) {
        context.dataStore.edit { prefs ->
            prefs[BLOCKER_PLAN] = plan.id
            if (plan != BlockerPlan.PAUSED) {
                prefs[PAUSE_UNTIL_MS] = 0L
            }
            if (plan == BlockerPlan.CURIOUS) {
                prefs[CURIOUS_SESSION_START_MS] = 0L
                prefs[CURIOUS_COOLDOWN_UNTIL_MS] = 0L
            }
        }
    }

    suspend fun updateCuriousWindow(sessionStartMs: Long, cooldownUntilMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[CURIOUS_SESSION_START_MS] = sessionStartMs
            prefs[CURIOUS_COOLDOWN_UNTIL_MS] = cooldownUntilMs
        }
    }

    suspend fun setBlockedApps(apps: List<BlockedAppRule>) {
        setString(BLOCKED_APPS_JSON, SiteListSerializer.encodeApps(apps))
    }

    suspend fun setBlockedSites(sites: List<BlockedSiteRule>) {
        setString(BLOCKED_SITES_JSON, SiteListSerializer.encodeSites(sites))
    }

    suspend fun grantTemporaryAppUnlock(packageName: String, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        context.dataStore.edit { prefs ->
            val list = SiteListSerializer.decodeApps(prefs[BLOCKED_APPS_JSON] ?: "")
            val updated = list.map { app ->
                if (app.packageName == packageName) app.copy(unlockUntilMs = until) else app
            }
            prefs[BLOCKED_APPS_JSON] = SiteListSerializer.encodeApps(updated)
        }
    }

    suspend fun tickBlockedAppUsage(packageName: String, deltaSeconds: Int) {
        val today = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val list = SiteListSerializer.decodeApps(prefs[BLOCKED_APPS_JSON] ?: "")
            var changed = false
            val updated = list.map { app ->
                if (app.packageName == packageName) {
                    changed = true
                    val prevSec = if (app.usedDate == today) app.usedTodaySeconds else 0
                    app.copy(usedTodaySeconds = prevSec + deltaSeconds, usedDate = today)
                } else {
                    if (app.usedDate.isNotEmpty() && app.usedDate != today) {
                        changed = true
                        app.copy(usedTodaySeconds = 0, usedDate = today)
                    } else app
                }
            }
            if (changed) {
                prefs[BLOCKED_APPS_JSON] = SiteListSerializer.encodeApps(updated)
            }
        }
    }

    suspend fun addUsageSeconds(platformId: String, deltaSeconds: Int) {
        val today = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val savedDate = prefs[USAGE_DATE]
            if (savedDate != today) {
                Platform.entries.forEach { p -> prefs[usageSecondsKey(p.id)] = 0 }
                prefs[USAGE_DATE] = today
            }
            val key = usageSecondsKey(platformId)
            val current = prefs[key] ?: 0
            prefs[key] = current + deltaSeconds
        }
    }

    fun getAllUsageSeconds(): Flow<Map<String, Int>> =
        context.dataStore.data.map { prefs ->
            val today = LocalDate.now().toString()
            if (prefs[USAGE_DATE] != today) {
                emptyMap()
            } else {
                Platform.entries.associate { p -> p.id to (prefs[usageSecondsKey(p.id)] ?: 0) }
            }
        }

    // Appearance & Launch flows
    val useClassicIcons: Flow<Boolean> = getBoolean(USE_CLASSIC_ICONS, false)
    val autoAppLaunch: Flow<Boolean> = getBoolean(AUTO_APP_LAUNCH, false)
    val defaultPlatform: Flow<String> = getString(DEFAULT_PLATFORM, "instagram")

    // Native Blocker flows
    val nativeBlockIgReels: Flow<Boolean> = getBoolean(NATIVE_BLOCK_IG_REELS, true)
    val nativeAllowSentReels: Flow<Boolean> = getBoolean(NATIVE_ALLOW_SENT_REELS, true)
    val nativeLockIgReelsTab: Flow<Boolean> = getBoolean(NATIVE_LOCK_IG_REELS_TAB, true)
    val nativeBlockIgExplore: Flow<Boolean> = getBoolean(NATIVE_BLOCK_IG_EXPLORE, false)
    val nativeBlockIgStories: Flow<Boolean> = getBoolean(NATIVE_BLOCK_IG_STORIES, false)
    val nativeBlockYtShorts: Flow<Boolean> = getBoolean(NATIVE_BLOCK_YT_SHORTS, true)
    val nativeBlockFbReels: Flow<Boolean> = getBoolean(NATIVE_BLOCK_FB_REELS, true)
    val nativeBlockSnapSpotlight: Flow<Boolean> = getBoolean(NATIVE_BLOCK_SNAP_SPOTLIGHT, true)
    val nativeBlockSnapDiscover: Flow<Boolean> = getBoolean(NATIVE_BLOCK_SNAP_DISCOVER, false)
    val nativeBlockSnapStories: Flow<Boolean> = getBoolean(NATIVE_BLOCK_SNAP_STORIES, false)
    val nativeBlockTiktok: Flow<Boolean> = getBoolean(NATIVE_BLOCK_TIKTOK, true)
    val nativeBlockTwitterVideo: Flow<Boolean> = getBoolean(NATIVE_BLOCK_TWITTER_VIDEO, true)
    val nativeBlockRedditVideo: Flow<Boolean> = getBoolean(NATIVE_BLOCK_REDDIT_VIDEO, true)
    val nativeBlockLinkedinVideo: Flow<Boolean> = getBoolean(NATIVE_BLOCK_LINKEDIN_VIDEO, true)
    val nativeBlockWhatsAppStatus: Flow<Boolean> = getBoolean(NATIVE_BLOCK_WHATSAPP_STATUS, false)
    val nativeBlockBrowserShorts: Flow<Boolean> = getBoolean(NATIVE_BLOCK_BROWSER_SHORTS, true)
    val nativeBlockedCount: Flow<Int> = getInt(NATIVE_BLOCKED_COUNT, 0)

    // NoScroll Plan & Modes flows
    val blockerPlan: Flow<String> = getString(BLOCKER_PLAN, BlockerPlan.BLOCK_ALL.id)
    val planBeforePause: Flow<String> = getString(PLAN_BEFORE_PAUSE, BlockerPlan.BLOCK_ALL.id)
    val blockActionMode: Flow<String> = getString(BLOCK_ACTION_MODE, BlockActionMode.PRESS_BACK.id)

    val curiousSessionMinutes: Flow<Int> = getInt(CURIOUS_SESSION_MINUTES, 5)
    val curiousCooldownMinutes: Flow<Int> = getInt(CURIOUS_COOLDOWN_MINUTES, 2)
    val curiousSessionStartMs: Flow<Long> = getLong(CURIOUS_SESSION_START_MS, 0L)
    val curiousCooldownUntilMs: Flow<Long> = getLong(CURIOUS_COOLDOWN_UNTIL_MS, 0L)
    val curiousLockSwitchInCooldown: Flow<Boolean> = getBoolean(CURIOUS_LOCK_SWITCH_IN_COOLDOWN, true)
    val curiousAllowOneInCooldown: Flow<Boolean> = getBoolean(CURIOUS_ALLOW_ONE_IN_COOLDOWN, false)

    val pauseUntilMs: Flow<Long> = getLong(PAUSE_UNTIL_MS, 0L)
    val pauseDurationMinutes: Flow<Int> = getInt(PAUSE_DURATION_MINUTES, 10)
    val pauseWaitSeconds: Flow<Int> = getInt(PAUSE_WAIT_SECONDS, 10)
    val pauseCooldownMinutes: Flow<Int> = getInt(PAUSE_COOLDOWN_MINUTES, 5)
    val pauseCooldownUntilMs: Flow<Long> = getLong(PAUSE_COOLDOWN_UNTIL_MS, 0L)

    val reelCounterEnabled: Flow<Boolean> = getBoolean(REEL_COUNTER_ENABLED, true)
    val reelCounterDetailed: Flow<Boolean> = getBoolean(REEL_COUNTER_DETAILED, true)
    val reelCounterHaptics: Flow<Boolean> = getBoolean(REEL_COUNTER_HAPTICS, true)
    val blockHaptics: Flow<Boolean> = getBoolean(BLOCK_HAPTICS, true)

    val blockedAppsJson: Flow<String> = getString(BLOCKED_APPS_JSON, "[]")
    val blockedSitesJson: Flow<String> = getString(BLOCKED_SITES_JSON, "[]")
    val pinType: Flow<String> = getString(PIN_TYPE, PinType.NONE.id)
    val customPin: Flow<String> = getString(CUSTOM_PIN, "")
    val pinProtectSettings: Flow<Boolean> = getBoolean(PIN_PROTECT_SETTINGS, true)
    val pinProtectPlanSwitch: Flow<Boolean> = getBoolean(PIN_PROTECT_PLAN_SWITCH, true)
    val pinProtectAppLocks: Flow<Boolean> = getBoolean(PIN_PROTECT_APP_LOCKS, true)
    val pinProtectSystemSettings: Flow<Boolean> = getBoolean(PIN_PROTECT_SYSTEM_SETTINGS, false)

    val todayBlocksCount: Flow<Int> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        if (prefs[TODAY_BLOCKS_DATE] == today) prefs[TODAY_BLOCKS_COUNT] ?: 0 else 0
    }
    val todayReelsScrolled: Flow<Int> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        if (prefs[TODAY_BLOCKS_DATE] == today) prefs[TODAY_REELS_SCROLLED] ?: 0 else 0
    }
    val blockHistoryJson: Flow<String> = getString(BLOCK_HISTORY_JSON, "[]")

    // YouTube flows
    val ytBlockAds: Flow<Boolean> = getBoolean(YT_BLOCK_ADS, true)
    val ytHideShorts: Flow<Boolean> = getBoolean(YT_HIDE_SHORTS, true)
    val ytHideComments: Flow<Boolean> = getBoolean(YT_HIDE_COMMENTS, false)
    val ytHideRecommendations: Flow<Boolean> = getBoolean(YT_HIDE_RECOMMENDATIONS, false)
    val ytBackgroundPlay: Flow<Boolean> = getBoolean(YT_BACKGROUND_PLAY, true)
    val ytSearchOnly: Flow<Boolean> = getBoolean(YT_SEARCH_ONLY, false)
    val ytBlockFeedAutoplay: Flow<Boolean> = getBoolean(YT_BLOCK_FEED_AUTOPLAY, false)

    // Instagram flows
    val igHideAds: Flow<Boolean> = getBoolean(IG_HIDE_ADS, true)
    val igHideSuggested: Flow<Boolean> = getBoolean(IG_HIDE_SUGGESTED, true)
    val igBlockReels: Flow<Boolean> = getBoolean(IG_BLOCK_REELS, true)
    val igHideComments: Flow<Boolean> = getBoolean(IG_HIDE_COMMENTS, false)
    val igBlockPosting: Flow<Boolean> = getBoolean(IG_BLOCK_POSTING, false)
    val igReelsBudget: Flow<Int> = getInt(IG_REELS_BUDGET, 30)

    // TikTok flows
    val ttBlockExplore: Flow<Boolean> = getBoolean(TT_BLOCK_EXPLORE, true)
    val ttBlockLive: Flow<Boolean> = getBoolean(TT_BLOCK_LIVE, true)
    val ttBlockShop: Flow<Boolean> = getBoolean(TT_BLOCK_SHOP, true)
    val ttBlockUpload: Flow<Boolean> = getBoolean(TT_BLOCK_UPLOAD, true)
    val ttBlockSearch: Flow<Boolean> = getBoolean(TT_BLOCK_SEARCH, false)
    val ttBlockComments: Flow<Boolean> = getBoolean(TT_BLOCK_COMMENTS, false)

    // Twitter flows
    val twHideForYou: Flow<Boolean> = getBoolean(TW_HIDE_FOR_YOU, true)
    val twHideAds: Flow<Boolean> = getBoolean(TW_HIDE_ADS, true)

    // Facebook flows
    val fbBlockReels: Flow<Boolean> = getBoolean(FB_BLOCK_REELS, true)
    val fbHidePeople: Flow<Boolean> = getBoolean(FB_HIDE_PEOPLE, true)
    val fbHideMarketplace: Flow<Boolean> = getBoolean(FB_HIDE_MARKETPLACE, false)

    // Reddit flows
    val redditBlockAds: Flow<Boolean> = getBoolean(REDDIT_BLOCK_ADS, true)
    val redditHideSuggested: Flow<Boolean> = getBoolean(REDDIT_HIDE_SUGGESTED, true)
    val redditBlockPopular: Flow<Boolean> = getBoolean(REDDIT_BLOCK_POPULAR, false)

    // General & Sleep Mode flows
    val grayscaleMode: Flow<Boolean> = getBoolean(GRAYSCALE_MODE, false)
    val sleepModeEnabled: Flow<Boolean> = getBoolean(SLEEP_MODE_ENABLED, false)
    val sleepStartHour: Flow<Int> = getInt(SLEEP_START_HOUR, 22)
    val sleepStartMinute: Flow<Int> = getInt(SLEEP_START_MINUTE, 0)
    val sleepEndHour: Flow<Int> = getInt(SLEEP_END_HOUR, 7)
    val sleepEndMinute: Flow<Int> = getInt(SLEEP_END_MINUTE, 0)
}
