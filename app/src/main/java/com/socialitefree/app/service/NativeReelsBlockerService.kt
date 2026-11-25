package com.socialitefree.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.R
import com.socialitefree.app.data.*
import com.socialitefree.app.ui.AppLockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * Persistent Accessibility-powered blocker for native social apps, browsers & App Blocker (v4).
 *
 * Full NoScroll feature parity (100% free & ad-free):
 * - Detection Plans: BLOCK_ALL, CURIOUS (timed watch + cooldown), ONE_REEL (1 reel then exit), PAUSED (auto-resume)
 * - Blocking Actions: PRESS_BACK, GO_HOME (minimize), LOCK_SCREEN (Android 9+)
 * - 14+ app families + multi-browser Shorts & Unlimited Website/Keyword Blocker
 * - Floating Scroll Watchtime Counter overlay (TYPE_ACCESSIBILITY_OVERLAY) + Haptic feedback
 * - App Blocker with Daily Quota + Temporary Unlock + Phone Settings Anti-Tamper
 */
class NativeReelsBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "NativeReelsBlocker"

        private const val ACTION_COOLDOWN_MS = 380L
        private const val TOAST_COOLDOWN_MS = 2200L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val DM_TO_REEL_WINDOW_MS = 5000L
        private const val SENT_REEL_SCROLL_GRACE_MS = 650L
        private const val OVERLAY_LAUNCH_COOLDOWN_MS = 1400L

        private val SHORT_VIDEO_URL_PATHS = listOf(
            "youtube.com/shorts/",
            "m.youtube.com/shorts/",
            "instagram.com/reels",
            "instagram.com/reel/",
            "facebook.com/reel/",
            "facebook.com/reels/",
            "m.facebook.com/reel",
            "tiktok.com/@",
            "tiktok.com/foryou"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefsManager: PreferencesManager
    private var windowManager: WindowManager? = null

    // Platform rules
    @Volatile private var blockIgReels = true
    @Volatile private var allowSentReels = true
    @Volatile private var lockIgReelsTab = true
    @Volatile private var blockIgExplore = false
    @Volatile private var blockIgStories = false
    @Volatile private var blockYtShorts = true
    @Volatile private var blockFbReels = true
    @Volatile private var blockSnapSpotlight = true
    @Volatile private var blockSnapDiscover = false
    @Volatile private var blockSnapStories = false
    @Volatile private var blockTiktok = true
    @Volatile private var blockTwitterVideo = true
    @Volatile private var blockRedditVideo = true
    @Volatile private var blockLinkedinVideo = true
    @Volatile private var blockWhatsAppStatus = false
    @Volatile private var blockBrowserShorts = true

    // Plans & modes
    @Volatile private var activePlan = BlockerPlan.BLOCK_ALL
    @Volatile private var blockActionMode = BlockActionMode.PRESS_BACK
    @Volatile private var curiousSessionMins = 5
    @Volatile private var curiousCooldownMins = 2
    @Volatile private var curiousSessionStartMs = 0L
    @Volatile private var curiousCooldownUntilMs = 0L
    @Volatile private var curiousAllowOneInCooldown = false
    @Volatile private var pauseUntilMs = 0L

    // Counter overlay & haptics
    @Volatile private var reelCounterEnabled = true
    @Volatile private var reelCounterDetailed = true
    @Volatile private var reelCounterHaptics = true
    @Volatile private var blockHaptics = true

    // App & Website blockers + PIN guard
    @Volatile private var blockedApps: List<BlockedAppRule> = emptyList()
    @Volatile private var blockedSites: List<BlockedSiteRule> = emptyList()
    @Volatile private var pinType = PinType.NONE
    @Volatile private var pinProtectSystemSettings = false

    private var lastActionAtMs = 0L
    private var lastToastAtMs = 0L
    private var lastAppLockLaunchAtMs = 0L
    private var currentForegroundPkg: String? = null

    // Sent Reel / One Reel tracking
    private var lastInDmAtMs = 0L
    private var inOneReelSession = false
    private var oneReelEnteredAtMs = 0L
    private var oneReelInitialSignature: String? = null

    // Short video scroll session counter (for Watchtime Counter overlay & Curious timer)
    private var activeReelWatchPkg: String? = null
    private var activeReelWatchStartMs = 0L
    private var sessionReelsCount = 0
    private var lastCounterSig: String? = null
    private var lastCounterScrollPulseAtMs = 0L

    // App Blocker usage tick
    private var lastAppUsageTickMs = 0L

    // Tab lock overlay (always mutated strictly on mainHandler)
    private var tabLockOverlay: View? = null
    private var tabLockParams: WindowManager.LayoutParams? = null
    private val lastOverlayRect = Rect()

    // Floating Watchtime Counter pill overlay
    private var counterOverlay: View? = null
    private var counterLabel: TextView? = null
    private var lastCounterText = ""

    private var watchdogRunning = false
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val pkg = currentForegroundPkg
            val isWatched = pkg != null && (
                pkg in BlockerPackages.ALL_SHORT_VIDEO_PACKAGES ||
                    pkg in BlockerPackages.BROWSERS ||
                    blockedApps.any { it.enabled && it.packageName == pkg }
                )
            if (pkg == null || !isWatched) {
                watchdogRunning = false
                removeTabLockOverlay()
                removeCounterOverlay()
                return
            }
            try {
                checkPauseExpiry()
                tickAppUsageIfNeeded(pkg)
                val root = findAppWindowRoot(pkg)
                if (root != null) {
                    inspectScreen(pkg, event = null, root = root)
                }
            } catch (_: Throwable) {
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefsManager = PreferencesManager.getInstance(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

        serviceScope.launch { prefsManager.nativeBlockIgReels.collectLatest { blockIgReels = it } }
        serviceScope.launch { prefsManager.nativeAllowSentReels.collectLatest { allowSentReels = it } }
        serviceScope.launch { prefsManager.nativeLockIgReelsTab.collectLatest { lockIgReelsTab = it } }
        serviceScope.launch { prefsManager.nativeBlockIgExplore.collectLatest { blockIgExplore = it } }
        serviceScope.launch { prefsManager.nativeBlockIgStories.collectLatest { blockIgStories = it } }
        serviceScope.launch { prefsManager.nativeBlockYtShorts.collectLatest { blockYtShorts = it } }
        serviceScope.launch { prefsManager.nativeBlockFbReels.collectLatest { blockFbReels = it } }
        serviceScope.launch { prefsManager.nativeBlockSnapSpotlight.collectLatest { blockSnapSpotlight = it } }
        serviceScope.launch { prefsManager.nativeBlockSnapDiscover.collectLatest { blockSnapDiscover = it } }
        serviceScope.launch { prefsManager.nativeBlockSnapStories.collectLatest { blockSnapStories = it } }
        serviceScope.launch { prefsManager.nativeBlockTiktok.collectLatest { blockTiktok = it } }
        serviceScope.launch { prefsManager.nativeBlockTwitterVideo.collectLatest { blockTwitterVideo = it } }
        serviceScope.launch { prefsManager.nativeBlockRedditVideo.collectLatest { blockRedditVideo = it } }
        serviceScope.launch { prefsManager.nativeBlockLinkedinVideo.collectLatest { blockLinkedinVideo = it } }
        serviceScope.launch { prefsManager.nativeBlockWhatsAppStatus.collectLatest { blockWhatsAppStatus = it } }
        serviceScope.launch { prefsManager.nativeBlockBrowserShorts.collectLatest { blockBrowserShorts = it } }

        serviceScope.launch { prefsManager.blockerPlan.collectLatest { activePlan = BlockerPlan.fromId(it) } }
        serviceScope.launch { prefsManager.blockActionMode.collectLatest { blockActionMode = BlockActionMode.fromId(it) } }
        serviceScope.launch { prefsManager.curiousSessionMinutes.collectLatest { curiousSessionMins = it.coerceAtLeast(1) } }
        serviceScope.launch { prefsManager.curiousCooldownMinutes.collectLatest { curiousCooldownMins = it.coerceAtLeast(1) } }
        serviceScope.launch { prefsManager.curiousSessionStartMs.collectLatest { curiousSessionStartMs = it } }
        serviceScope.launch { prefsManager.curiousCooldownUntilMs.collectLatest { curiousCooldownUntilMs = it } }
        serviceScope.launch { prefsManager.curiousAllowOneInCooldown.collectLatest { curiousAllowOneInCooldown = it } }
        serviceScope.launch { prefsManager.pauseUntilMs.collectLatest { pauseUntilMs = it } }

        serviceScope.launch { prefsManager.reelCounterEnabled.collectLatest { reelCounterEnabled = it } }
        serviceScope.launch { prefsManager.reelCounterDetailed.collectLatest { reelCounterDetailed = it } }
        serviceScope.launch { prefsManager.reelCounterHaptics.collectLatest { reelCounterHaptics = it } }
        serviceScope.launch { prefsManager.blockHaptics.collectLatest { blockHaptics = it } }

        serviceScope.launch {
            prefsManager.blockedAppsJson.collectLatest { blockedApps = SiteListSerializer.decodeApps(it) }
        }
        serviceScope.launch {
            prefsManager.blockedSitesJson.collectLatest { blockedSites = SiteListSerializer.decodeSites(it) }
        }
        serviceScope.launch { prefsManager.pinType.collectLatest { pinType = PinType.fromId(it) } }
        serviceScope.launch { prefsManager.pinProtectSystemSettings.collectLatest { pinProtectSystemSettings = it } }

        BlockerKeepAliveService.start(applicationContext)
        Log.i(TAG, "SocialLite NativeReelsBlockerService connected (v4 Full Engine)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName || pkg.contains("inputmethod", ignoreCase = true) ||
            pkg == "com.android.systemui"
        ) {
            return
        }

        checkPauseExpiry()

        // 1. Check System Settings anti-tamper guard
        if (pkg == "com.android.settings" &&
            pinProtectSystemSettings &&
            pinType != PinType.NONE &&
            !isBlockerPausedNow()
        ) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                launchAppLockOverlay("com.android.settings", "Settings", "SETTINGS_GUARD")
            }
            return
        }

        // 2. Check App Blocker & Daily Quota
        val blockedApp = blockedApps.firstOrNull { it.enabled && it.packageName == pkg }
        if (blockedApp != null && !isBlockerPausedNow()) {
            val now = System.currentTimeMillis()
            val temporarilyUnlocked = blockedApp.unlockUntilMs > now
            if (!temporarilyUnlocked) {
                val today = LocalDate.now().toString()
                val usedToday = if (blockedApp.usedDate == today) blockedApp.usedTodaySeconds else 0
                val limitSec = blockedApp.dailyLimitMinutes * 60
                if (blockedApp.dailyLimitMinutes <= 0 || usedToday >= limitSec) {
                    val reason = if (blockedApp.dailyLimitMinutes > 0) "DAILY_LIMIT" else "APP_LOCK"
                    removeTabLockOverlay()
                    removeCounterOverlay()
                    launchAppLockOverlay(pkg, blockedApp.appName, reason)
                    return
                }
            }
        }

        val isBrowser = pkg in BlockerPackages.BROWSERS
        val isShortApp = pkg in BlockerPackages.ALL_SHORT_VIDEO_PACKAGES
        if (!isBrowser && !isShortApp && blockedApp == null) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentForegroundPkg = pkg
                resetOneReelState()
                resetWatchCounterSession()
                removeTabLockOverlay()
                removeCounterOverlay()
            }
            return
        }

        if (currentForegroundPkg != pkg && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            resetWatchCounterSession()
            lastAppUsageTickMs = System.currentTimeMillis()
        }
        currentForegroundPkg = pkg
        ensureWatchdogStarted()

        try {
            val root = findAppWindowRoot(pkg)
            inspectScreen(pkg, event, root)
        } catch (t: Throwable) {
            Log.w(TAG, "onAccessibilityEvent error swallowed: ${t.message}")
        }
    }

    private fun checkPauseExpiry() {
        val now = System.currentTimeMillis()
        if (activePlan == BlockerPlan.PAUSED && pauseUntilMs > 0L && now >= pauseUntilMs) {
            pauseUntilMs = 0L
            serviceScope.launch { prefsManager.resumeFromPause() }
            showRateLimitedToast("Pause ended — SocialLite Blocker resumed")
        }
    }

    private fun isBlockerPausedNow(): Boolean {
        return activePlan == BlockerPlan.PAUSED &&
            (pauseUntilMs == 0L || System.currentTimeMillis() < pauseUntilMs)
    }

    private fun tickAppUsageIfNeeded(pkg: String) {
        val now = System.currentTimeMillis()
        if (lastAppUsageTickMs == 0L) {
            lastAppUsageTickMs = now
            return
        }
        val elapsedSec = ((now - lastAppUsageTickMs) / 1000L).toInt()
        if (elapsedSec >= 2) {
            lastAppUsageTickMs = now
            val rule = blockedApps.firstOrNull { it.enabled && it.packageName == pkg } ?: return
            if (rule.dailyLimitMinutes > 0 && rule.unlockUntilMs <= now && !isBlockerPausedNow()) {
                serviceScope.launch { prefsManager.tickBlockedAppUsage(pkg, elapsedSec) }
            }
        }
    }

    private fun launchAppLockOverlay(pkg: String, appName: String, reason: String) {
        if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) return
        val now = System.currentTimeMillis()
        if (now - lastAppLockLaunchAtMs < OVERLAY_LAUNCH_COOLDOWN_MS) return
        lastAppLockLaunchAtMs = now

        vibrateFeedback(strong = true)
        serviceScope.launch {
            prefsManager.recordBlockEvent(appName, pkg, "App Lock", activePlan.shortTitle)
        }

        val intent = Intent(this, AppLockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppLockOverlayActivity.EXTRA_TARGET_PACKAGE, pkg)
            putExtra(AppLockOverlayActivity.EXTRA_TARGET_APP_NAME, appName)
            putExtra(AppLockOverlayActivity.EXTRA_REASON, reason)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching AppLockOverlayActivity: ${e.message}")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun ensureWatchdogStarted() {
        mainHandler.post {
            if (!watchdogRunning) {
                watchdogRunning = true
                mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun findAppWindowRoot(targetPkg: String): AccessibilityNodeInfo? {
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null && activeRoot.packageName?.toString() == targetPkg) {
                return activeRoot
            }
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (w in windowList) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val r = w.root
                        if (r != null && r.packageName?.toString() == targetPkg) {
                            return r
                        }
                    }
                }
            }
            return activeRoot
        } catch (_: Throwable) {
            return rootInActiveWindow
        }
    }

    private fun inspectScreen(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        if (isBlockerPausedNow()) {
            removeTabLockOverlay()
            updateCounterOverlay(pkg, isWatchingShorts = true)
            return
        }

        if (pkg in BlockerPackages.BROWSERS) {
            handleExternalBrowser(pkg, root)
            removeCounterOverlay()
            return
        }

        when {
            pkg in BlockerPackages.INSTAGRAM_PACKAGES -> handleInstagram(pkg, event, root)
            pkg in BlockerPackages.YOUTUBE_PACKAGES -> handleYouTube(pkg, event, root)
            pkg in BlockerPackages.SNAPCHAT_PACKAGES -> handleSnapchat(pkg, event, root)
            pkg in BlockerPackages.FACEBOOK_PACKAGES -> handleFacebook(pkg, event, root)
            pkg in BlockerPackages.TIKTOK_PACKAGES -> handleTikTok(pkg, event, root)
            pkg in BlockerPackages.TWITTER_PACKAGES -> handleTwitter(pkg, event, root)
            pkg in BlockerPackages.REDDIT_PACKAGES -> handleReddit(pkg, event, root)
            pkg in BlockerPackages.LINKEDIN_PACKAGES -> handleLinkedIn(pkg, event, root)
            pkg in BlockerPackages.WHATSAPP_PACKAGES -> handleWhatsApp(pkg, root)
            pkg in BlockerPackages.EXTRA_SHORT_VIDEO_PACKAGES -> handleExtraShortVideoApps(pkg, event, root)
            else -> {
                removeTabLockOverlay()
                removeCounterOverlay()
            }
        }
    }

    // =========================================================================
    // Plan Gate Engine (BLOCK_ALL / CURIOUS / ONE_REEL)
    // Returns true if allowed to continue watching right now, false if blocked!
    // =========================================================================
    private fun evaluateShortVideoPlan(
        pkg: String,
        platformLabel: String,
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        isFriendDmReel: Boolean = false,
        preferredAction: BlockActionMode = blockActionMode
    ): Boolean {
        val now = System.currentTimeMillis()

        // 1. Always respect "Allow 1 Friend-Sent Reel" in Instagram DM if enabled
        if (isFriendDmReel && allowSentReels) {
            return handleSingleReelAllowance(
                pkg = pkg,
                platformLabel = platformLabel,
                event = event,
                root = root,
                preferredAction = preferredAction,
                blockMsg = "Reel scrolling blocked by SocialLite"
            )
        }

        return when (activePlan) {
            BlockerPlan.PAUSED -> {
                updateCounterOverlay(pkg, isWatchingShorts = true)
                true
            }
            BlockerPlan.ONE_REEL -> {
                handleSingleReelAllowance(
                    pkg = pkg,
                    platformLabel = platformLabel,
                    event = event,
                    root = root,
                    preferredAction = preferredAction,
                    blockMsg = "One Reel Mode: stop after 1 video"
                )
            }
            BlockerPlan.CURIOUS -> {
                // Inside cooldown lock?
                if (curiousCooldownUntilMs > now) {
                    if (curiousAllowOneInCooldown) {
                        return handleSingleReelAllowance(
                            pkg = pkg,
                            platformLabel = platformLabel,
                            event = event,
                            root = root,
                            preferredAction = preferredAction,
                            blockMsg = "Cooldown active — 1 reel limit reached"
                        )
                    }
                    val secsLeft = ((curiousCooldownUntilMs - now) / 1000L).coerceAtLeast(1L)
                    executeBlock(
                        platformLabel = platformLabel,
                        pkg = pkg,
                        mode = preferredAction,
                        toastMessage = "Curious Cooldown active (${secsLeft}s left)"
                    )
                    false
                } else {
                    val windowMs = curiousSessionMins * 60_000L
                    val cooldownMs = curiousCooldownMins * 60_000L
                    if (curiousSessionStartMs <= 0L || (now - curiousSessionStartMs) >= (windowMs + cooldownMs)) {
                        // Start a brand-new Curious watch window!
                        curiousSessionStartMs = now
                        curiousCooldownUntilMs = now + windowMs + cooldownMs
                        val startCopy = now
                        val coolCopy = now + windowMs + cooldownMs
                        serviceScope.launch { prefsManager.updateCuriousWindow(startCopy, coolCopy) }
                    }
                    val elapsedInSession = now - curiousSessionStartMs
                    if (elapsedInSession < windowMs) {
                        // Watch window open -> show live Curious countdown overlay + track scrolls
                        trackWatchSessionAndPulse(pkg, event, root)
                        true
                    } else {
                        // Cooldown just started -> block!
                        val secsLeft = ((curiousCooldownUntilMs - now) / 1000L).coerceAtLeast(1L)
                        executeBlock(
                            platformLabel = platformLabel,
                            pkg = pkg,
                            mode = preferredAction,
                            toastMessage = "Curious session ended! Cooldown (${secsLeft}s)"
                        )
                        false
                    }
                }
            }
            BlockerPlan.BLOCK_ALL -> {
                executeBlock(
                    platformLabel = platformLabel,
                    pkg = pkg,
                    mode = preferredAction,
                    toastMessage = "$platformLabel blocked by SocialLite"
                )
                false
            }
        }
    }

    private fun handleSingleReelAllowance(
        pkg: String,
        platformLabel: String,
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        preferredAction: BlockActionMode,
        blockMsg: String
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!inOneReelSession) {
            inOneReelSession = true
            oneReelEnteredAtMs = now
            oneReelInitialSignature = readReelsContentSignature(pkg, root)
            trackWatchSessionAndPulse(pkg, event, root)
            return true
        }

        if (oneReelInitialSignature == null) {
            oneReelInitialSignature = readReelsContentSignature(pkg, root)
        }

        val elapsed = now - oneReelEnteredAtMs
        if (elapsed > SENT_REEL_SCROLL_GRACE_MS) {
            val scrolledEvent = event != null &&
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                isReelsScrollEvent(event)

            val currentSig = readReelsContentSignature(pkg, root)
            val contentChanged = oneReelInitialSignature != null &&
                currentSig != null &&
                currentSig != oneReelInitialSignature

            if (scrolledEvent || contentChanged) {
                resetOneReelState()
                lastInDmAtMs = 0L
                executeBlock(platformLabel, pkg, preferredAction, blockMsg)
                return false
            }
        }
        trackWatchSessionAndPulse(pkg, event, root)
        return true
    }

    // =========================================================================
    // Platform Handlers
    // =========================================================================

    private fun handleInstagram(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        if (!blockIgReels && !blockIgExplore && !blockIgStories) {
            removeTabLockOverlay()
            removeCounterOverlay()
            return
        }

        val now = System.currentTimeMillis()
        val eventClass = event?.className?.toString() ?: ""

        val inDmChatOnScreen = root != null && hasVisibleOnScreenViewId(
            root,
            listOf(
                "$pkg:id/row_thread_composer_edittext",
                "$pkg:id/direct_thread_recycler_view",
                "$pkg:id/reply_bar_edittext"
            )
        )
        if (inDmChatOnScreen) {
            lastInDmAtMs = now
        }

        val isClipsActivity = eventClass.contains("ClipsUrlHandlerActivity", ignoreCase = true) ||
            eventClass.contains("ClipsViewer", ignoreCase = true)

        val inReelsViewer = isClipsActivity || (root != null && hasVisibleOnScreenViewId(
            root,
            listOf(
                "$pkg:id/clips_viewer_container",
                "$pkg:id/clips_viewer_view_pager",
                "$pkg:id/clips_viewer_video_layout",
                "$pkg:id/clips_author_username",
                "$pkg:id/clips_video_container"
            )
        ))

        if (blockIgReels && inReelsViewer) {
            removeTabLockOverlay()
            val hasReplyBar = root != null && hasVisibleOnScreenViewId(root, listOf("$pkg:id/reply_bar_edittext"))
            val cameFromDm = (now - lastInDmAtMs) <= DM_TO_REEL_WINDOW_MS || hasReplyBar
            evaluateShortVideoPlan(
                pkg = pkg,
                platformLabel = "Instagram Reels",
                event = event,
                root = root,
                isFriendDmReel = cameFromDm
            )
            return
        }

        if (inOneReelSession) {
            resetOneReelState()
        }
        resetWatchCounterSession()
        removeCounterOverlay()

        // Optional Instagram Stories blocker (NoScroll: :id/reel_viewer_title)
        if (blockIgStories && root != null &&
            hasVisibleOnScreenViewId(root, listOf("$pkg:id/reel_viewer_title"))
        ) {
            removeTabLockOverlay()
            executeBlock("Instagram Stories", pkg, BlockActionMode.PRESS_BACK, "Stories blocked by SocialLite")
            return
        }

        // Clear DM window once visibly back on Home / Feed / Search tab
        if (!inDmChatOnScreen && root != null) {
            val onMainTabs = hasVisibleOnScreenViewId(
                root,
                listOf("$pkg:id/tab_bar", "$pkg:id/feed_tab", "$pkg:id/search_tab")
            )
            if (onMainTabs) {
                lastInDmAtMs = 0L
            }
        }

        // Block Explore / Search if enabled (:id/explore_action_bar or :id/action_bar_search_edit_text)
        if (blockIgExplore && root != null) {
            if (hasVisibleOnScreenViewId(
                    root,
                    listOf("$pkg:id/explore_action_bar", "$pkg:id/action_bar_search_edit_text")
                )
            ) {
                removeTabLockOverlay()
                executeBlock("Instagram Explore", pkg, BlockActionMode.PRESS_BACK, "Explore blocked by SocialLite")
                return
            }
        }

        // Tab Lock overlay only in BLOCK_ALL or during CURIOUS cooldown (let user open tab in ONE_REEL / active Curious)
        val shouldLockTab = blockIgReels && lockIgReelsTab && root != null && (
            activePlan == BlockerPlan.BLOCK_ALL ||
                (activePlan == BlockerPlan.CURIOUS && curiousCooldownUntilMs > now &&
                    (now - curiousSessionStartMs) >= curiousSessionMins * 60_000L)
            )

        if (shouldLockTab && root != null) {
            val reelsTab = findVisibleViewById(root, "$pkg:id/clips_tab")
                ?: findBottomTabNode(root, "$pkg:id/tab_bar", "reel")
            if (reelsTab != null && isNodeActuallyOnScreen(reelsTab)) {
                val rect = Rect()
                reelsTab.getBoundsInScreen(rect)
                if (rect.top > 250) {
                    showOrMoveTabLockOverlay(
                        rect = rect,
                        iconRes = R.drawable.ig_reels_tab_locked,
                        blockMsg = "Reels are blocked by SocialLite"
                    )
                    return
                }
            }
        }
        removeTabLockOverlay()
    }

    private fun handleYouTube(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        if (!blockYtShorts || root == null) {
            removeTabLockOverlay()
            removeCounterOverlay()
            return
        }

        val inShortsPlayer = hasVisibleOnScreenViewId(
            root,
            listOf(
                "$pkg:id/reel_player_underlay",
                "$pkg:id/reel_player_page_container",
                "$pkg:id/reel_recycler",
                "$pkg:id/shorts_player_container",
                "$pkg:id/reel_watch_fragment_root",
                "$pkg:id/reel_vod_player_container",
                "$pkg:id/reel_watch_player",
                "$pkg:id/playback_gesture_detector"
            )
        ) || hasVisibleViewIdContainsAny(root, listOf("reel_player", "shorts_player", "reel_watch", "reel_recycler"))

        if (inShortsPlayer) {
            removeTabLockOverlay()
            evaluateShortVideoPlan(pkg, "YouTube Shorts", event, root)
            return
        }

        resetOneReelState()
        resetWatchCounterSession()
        removeCounterOverlay()

        val shouldLockTab = activePlan == BlockerPlan.BLOCK_ALL
        if (shouldLockTab) {
            val shortsTab = findBottomTabNode(root, "$pkg:id/pivot_bar", "short")
            if (shortsTab != null && isNodeActuallyOnScreen(shortsTab)) {
                val rect = Rect()
                shortsTab.getBoundsInScreen(rect)
                if (rect.top > 250) {
                    showOrMoveTabLockOverlay(
                        rect = rect,
                        iconRes = R.drawable.ig_reels_tab_locked,
                        blockMsg = "Shorts are blocked by SocialLite"
                    )
                    return
                }
            }
        }
        removeTabLockOverlay()
    }

    private fun handleSnapchat(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        if (root == null || (!blockSnapSpotlight && !blockSnapDiscover && !blockSnapStories)) {
            removeTabLockOverlay()
            removeCounterOverlay()
            return
        }

        if (blockSnapSpotlight && hasVisibleOnScreenViewId(
                root,
                listOf("$pkg:id/spotlight_container", "$pkg:id/spotlight_view_count")
            )
        ) {
            removeTabLockOverlay()
            evaluateShortVideoPlan(pkg, "Snapchat Spotlight", event, root)
            return
        }

        if (blockSnapDiscover && hasVisibleOnScreenViewId(root, listOf("$pkg:id/df_large_story"))) {
            removeTabLockOverlay()
            executeBlock("Snapchat Discover", pkg, blockActionMode, "Discover blocked by SocialLite")
            return
        }

        if (blockSnapStories && (
                hasVisibleOnScreenViewId(root, listOf("$pkg:id/view_profile")) ||
                    hasVisibleViewIdContainsAny(root, listOf("context_vertical_action_share", "context_vertical_action_favorite"))
                )
        ) {
            removeTabLockOverlay()
            executeBlock("Snapchat Stories", pkg, BlockActionMode.PRESS_BACK, "Stories blocked by SocialLite")
            return
        }

        resetOneReelState()
        resetWatchCounterSession()
        removeCounterOverlay()

        if (blockSnapSpotlight && activePlan == BlockerPlan.BLOCK_ALL) {
            val spotTab = findVisibleViewById(root, "$pkg:id/ngs_spotlight_icon_container")
            if (spotTab != null && isNodeActuallyOnScreen(spotTab)) {
                val rect = Rect()
                spotTab.getBoundsInScreen(rect)
                if (rect.top > 250) {
                    showOrMoveTabLockOverlay(
                        rect = rect,
                        iconRes = R.drawable.snapchat_spotlight_tab_locked,
                        blockMsg = "Spotlight is blocked by SocialLite"
                    )
                    return
                }
            }
        }
        removeTabLockOverlay()
    }

    private fun handleFacebook(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (!blockFbReels || root == null) {
            removeCounterOverlay()
            return
        }
        if (hasVisibleViewIdContainsAny(root, listOf("reels_viewer", "fb_shorts"))) {
            evaluateShortVideoPlan(pkg, "Facebook Reels", event, root)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    private fun handleTikTok(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (!blockTiktok || root == null) {
            removeCounterOverlay()
            return
        }
        if (hasVisibleOnScreenViewId(root, listOf("$pkg:id/desc"))) {
            // TikTok default recommended mode in NoScroll is GO_HOME unless user chose LOCK_SCREEN
            val ttAction = if (blockActionMode == BlockActionMode.PRESS_BACK) BlockActionMode.GO_HOME else blockActionMode
            evaluateShortVideoPlan(pkg, "TikTok Videos", event, root, preferredAction = ttAction)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    private fun handleTwitter(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (!blockTwitterVideo || root == null) {
            removeCounterOverlay()
            return
        }
        val inImmersiveVideo = hasVisibleViewIdContainsAny(root, listOf("VideoTab")) ||
            (hasVisibleOnScreenViewId(root, listOf("$pkg:id/mediacontroller_progress")) &&
                hasVisibleViewIdContainsAny(root, listOf("immersive_player", "video_player_dock")))
        if (inImmersiveVideo) {
            evaluateShortVideoPlan(pkg, "X Video Feed", event, root)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    private fun handleReddit(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (!blockRedditVideo || root == null) {
            removeCounterOverlay()
            return
        }
        if (hasVisibleViewIdContainsAny(root, listOf("content_video_view"))) {
            evaluateShortVideoPlan(pkg, "Reddit Watch", event, root)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    private fun handleLinkedIn(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (!blockLinkedinVideo || root == null) {
            removeCounterOverlay()
            return
        }
        if (hasVisibleOnScreenViewId(root, listOf("$pkg:id/media_viewer_actor_bottom"))) {
            evaluateShortVideoPlan(pkg, "LinkedIn Videos", event, root)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    private fun handleWhatsApp(pkg: String, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        removeCounterOverlay()
        if (!blockWhatsAppStatus || root == null) return
        if (hasVisibleOnScreenViewId(root, listOf("$pkg:id/status_header"))) {
            executeBlock("WhatsApp Status", pkg, BlockActionMode.PRESS_BACK, "Status blocked by SocialLite")
        }
    }

    private fun handleExtraShortVideoApps(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (root == null) {
            removeCounterOverlay()
            return
        }
        val detected = when (pkg) {
            "com.vk.clips", "com.vkontakte.android" -> hasVisibleOnScreenViewId(
                root,
                listOf("$pkg:id/clip_owner_name_scrollable", "$pkg:id/fullscreen_clip_overlay_like_text", "$pkg:id/clip_owner_view")
            )
            "com.xingin.xhs" -> hasVisibleOnScreenViewId(root, listOf("$pkg:id/noteContentText"))
            else -> hasVisibleOnScreenViewId(root, listOf("$pkg:id/story_ctrl_router"))
        }
        if (detected) {
            evaluateShortVideoPlan(pkg, "Short Videos", event, root)
        } else {
            resetOneReelState()
            resetWatchCounterSession()
            removeCounterOverlay()
        }
    }

    // =========================================================================
    // External Browser Short Video & Unlimited Website/Keyword Blocker
    // =========================================================================
    private fun handleExternalBrowser(pkg: String, root: AccessibilityNodeInfo?) {
        removeTabLockOverlay()
        if (root == null) return

        val url = extractBrowserUrl(pkg, root) ?: return
        val lower = url.lowercase()

        // 1. Check Shorts/Reels URLs inside Chrome/Brave/Edge/Firefox/Opera/etc.
        if (blockBrowserShorts && SHORT_VIDEO_URL_PATHS.any { lower.contains(it) }) {
            executeBlock(
                platformLabel = "Browser Shorts",
                pkg = pkg,
                mode = blockActionMode,
                toastMessage = "Browser Shorts/Reels blocked by SocialLite"
            )
            return
        }

        // 2. Check user's custom Unlimited Blocked Websites & Keywords
        for (rule in blockedSites) {
            if (rule.matchesUrl(lower)) {
                executeBlock(
                    platformLabel = rule.pattern,
                    pkg = pkg,
                    mode = rule.action,
                    toastMessage = "Site blocked: ${rule.pattern}"
                )
                return
            }
        }
    }

    private fun extractBrowserUrl(pkg: String, root: AccessibilityNodeInfo): String? {
        for (hint in BlockerPackages.URL_BAR_HINTS) {
            val fullId = "$pkg$hint"
            val node = findVisibleViewById(root, fullId)
            val txt = node?.text?.toString()?.trim()
            if (!txt.isNullOrBlank() && (txt.contains('.') || txt.contains('/'))) {
                return txt
            }
        }
        return null
    }

    // =========================================================================
    // Watchtime Counter Overlay & Scroll Haptics
    // =========================================================================
    private fun trackWatchSessionAndPulse(pkg: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()
        if (activeReelWatchPkg != pkg || activeReelWatchStartMs == 0L) {
            activeReelWatchPkg = pkg
            activeReelWatchStartMs = now
            sessionReelsCount = 1
            lastCounterSig = readReelsContentSignature(pkg, root)
        } else {
            val newSig = readReelsContentSignature(pkg, root)
            val scrolled = event != null &&
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                (now - lastCounterScrollPulseAtMs) > 900L &&
                isReelsScrollEvent(event)
            val sigChanged = newSig != null && newSig != lastCounterSig

            if (scrolled || sigChanged) {
                lastCounterScrollPulseAtMs = now
                lastCounterSig = newSig
                sessionReelsCount++
                serviceScope.launch { prefsManager.incrementReelsScrolled() }
                if (reelCounterHaptics) vibrateFeedback(strong = false)
            }
        }
        updateCounterOverlay(pkg, isWatchingShorts = true)
    }

    private fun resetWatchCounterSession() {
        activeReelWatchPkg = null
        activeReelWatchStartMs = 0L
        sessionReelsCount = 0
        lastCounterSig = null
    }

    @SuppressLint("RtlHardcoded")
    private fun updateCounterOverlay(pkg: String, isWatchingShorts: Boolean) {
        if (!reelCounterEnabled || !isWatchingShorts) {
            removeCounterOverlay()
            return
        }
        val wm = windowManager ?: return
        val now = System.currentTimeMillis()

        val text = buildString {
            when (activePlan) {
                BlockerPlan.PAUSED -> {
                    val leftMin = ((pauseUntilMs - now) / 60_000L).coerceAtLeast(1L)
                    append("⏸ Paused · ${leftMin}m")
                }
                BlockerPlan.CURIOUS -> {
                    val windowMs = curiousSessionMins * 60_000L
                    val remSec = if (curiousSessionStartMs > 0L) {
                        ((curiousSessionStartMs + windowMs - now) / 1000L).coerceAtLeast(0L)
                    } else (curiousSessionMins * 60L)
                    append("⏳ ${remSec / 60}:${(remSec % 60).toString().padStart(2, '0')}")
                }
                BlockerPlan.ONE_REEL -> append("1️⃣ One Reel")
                BlockerPlan.BLOCK_ALL -> append("🛡 Active")
            }
            if (reelCounterDetailed && activeReelWatchStartMs > 0L) {
                val watchSec = ((now - activeReelWatchStartMs) / 1000L).coerceAtLeast(0L)
                append("  ·  ${watchSec / 60}:${(watchSec % 60).toString().padStart(2, '0')} · #$sessionReelsCount")
            }
        }

        mainHandler.post {
            try {
                if (counterOverlay == null) {
                    val pill = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(24, 12, 24, 12)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#CC141B29"))
                            cornerRadius = 40f
                            setStroke(2, Color.parseColor("#557DD3FC"))
                        }
                    }
                    val tv = TextView(this).apply {
                        this.text = text
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                    pill.addView(tv)

                    val lp = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = 28
                        y = 110
                    }
                    wm.addView(pill, lp)
                    counterOverlay = pill
                    counterLabel = tv
                    lastCounterText = text
                } else if (lastCounterText != text) {
                    counterLabel?.text = text
                    lastCounterText = text
                }
            } catch (_: Exception) {
                counterOverlay = null
                counterLabel = null
            }
        }
    }

    private fun removeCounterOverlay() {
        mainHandler.post {
            val view = counterOverlay ?: return@post
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
            } catch (_: Exception) {
            } finally {
                counterOverlay = null
                counterLabel = null
                lastCounterText = ""
            }
        }
    }

    // =========================================================================
    // Execute Block Action & Haptics
    // =========================================================================
    private fun executeBlock(
        platformLabel: String,
        pkg: String,
        mode: BlockActionMode,
        toastMessage: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now

        when (mode) {
            BlockActionMode.PRESS_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            BlockActionMode.GO_HOME -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 80L)
            }
            BlockActionMode.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 80L)
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }

        if (blockHaptics) vibrateFeedback(strong = true)
        serviceScope.launch {
            prefsManager.recordBlockEvent(platformLabel, pkg, mode.label, activePlan.shortTitle)
        }
        showRateLimitedToast(toastMessage)
    }

    @Suppress("DEPRECATION")
    private fun vibrateFeedback(strong: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            val ms = if (strong) 42L else 18L
            val amp = if (strong) 180 else 90
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (_: Exception) {
        }
    }

    private fun showRateLimitedToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastAtMs < TOAST_COOLDOWN_MS) return
        lastToastAtMs = now
        mainHandler.post {
            Toast.makeText(applicationContext, msg.replace("SocialLite", "NoScroll"), Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // Helpers (Scroll Detection, Content Signature, Bounds, Overlays)
    // =========================================================================
    private fun isReelsScrollEvent(event: AccessibilityEvent): Boolean {
        val srcId = event.source?.viewIdResourceName ?: ""
        val srcCls = event.source?.className?.toString() ?: ""
        if (srcId.contains("clips_viewer", ignoreCase = true) ||
            srcId.contains("view_pager", ignoreCase = true) ||
            srcId.contains("reel_recycler", ignoreCase = true) ||
            srcCls.contains("ViewPager", ignoreCase = true) ||
            srcCls.contains("RecyclerView", ignoreCase = true)
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && abs(event.scrollDeltaY) > 15) {
            return true
        }
        if (event.fromIndex >= 0 && event.toIndex >= 0 && event.toIndex != event.fromIndex) {
            return true
        }
        return false
    }

    private fun readReelsContentSignature(pkg: String, root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val author = findVisibleViewById(root, "$pkg:id/clips_author_username")
            ?.text?.toString()?.trim()
        if (!author.isNullOrEmpty()) return "author:$author"

        val viewer = findVisibleViewById(root, "$pkg:id/clips_viewer_container")
            ?: findVisibleViewById(root, "$pkg:id/reel_player_page_container")
            ?: root
        val parts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(viewer)
        var visited = 0
        while (queue.isNotEmpty() && visited < 40 && parts.size < 4) {
            val node = queue.removeFirst()
            visited++
            val t = node.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length > 2) {
                parts.add(t)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return if (parts.isNotEmpty()) parts.joinToString("|") else null
    }

    private fun resetOneReelState() {
        inOneReelSession = false
        oneReelEnteredAtMs = 0L
        oneReelInitialSignature = null
    }

    @SuppressLint("RtlHardcoded")
    private fun showOrMoveTabLockOverlay(rect: Rect, iconRes: Int, blockMsg: String) {
        val wm = windowManager ?: return
        mainHandler.post {
            try {
                val existing = tabLockOverlay
                if (existing != null && existing.isAttachedToWindow && lastOverlayRect == rect) {
                    return@post
                }
                if (existing != null && !existing.isAttachedToWindow) {
                    tabLockOverlay = null
                    tabLockParams = null
                    lastOverlayRect.setEmpty()
                }
                if (tabLockOverlay == null) {
                    val container = FrameLayout(this).apply {
                        val bg = GradientDrawable().apply {
                            setColor(Color.parseColor("#ECEDE8DF"))
                            cornerRadius = 24f
                            setStroke(2, Color.parseColor("#331C1C1E"))
                        }
                        background = bg
                        isClickable = true
                        isFocusable = false
                        setOnClickListener { showRateLimitedToast(blockMsg) }
                    }
                    val icon = ImageView(this).apply {
                        setImageResource(iconRes)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        alpha = 0.88f
                    }
                    val iconSize = (minOf(rect.width(), rect.height()) * 0.56f).toInt().coerceIn(36, 84)
                    container.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))

                    val lp = WindowManager.LayoutParams(
                        rect.width(),
                        rect.height(),
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.LEFT
                        x = rect.left
                        y = rect.top
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }
                    wm.addView(container, lp)
                    tabLockOverlay = container
                    tabLockParams = lp
                    lastOverlayRect.set(rect)
                } else {
                    val lp = tabLockParams ?: return@post
                    lp.x = rect.left
                    lp.y = rect.top
                    lp.width = rect.width()
                    lp.height = rect.height()
                    wm.updateViewLayout(tabLockOverlay, lp)
                    lastOverlayRect.set(rect)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Overlay error: ${e.message}")
                tabLockOverlay = null
                tabLockParams = null
                lastOverlayRect.setEmpty()
            }
        }
    }

    private fun removeTabLockOverlay() {
        mainHandler.post {
            val overlay = tabLockOverlay ?: return@post
            try {
                if (overlay.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(overlay)
                }
            } catch (_: Exception) {
            } finally {
                tabLockOverlay = null
                tabLockParams = null
                lastOverlayRect.setEmpty()
            }
        }
    }

    private fun isNodeActuallyOnScreen(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val dm = resources.displayMetrics
        return rect.width() > 20 && rect.height() > 20 &&
            rect.right > 16 && rect.left < (dm.widthPixels - 16) &&
            rect.bottom > 16 && rect.top < (dm.heightPixels + 100)
    }

    private fun hasVisibleOnScreenViewId(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                for (n in list) {
                    if (isNodeActuallyOnScreen(n)) return true
                }
            }
        }
        return false
    }

    private fun findVisibleViewById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByViewId(id) ?: return null
        return list.firstOrNull { isNodeActuallyOnScreen(it) }
    }

    private fun hasVisibleViewIdContainsAny(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 95) {
            val node = queue.removeFirst()
            visited++
            val vid = node.viewIdResourceName
            if (vid != null && isNodeActuallyOnScreen(node)) {
                for (n in needles) {
                    if (vid.contains(n, ignoreCase = true)) return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun findBottomTabNode(
        root: AccessibilityNodeInfo,
        barViewId: String,
        keyword: String
    ): AccessibilityNodeInfo? {
        val bar = findVisibleViewById(root, barViewId) ?: root
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(bar)
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val node = queue.removeFirst()
            visited++
            val cd = node.contentDescription?.toString()?.lowercase() ?: ""
            val txt = node.text?.toString()?.lowercase() ?: ""
            if (isNodeActuallyOnScreen(node) && (cd.contains(keyword) || txt.contains(keyword))) {
                var target: AccessibilityNodeInfo? = node
                var hops = 0
                while (target != null && !target.isClickable && hops < 3) {
                    target = target.parent
                    hops++
                }
                return if (target != null && target.isClickable && isNodeActuallyOnScreen(target)) target else node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {
        watchdogRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        removeTabLockOverlay()
        removeCounterOverlay()
    }

    override fun onDestroy() {
        watchdogRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        removeTabLockOverlay()
        removeCounterOverlay()
        BlockerKeepAliveService.stop(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }
}
