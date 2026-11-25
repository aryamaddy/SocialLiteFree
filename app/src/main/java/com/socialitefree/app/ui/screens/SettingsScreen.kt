package com.socialitefree.app.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialitefree.app.R
import com.socialitefree.app.data.Platform
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberAppSettings(prefsManager)

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var expandedPlatformId by remember { mutableStateOf<String?>("youtube") }

    Box(modifier = Modifier.fillMaxSize()) {
        // Authentic warm settings background (settings_bg.webp)
        Image(
            painter = painterResource(id = R.drawable.settings_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar matching tut_settings.webp ("Settings" centered + pill "Done" button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = CreamCard.copy(alpha = 0.85f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                ) {
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Pro Status Banner Card (matching tut_settings.webp)
                item {
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SocialPurple)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SocialLite Pro",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "All features unlocked · 100% Free",
                                    fontSize = 12.sp,
                                    color = InkSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SocialPurpleSoft
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SocialPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 2. App Icons Style (Painted 3D vs Classic)
                item {
                    SettingsSectionHeader(Icons.Rounded.Palette, "APP ICONS")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            IconStyleOption(
                                label = "Painted 3D",
                                iconRes = R.drawable.ic_instagram_painted,
                                selected = !settings.useClassicIcons,
                                modifier = Modifier.weight(1f)
                            ) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.USE_CLASSIC_ICONS, false) }
                            }
                            IconStyleOption(
                                label = "Classic",
                                iconRes = R.drawable.ic_instagram_classic,
                                selected = settings.useClassicIcons,
                                modifier = Modifier.weight(1f)
                            ) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.USE_CLASSIC_ICONS, true) }
                            }
                        }
                    }
                }

                // 3. Auto App Launch (matching tut_settings.webp)
                item {
                    SettingsSectionHeader(Icons.Rounded.Bolt, "AUTO APP LAUNCH")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        Column {
                            SettingsSwitchRow(
                                title = "Auto App Launch",
                                checked = settings.autoAppLaunch
                            ) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.AUTO_APP_LAUNCH, it) }
                            }
                            if (settings.autoAppLaunch) {
                                HairlineDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFavoritePicker = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Favorite App", fontSize = 14.sp, color = InkPrimary)
                                    val fav = Platform.fromId(settings.favoritePlatformId) ?: Platform.YOUTUBE
                                    Text(
                                        text = "${fav.displayName} ▾",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SocialPurple
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Automatically open your favorite app every time you launch SocialLite.",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
                    )
                }

                // 4. General Well-Being
                item {
                    SettingsSectionHeader(Icons.Rounded.Spa, "WELL-BEING")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        SettingsSwitchRow(
                            title = "Grayscale Mode",
                            subtitle = "Makes feeds less stimulating across all apps",
                            checked = settings.grayscaleMode
                        ) {
                            coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.GRAYSCALE_MODE, it) }
                        }
                    }
                }

                // 5. Visible Apps on Home Grid
                item {
                    SettingsSectionHeader(Icons.Rounded.Apps, "HOME GRID APPS")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        Column {
                            Platform.entries.forEachIndexed { index, platform ->
                                val isVisible by prefsManager.isPlatformVisible(platform)
                                    .collectAsState(initial = platform.defaultVisible)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = platform.iconRes(settings.useClassicIcons)),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = platform.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = InkPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = isVisible,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch {
                                                prefsManager.setBoolean(
                                                    PreferencesManager.showPlatformKey(platform.id),
                                                    checked
                                                )
                                            }
                                        }
                                    )
                                }
                                if (index < Platform.entries.lastIndex) HairlineDivider()
                            }
                        }
                    }
                }

                // 6. Platform Filter Controls
                item {
                    SettingsSectionHeader(Icons.Rounded.Tune, "CONTENT FILTERS")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        Column {
                            // YouTube
                            PlatformFilterAccordion(
                                platform = Platform.YOUTUBE,
                                expanded = expandedPlatformId == Platform.YOUTUBE.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.YOUTUBE.id) null else Platform.YOUTUBE.id
                                }
                            ) {
                                SettingsSwitchRow("Block Video & Feed Ads", "Auto-skips and hides sponsored ads", settings.ytBlockAds) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BLOCK_ADS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide YouTube Shorts", "Removes Shorts shelves & tab", settings.ytHideShorts) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_SHORTS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Background Playback", "Keeps audio playing with screen off", settings.ytBackgroundPlay) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BACKGROUND_PLAY, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Recommendations", "Hides related videos below player", settings.ytHideRecommendations) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_RECOMMENDATIONS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Comments", "Keeps comments collapsed/hidden", settings.ytHideComments) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_COMMENTS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Search-Only Mode", "Hides homepage completely", settings.ytSearchOnly) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_SEARCH_ONLY, it) }
                                }
                            }

                            HairlineDivider()

                            // Instagram
                            PlatformFilterAccordion(
                                platform = Platform.INSTAGRAM,
                                expanded = expandedPlatformId == Platform.INSTAGRAM.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.INSTAGRAM.id) null else Platform.INSTAGRAM.id
                                }
                            ) {
                                SettingsSwitchRow("Hide Ads & Sponsored", "Filters sponsored feed units", settings.igHideAds) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_ADS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Suggested Posts", "Stops 'Suggested for you' feed clutter", settings.igHideSuggested) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_SUGGESTED, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Block Reels", "Hides Reels tab & blocks infinite loops", settings.igBlockReels) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_BLOCK_REELS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Comments", "Hides comment sections", settings.igHideComments) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_COMMENTS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Block Post Creation", "Disables create/upload buttons", settings.igBlockPosting) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_BLOCK_POSTING, it) }
                                }
                            }

                            HairlineDivider()

                            // X / Twitter
                            PlatformFilterAccordion(
                                platform = Platform.TWITTER,
                                expanded = expandedPlatformId == Platform.TWITTER.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.TWITTER.id) null else Platform.TWITTER.id
                                }
                            ) {
                                SettingsSwitchRow("Hide 'For You' Tab", "Force Following chronological feed", settings.twHideForYou) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_FOR_YOU, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Promoted Posts", "Removes sponsored ads in X feed", settings.twHideAds) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_ADS, it) }
                                }
                            }

                            HairlineDivider()

                            // TikTok
                            PlatformFilterAccordion(
                                platform = Platform.TIKTOK,
                                expanded = expandedPlatformId == Platform.TIKTOK.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.TIKTOK.id) null else Platform.TIKTOK.id
                                }
                            ) {
                                SettingsSwitchRow("Block Explore", null, settings.ttBlockExplore) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_EXPLORE, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Block Live", null, settings.ttBlockLive) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_LIVE, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Block Shop", null, settings.ttBlockShop) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_SHOP, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Block Upload", null, settings.ttBlockUpload) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_UPLOAD, it) }
                                }
                            }

                            HairlineDivider()

                            // Facebook
                            PlatformFilterAccordion(
                                platform = Platform.FACEBOOK,
                                expanded = expandedPlatformId == Platform.FACEBOOK.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.FACEBOOK.id) null else Platform.FACEBOOK.id
                                }
                            ) {
                                SettingsSwitchRow("Block Reels", "Blocks Facebook Reels feed", settings.fbBlockReels) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_BLOCK_REELS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide People You May Know", null, settings.fbHidePeople) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_HIDE_PEOPLE, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Marketplace", null, settings.fbHideMarketplace) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_HIDE_MARKETPLACE, it) }
                                }
                            }

                            HairlineDivider()

                            // Reddit
                            PlatformFilterAccordion(
                                platform = Platform.REDDIT,
                                expanded = expandedPlatformId == Platform.REDDIT.id,
                                onToggle = {
                                    expandedPlatformId = if (expandedPlatformId == Platform.REDDIT.id) null else Platform.REDDIT.id
                                }
                            ) {
                                SettingsSwitchRow("Block Promoted Posts", null, settings.redditBlockAds) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.REDDIT_BLOCK_ADS, it) }
                                }
                                HairlineDivider()
                                SettingsSwitchRow("Hide Suggested Communities", null, settings.redditHideSuggested) {
                                    coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.REDDIT_HIDE_SUGGESTED, it) }
                                }
                            }
                        }
                    }
                }

                // 7. Data & About (matching tut_settings.webp)
                item {
                    SettingsSectionHeader(Icons.Rounded.Info, "DATA & ABOUT")
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsCard {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearDataDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = SocialAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Clear All Data",
                                    fontSize = 15.sp,
                                    color = InkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = InkTertiary
                                )
                            }
                            HairlineDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text("Version", fontSize = 15.sp, color = InkPrimary)
                                Text("1.3.1 (Free)", fontSize = 15.sp, color = InkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Browsing Data?") },
            text = { Text("This will log you out of social sites inside SocialLite and clear cached pages and cookies.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        showClearDataDialog = false
                        Toast.makeText(context, "All browsing data cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = SocialCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFavoritePicker) {
        AlertDialog(
            onDismissRequest = { showFavoritePicker = false },
            title = { Text("Select Auto-Launch App") },
            text = {
                Column {
                    Platform.entries.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        prefsManager.setString(PreferencesManager.DEFAULT_PLATFORM, p.id)
                                    }
                                    showFavoritePicker = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.favoritePlatformId == p.id,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(p.displayName, fontSize = 15.sp, color = InkPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavoritePicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = InkSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = InkSecondary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CreamCard.copy(alpha = 0.90f),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun HairlineDivider() {
    Divider(
        color = CreamDivider.copy(alpha = 0.7f),
        thickness = 0.6.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun IconStyleOption(
    label: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SocialPurpleSoft else CreamCardSubtle.copy(alpha = 0.6f),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) SocialPurple else InkPrimary
            )
        }
    }
}

@Composable
private fun PlatformFilterAccordion(
    platform: Platform,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Image(
                painter = painterResource(id = platform.gearIconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = platform.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = InkSecondary
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CreamSurface.copy(alpha = 0.6f))
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = InkPrimary)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = InkSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
