package com.socialitefree.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialitefree.app.data.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // YouTube states
    val ytBlockAds by prefsManager.ytBlockAds.collectAsState(initial = true)
    val ytHideShorts by prefsManager.ytHideShorts.collectAsState(initial = true)
    val ytHideComments by prefsManager.ytHideComments.collectAsState(initial = false)
    val ytHideRecs by prefsManager.ytHideRecommendations.collectAsState(initial = true)
    val ytBgPlay by prefsManager.ytBackgroundPlay.collectAsState(initial = true)
    val ytSearchOnly by prefsManager.ytSearchOnly.collectAsState(initial = false)
    val ytBlockFeedAutoplay by prefsManager.ytBlockFeedAutoplay.collectAsState(initial = true)

    // Instagram states
    val igHideAds by prefsManager.igHideAds.collectAsState(initial = true)
    val igHideSuggested by prefsManager.igHideSuggested.collectAsState(initial = true)
    val igHideComments by prefsManager.igHideComments.collectAsState(initial = false)
    val igBlockPosting by prefsManager.igBlockPosting.collectAsState(initial = false)
    val igReelsBudget by prefsManager.igReelsBudget.collectAsState(initial = 30)

    // TikTok states
    val ttBlockExplore by prefsManager.ttBlockExplore.collectAsState(initial = true)
    val ttBlockLive by prefsManager.ttBlockLive.collectAsState(initial = true)
    val ttBlockShop by prefsManager.ttBlockShop.collectAsState(initial = true)
    val ttBlockUpload by prefsManager.ttBlockUpload.collectAsState(initial = true)
    val ttBlockSearch by prefsManager.ttBlockSearch.collectAsState(initial = false)
    val ttBlockComments by prefsManager.ttBlockComments.collectAsState(initial = false)

    // Twitter states
    val twHideForYou by prefsManager.twHideForYou.collectAsState(initial = true)
    val twHideAds by prefsManager.twHideAds.collectAsState(initial = true)

    // General states
    val grayscale by prefsManager.grayscaleMode.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings & Controls", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Section
            item {
                SectionHeader("📱 General Well-Being")
                SwitchRow("Grayscale Mode", "Reduces screen addiction and visual stimulation", grayscale) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.GRAYSCALE_MODE, it) }
                }
            }

            // YouTube Section
            item {
                SectionHeader("▶️ YouTube Controls")
                SwitchRow("Block Video & Feed Ads", "Strips video ads, banners and auto-skips", ytBlockAds) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BLOCK_ADS, it) }
                }
                SwitchRow("Hide YouTube Shorts", "Completely removes addictive short videos", ytHideShorts) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_SHORTS, it) }
                }
                SwitchRow("Background Playback", "Keeps audio playing when screen is locked/off", ytBgPlay) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BACKGROUND_PLAY, it) }
                }
                SwitchRow("Hide Recommendations", "Hides related videos below player", ytHideRecs) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_RECOMMENDATIONS, it) }
                }
                SwitchRow("Block Feed Autoplay", "Prevents videos from auto-playing in feed", ytBlockFeedAutoplay) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BLOCK_FEED_AUTOPLAY, it) }
                }
                SwitchRow("Hide Comments", "Keeps comment section hidden", ytHideComments) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_COMMENTS, it) }
                }
                SwitchRow("Search-Only Mode", "Hides homepage entirely to avoid doom-scrolling", ytSearchOnly) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_SEARCH_ONLY, it) }
                }
            }

            // Instagram Section
            item {
                SectionHeader("📸 Instagram Controls")
                SwitchRow("Hide Sponsored & Ads", "Filters out sponsored posts from timeline", igHideAds) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_ADS, it) }
                }
                SwitchRow("Hide Suggested Posts", "No netego units or 'suggested for you'", igHideSuggested) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_SUGGESTED, it) }
                }
                SwitchRow("Hide Comments", "Hides comments on posts and reels", igHideComments) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_COMMENTS, it) }
                }
                SwitchRow("Block Post Creation", "Disables uploading or creating new posts", igBlockPosting) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_BLOCK_POSTING, it) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reels Daily Budget: $igReelsBudget mins",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = igReelsBudget.toFloat(),
                    onValueChange = {
                        coroutineScope.launch { prefsManager.setInt(PreferencesManager.IG_REELS_BUDGET, it.toInt()) }
                    },
                    valueRange = 0f..60f,
                    steps = 11
                )
            }

            // TikTok Section
            item {
                SectionHeader("🎵 TikTok Controls")
                SwitchRow("Block Explore & Discover", "Hides trending/viral exploration", ttBlockExplore) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_EXPLORE, it) }
                }
                SwitchRow("Block Live Streams", "Restricts live broadcasts", ttBlockLive) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_LIVE, it) }
                }
                SwitchRow("Block TikTok Shop", "Hides product shopping features", ttBlockShop) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_SHOP, it) }
                }
                SwitchRow("Block Upload / Camera", "Disables posting TikTok videos", ttBlockUpload) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_UPLOAD, it) }
                }
                SwitchRow("Block Comments", "Hides comment sheet and counts", ttBlockComments) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_COMMENTS, it) }
                }
            }

            // Twitter Section
            item {
                SectionHeader("🐦 X / Twitter Controls")
                SwitchRow("Force 'Following' Tab", "Hides algorithmic 'For You' timeline", twHideForYou) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_FOR_YOU, it) }
                }
                SwitchRow("Hide Promoted Tweets", "Removes paid/sponsored tweets", twHideAds) {
                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_ADS, it) }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
