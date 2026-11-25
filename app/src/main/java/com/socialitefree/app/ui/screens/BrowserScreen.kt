package com.socialitefree.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.socialitefree.app.R
import com.socialitefree.app.data.AppSettings
import com.socialitefree.app.data.Platform
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.service.BackgroundPlayService
import com.socialitefree.app.ui.theme.*
import com.socialitefree.app.webview.SocialWebChromeClient
import com.socialitefree.app.webview.SocialWebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    platformId: String,
    prefsManager: PreferencesManager,
    onNavigateHome: () -> Unit,
    onSwitchPlatform: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val platform = remember(platformId) { Platform.fromId(platformId) ?: Platform.YOUTUBE }
    val currentSettings = rememberAppSettings(prefsManager)

    // Keep latest settings ref for WebViewClient lambdas
    val latestSettings by rememberUpdatedState(currentSettings)

    var webView: WebView? by remember { mutableStateOf(null) }
    var webClientRef: SocialWebViewClient? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    // Floating Menu & Sheets state
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlatformSettingsSheet by remember { mutableStateOf(false) }
    var showSwitchAppSheet by remember { mutableStateOf(false) }

    // Sleep Mode gate check
    var sleepBypassUntilMs by remember { mutableStateOf(0L) }
    val nowTime = remember(platformId) { LocalTime.now() }
    val isSleepBlocked = currentSettings.isInSleepWindow(nowTime.hour, nowTime.minute) &&
        System.currentTimeMillis() > sleepBypassUntilMs

    // Live Usage Time Tracking (records every 5s while viewing)
    LaunchedEffect(platform.id, isSleepBlocked) {
        if (!isSleepBlocked) {
            while (true) {
                delay(5000)
                prefsManager.addUsageSeconds(platform.id, 5)
            }
        }
    }

    // Re-inject JS whenever settings change while inside browser
    LaunchedEffect(currentSettings) {
        webView?.let { wv ->
            webClientRef?.reinjectScripts(wv)
        }
    }

    // Handle Android system back press
    BackHandler(enabled = true) {
        when {
            menuExpanded -> menuExpanded = false
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onNavigateHome()
        }
    }

    // Manage BackgroundPlayService for YouTube
    DisposableEffect(platform, currentSettings.ytBackgroundPlay) {
        if (platform == Platform.YOUTUBE && currentSettings.ytBackgroundPlay) {
            BackgroundPlayService.start(context, "YouTube Audio Playing")
        }
        onDispose {
            if (platform == Platform.YOUTUBE) {
                BackgroundPlayService.stop(context)
            }
        }
    }

    if (isSleepBlocked) {
        SleepGateOverlay(
            platform = platform,
            onGoHome = onNavigateHome,
            onTemporaryUnlock = {
                sleepBypassUntilMs = System.currentTimeMillis() + 5 * 60 * 1000L
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Full-Screen WebView (keyed by platform.id so switching platforms cleanly initializes new WebView)
        key(platform.id) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            javaScriptCanOpenWindowsAutomatically = true

                            val defaultUa = userAgentString
                            userAgentString = defaultUa.replace("; wv", "")
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun postMessage(msg: String) {
                                android.util.Log.d("YouTubeBridge", "Message: $msg")
                            }
                        }, "__NS__yt")

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun reelsBudgetExceeded() {
                                post {
                                    Toast.makeText(ctx, "Daily Reels limit reached!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }, "SocialLiteFree")

                        val client = SocialWebViewClient(
                            context = ctx,
                            platform = platform,
                            getSettings = { latestSettings },
                            onPageFinishedCallback = {
                                canGoBack = canGoBack()
                                canGoForward = canGoForward()
                            }
                        )
                        webClientRef = client
                        webViewClient = client

                        webChromeClient = SocialWebChromeClient(
                            onProgressChangedCallback = { p -> progress = p }
                        )

                        loadUrl(platform.url)
                        webView = this
                    }
                },
                update = { view ->
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle thin top progress indicator only while loading
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = progress / 100f,
                color = SocialPurple,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // Tap-outside scrim when floating pill is open
        if (menuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { menuExpanded = false }
                    )
            )
        }

        // Floating Translucent Pill Toolbar + ≡ Toggle Button (matching tut_ig_feed.webp & tut_ig_menu.webp)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
        ) {
            AnimatedVisibility(
                visible = menuExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.88f)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xEBCDC7BE),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        PillIconButton(
                            icon = Icons.Rounded.ChevronLeft,
                            enabled = canGoBack,
                            contentDescription = "Back"
                        ) {
                            webView?.goBack()
                            menuExpanded = false
                        }
                        PillIconButton(
                            icon = Icons.Rounded.Home,
                            enabled = true,
                            contentDescription = "Home"
                        ) {
                            menuExpanded = false
                            onNavigateHome()
                        }
                        PillIconButton(
                            icon = Icons.Rounded.Refresh,
                            enabled = true,
                            contentDescription = "Reload"
                        ) {
                            webView?.reload()
                            menuExpanded = false
                        }
                        PillIconButton(
                            icon = Icons.Rounded.ChevronRight,
                            enabled = canGoForward,
                            contentDescription = "Forward"
                        ) {
                            webView?.goForward()
                            menuExpanded = false
                        }
                        PillIconButton(
                            icon = Icons.Rounded.Apps,
                            enabled = true,
                            contentDescription = "Switch App"
                        ) {
                            menuExpanded = false
                            showSwitchAppSheet = true
                        }
                        PillIconButton(
                            icon = Icons.Rounded.Settings,
                            enabled = true,
                            contentDescription = "Platform Settings"
                        ) {
                            menuExpanded = false
                            showPlatformSettingsSheet = true
                        }
                    }
                }
            }

            // Floating Circular Menu Button (≡ / ✕)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xE0D4CEC5))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable { menuExpanded = !menuExpanded }
            ) {
                Icon(
                    imageVector = if (menuExpanded) Icons.Rounded.Close else Icons.Rounded.Menu,
                    contentDescription = if (menuExpanded) "Close Menu" else "Open Menu",
                    tint = InkPrimary,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }

    // Per-Platform Settings Bottom Sheet (live controls right inside the browser)
    if (showPlatformSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlatformSettingsSheet = false },
            containerColor = CreamBackground
        ) {
            InBrowserPlatformSettingsSheet(
                platform = platform,
                settings = currentSettings,
                prefsManager = prefsManager,
                onApplyReload = {
                    webClientRef?.reinjectScripts(webView)
                    webView?.reload()
                },
                onDismiss = { showPlatformSettingsSheet = false }
            )
        }
    }

    // Quick App Switcher Bottom Sheet
    if (showSwitchAppSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSwitchAppSheet = false },
            containerColor = CreamBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Switch Platform",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(Platform.entries) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                showSwitchAppSheet = false
                                if (item.id != platform.id) {
                                    onSwitchPlatform(item.id)
                                }
                            }
                        ) {
                            Image(
                                painter = painterResource(id = item.iconRes(currentSettings.useClassicIcons)),
                                contentDescription = item.displayName,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (item.id == platform.id) FontWeight.Bold else FontWeight.Medium,
                                color = if (item.id == platform.id) SocialPurple else InkPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillIconButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) InkPrimary else InkPrimary.copy(alpha = 0.28f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun InBrowserPlatformSettingsSheet(
    platform: Platform,
    settings: AppSettings,
    prefsManager: PreferencesManager,
    onApplyReload: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner Header (using settings_banner_youtube / instagram / x if available)
        item {
            val bannerRes = platform.settingsBannerRes
            if (bannerRes != null) {
                Image(
                    painter = painterResource(id = bannerRes),
                    contentDescription = "${platform.displayName} Settings",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = platform.gearIconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${platform.displayName} Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkPrimary
                        )
                        Text(
                            text = "Personalize your experience.",
                            fontSize = 13.sp,
                            color = InkSecondary
                        )
                    }
                }
            }
        }

        // Platform Specific Switches
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CreamCard
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    when (platform) {
                        Platform.YOUTUBE -> {
                            SheetSwitchRow("Block Ads", "Strip pre-roll & feed ads", settings.ytBlockAds) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BLOCK_ADS, it) }
                            }
                            SheetSwitchRow("Hide Shorts", "Remove Shorts shelves & tab", settings.ytHideShorts) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_SHORTS, it) }
                            }
                            SheetSwitchRow("Background Playback", "Keep audio playing when screen locks", settings.ytBackgroundPlay) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_BACKGROUND_PLAY, it) }
                            }
                            SheetSwitchRow("Hide Watch Recommendations", "Remove related videos below player", settings.ytHideRecommendations) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_RECOMMENDATIONS, it) }
                            }
                            SheetSwitchRow("Hide Comments", "Hide comment threads on watch page", settings.ytHideComments) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_HIDE_COMMENTS, it) }
                            }
                            SheetSwitchRow("Search-Only Mode", "Hide home feed completely", settings.ytSearchOnly) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.YT_SEARCH_ONLY, it) }
                            }
                        }
                        Platform.INSTAGRAM -> {
                            SheetSwitchRow("Hide Ads & Sponsored", "Filter sponsored posts from timeline", settings.igHideAds) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_ADS, it) }
                            }
                            SheetSwitchRow("Hide Suggested Posts", "Only show accounts you follow", settings.igHideSuggested) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_SUGGESTED, it) }
                            }
                            SheetSwitchRow("Block Reels", "Hide Reels tab & block reels feed", settings.igBlockReels) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_BLOCK_REELS, it) }
                            }
                            SheetSwitchRow("Hide Comments", "Hide comment boxes on posts", settings.igHideComments) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_HIDE_COMMENTS, it) }
                            }
                            SheetSwitchRow("Block Posting", "Hide create/upload buttons", settings.igBlockPosting) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.IG_BLOCK_POSTING, it) }
                            }
                        }
                        Platform.TWITTER -> {
                            SheetSwitchRow("Hide 'For You' Tab", "Force chronological Following timeline", settings.twHideForYou) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_FOR_YOU, it) }
                            }
                            SheetSwitchRow("Hide Promoted Posts", "Filter sponsored ads in timeline", settings.twHideAds) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TW_HIDE_ADS, it) }
                            }
                        }
                        Platform.TIKTOK -> {
                            SheetSwitchRow("Block Explore", "Hide Discover & Explore feeds", settings.ttBlockExplore) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_EXPLORE, it) }
                            }
                            SheetSwitchRow("Block Live", "Block Live streams", settings.ttBlockLive) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_LIVE, it) }
                            }
                            SheetSwitchRow("Block Shop", "Hide shopping & commerce tabs", settings.ttBlockShop) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_SHOP, it) }
                            }
                            SheetSwitchRow("Block Comments", "Hide comment drawers", settings.ttBlockComments) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.TT_BLOCK_COMMENTS, it) }
                            }
                        }
                        Platform.FACEBOOK -> {
                            SheetSwitchRow("Block Reels", "Redirect & hide Facebook Reels", settings.fbBlockReels) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_BLOCK_REELS, it) }
                            }
                            SheetSwitchRow("Hide People You May Know", "Clean friend suggestions", settings.fbHidePeople) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_HIDE_PEOPLE, it) }
                            }
                            SheetSwitchRow("Hide Marketplace", "Hide Marketplace tab", settings.fbHideMarketplace) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.FB_HIDE_MARKETPLACE, it) }
                            }
                        }
                        Platform.REDDIT -> {
                            SheetSwitchRow("Block Ads", "Remove promoted Reddit posts", settings.redditBlockAds) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.REDDIT_BLOCK_ADS, it) }
                            }
                            SheetSwitchRow("Hide Suggested", "Hide recommended communities", settings.redditHideSuggested) {
                                coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.REDDIT_HIDE_SUGGESTED, it) }
                            }
                        }
                        else -> {
                            Text(
                                text = "Distraction-free container active.",
                                fontSize = 14.sp,
                                color = InkSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }

                    SheetSwitchRow("Grayscale Mode", "Remove color across this page", settings.grayscaleMode) {
                        coroutineScope.launch { prefsManager.setBoolean(PreferencesManager.GRAYSCALE_MODE, it) }
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        onApplyReload()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Apply & Reload", fontWeight = FontWeight.SemiBold, color = InkPrimary)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SheetSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = InkPrimary)
            Text(subtitle, fontSize = 12.sp, color = InkSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SleepGateOverlay(
    platform: Platform,
    onGoHome: () -> Unit,
    onTemporaryUnlock: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sleep_mode_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = SleepMoonAccent,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sleep Mode Active",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${platform.displayName} is resting right now to protect your sleep.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(containerColor = SleepMoonAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Back to Home", fontWeight = FontWeight.Bold, color = SleepNightBg)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onTemporaryUnlock) {
                Text("Unlock for 5 minutes", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
