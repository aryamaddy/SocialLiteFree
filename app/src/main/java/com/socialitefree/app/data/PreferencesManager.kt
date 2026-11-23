package com.socialitefree.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

        // Facebook keys (no specific toggles requested, but adding basic ones)
        val FB_HIDE_ADS = booleanPreferencesKey("fb_hide_ads")

        // General keys
        val GRAYSCALE_MODE = booleanPreferencesKey("grayscale_mode")
        val DEFAULT_PLATFORM = stringPreferencesKey("default_platform")
    }

    // Generic boolean getter
    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = true): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    // Generic int getter
    fun getInt(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    // Generic string getter
    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[key] ?: default }

    // Generic boolean setter
    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    // Generic int setter
    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    // Generic string setter
    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    // YouTube flows
    val ytBlockAds: Flow<Boolean> = getBoolean(YT_BLOCK_ADS, true)
    val ytHideShorts: Flow<Boolean> = getBoolean(YT_HIDE_SHORTS, true)
    val ytHideComments: Flow<Boolean> = getBoolean(YT_HIDE_COMMENTS, false)
    val ytHideRecommendations: Flow<Boolean> = getBoolean(YT_HIDE_RECOMMENDATIONS, false)
    val ytBackgroundPlay: Flow<Boolean> = getBoolean(YT_BACKGROUND_PLAY, true)
    val ytSearchOnly: Flow<Boolean> = getBoolean(YT_SEARCH_ONLY, false)
    val ytBlockFeedAutoplay: Flow<Boolean> = getBoolean(YT_BLOCK_FEED_AUTOPLAY, true)

    // Instagram flows
    val igHideAds: Flow<Boolean> = getBoolean(IG_HIDE_ADS, true)
    val igHideSuggested: Flow<Boolean> = getBoolean(IG_HIDE_SUGGESTED, true)
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
    val fbHideAds: Flow<Boolean> = getBoolean(FB_HIDE_ADS, true)

    // General flows
    val grayscaleMode: Flow<Boolean> = getBoolean(GRAYSCALE_MODE, false)
    val defaultPlatform: Flow<String> = getString(DEFAULT_PLATFORM, "")

    // Convenience: get all preferences as a snapshot
    val allPreferences: Flow<Preferences> = context.dataStore.data
}
