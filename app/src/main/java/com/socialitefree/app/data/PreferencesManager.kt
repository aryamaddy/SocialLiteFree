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
        val NATIVE_BLOCK_YT_SHORTS = booleanPreferencesKey("native_block_yt_shorts")
        val NATIVE_BLOCK_FB_REELS = booleanPreferencesKey("native_block_fb_reels")
        val NATIVE_BLOCK_SNAP_SPOTLIGHT = booleanPreferencesKey("native_block_snap_spotlight")
        val NATIVE_BLOCKED_COUNT = intPreferencesKey("native_blocked_count")
        val LAST_NOTIFIED_UPDATE_CODE = intPreferencesKey("last_notified_update_code")

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

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
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
        context.dataStore.edit { prefs ->
            prefs[NATIVE_BLOCKED_COUNT] = (prefs[NATIVE_BLOCKED_COUNT] ?: 0) + 1
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
    val nativeBlockYtShorts: Flow<Boolean> = getBoolean(NATIVE_BLOCK_YT_SHORTS, true)
    val nativeBlockFbReels: Flow<Boolean> = getBoolean(NATIVE_BLOCK_FB_REELS, true)
    val nativeBlockSnapSpotlight: Flow<Boolean> = getBoolean(NATIVE_BLOCK_SNAP_SPOTLIGHT, true)
    val nativeBlockedCount: Flow<Int> = getInt(NATIVE_BLOCKED_COUNT, 0)

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
