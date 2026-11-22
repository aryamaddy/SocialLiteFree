package com.socialitefree.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.socialitefree.app.data.AppSettings
import com.socialitefree.app.data.Platform
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.service.BackgroundPlayService
import com.socialitefree.app.webview.SocialWebChromeClient
import com.socialitefree.app.webview.SocialWebViewClient

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    platformId: String,
    prefsManager: PreferencesManager,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val platform = remember(platformId) { Platform.fromId(platformId) ?: Platform.YOUTUBE }

    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf(platform.displayName) }

    // Collect settings to pass into WebView client
    val ytBlockAds by prefsManager.ytBlockAds.collectAsState(initial = true)
    val ytHideShorts by prefsManager.ytHideShorts.collectAsState(initial = true)
    val ytHideComments by prefsManager.ytHideComments.collectAsState(initial = false)
    val ytHideRecs by prefsManager.ytHideRecommendations.collectAsState(initial = true)
    val ytBgPlay by prefsManager.ytBackgroundPlay.collectAsState(initial = true)
    val ytSearchOnly by prefsManager.ytSearchOnly.collectAsState(initial = false)
    val ytBlockFeedAutoplay by prefsManager.ytBlockFeedAutoplay.collectAsState(initial = true)

    val igHideAds by prefsManager.igHideAds.collectAsState(initial = true)
    val igHideSuggested by prefsManager.igHideSuggested.collectAsState(initial = true)
    val igHideComments by prefsManager.igHideComments.collectAsState(initial = false)
    val igBlockPosting by prefsManager.igBlockPosting.collectAsState(initial = false)
    val igReelsBudget by prefsManager.igReelsBudget.collectAsState(initial = 30)

    val ttBlockExplore by prefsManager.ttBlockExplore.collectAsState(initial = true)
    val ttBlockLive by prefsManager.ttBlockLive.collectAsState(initial = true)
    val ttBlockShop by prefsManager.ttBlockShop.collectAsState(initial = true)
    val ttBlockUpload by prefsManager.ttBlockUpload.collectAsState(initial = true)
    val ttBlockSearch by prefsManager.ttBlockSearch.collectAsState(initial = false)
    val ttBlockComments by prefsManager.ttBlockComments.collectAsState(initial = false)

    val twHideForYou by prefsManager.twHideForYou.collectAsState(initial = true)
    val twHideAds by prefsManager.twHideAds.collectAsState(initial = true)
    val grayscale by prefsManager.grayscaleMode.collectAsState(initial = false)

    val currentSettings = remember(
        ytBlockAds, ytHideShorts, ytHideComments, ytHideRecs, ytBgPlay, ytSearchOnly, ytBlockFeedAutoplay,
        igHideAds, igHideSuggested, igHideComments, igBlockPosting, igReelsBudget,
        ttBlockExplore, ttBlockLive, ttBlockShop, ttBlockUpload, ttBlockSearch, ttBlockComments,
        twHideForYou, twHideAds, grayscale
    ) {
        AppSettings(
            ytBlockAds = ytBlockAds,
            ytHideShorts = ytHideShorts,
            ytHideComments = ytHideComments,
            ytHideRecommendations = ytHideRecs,
            ytBackgroundPlay = ytBgPlay,
            ytSearchOnly = ytSearchOnly,
            ytBlockFeedAutoplay = ytBlockFeedAutoplay,
            igHideAds = igHideAds,
            igHideSuggested = igHideSuggested,
            igHideComments = igHideComments,
            igBlockPosting = igBlockPosting,
            igReelsBudget = igReelsBudget,
            ttBlockExplore = ttBlockExplore,
            ttBlockLive = ttBlockLive,
            ttBlockShop = ttBlockShop,
            ttBlockUpload = ttBlockUpload,
            ttBlockSearch = ttBlockSearch,
            ttBlockComments = ttBlockComments,
            twHideForYou = twHideForYou,
            twHideAds = twHideAds,
            grayscaleMode = grayscale
        )
    }

    // Handle Android system back press
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onNavigateHome()
        }
    }

    // Manage BackgroundPlayService for YouTube
    DisposableEffect(platform, ytBgPlay) {
        if (platform == Platform.YOUTUBE && ytBgPlay) {
            BackgroundPlayService.start(context, "YouTube Audio Playing")
        }
        onDispose {
            if (platform == Platform.YOUTUBE) {
                BackgroundPlayService.stop(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${platform.iconEmoji} $currentTitle",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onNavigateHome()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        }

                        // Add JS Bridge for Reels timer & notices
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun reelsBudgetExceeded() {
                                post {
                                    Toast.makeText(ctx, "Daily Reels limit reached!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }, "SocialLiteFree")

                        webViewClient = SocialWebViewClient(
                            context = ctx,
                            platform = platform,
                            getSettings = { currentSettings },
                            onPageFinishedCallback = { url ->
                                canGoBack = canGoBack()
                                title?.let { currentTitle = it }
                            }
                        )

                        webChromeClient = SocialWebChromeClient(
                            onProgressChangedCallback = { p -> progress = p }
                        )

                        loadUrl(platform.url)
                        webView = this
                    }
                },
                update = { view ->
                    canGoBack = view.canGoBack()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
