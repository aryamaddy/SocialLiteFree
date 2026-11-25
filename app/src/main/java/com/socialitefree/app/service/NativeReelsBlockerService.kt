package com.socialitefree.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.socialitefree.app.R
import com.socialitefree.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Persistent Accessibility-powered blocker for native social apps.
 *
 * Improvements over v1:
 * - Multi-window lookup (ignores overlays/toasts so root window is never lost).
 * - Reliable repeat blocking via GLOBAL_ACTION_BACK + 250ms active watchdog while inside monitored apps.
 * - Smart Sent-Reel mode: allows opening 1 Reel from Instagram DM, but immediately exits if user scrolls!
 * - Improved YouTube Shorts tab & player detection across modern YouTube app builds.
 */
class NativeReelsBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "NativeReelsBlocker"
        private const val PKG_INSTAGRAM = "com.instagram.android"
        private const val PKG_YOUTUBE = "com.google.android.youtube"
        private const val PKG_SNAPCHAT = "com.snapchat.android"
        private const val PKG_FACEBOOK = "com.facebook.katana"

        private val MONITORED_PACKAGES = setOf(
            PKG_INSTAGRAM, PKG_YOUTUBE, PKG_SNAPCHAT, PKG_FACEBOOK
        )

        private const val ACTION_COOLDOWN_MS = 380L
        private const val TOAST_COOLDOWN_MS = 2200L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val DM_TO_REEL_WINDOW_MS = 5500L
        private const val SENT_REEL_SCROLL_GRACE_MS = 650L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefsManager: PreferencesManager
    private var windowManager: WindowManager? = null

    @Volatile private var blockIgReels = true
    @Volatile private var allowSentReels = true
    @Volatile private var lockIgReelsTab = true
    @Volatile private var blockIgExplore = false
    @Volatile private var blockYtShorts = true
    @Volatile private var blockFbReels = true
    @Volatile private var blockSnapSpotlight = true

    private var lastActionAtMs = 0L
    private var lastToastAtMs = 0L
    private var currentForegroundPkg: String? = null

    // Sent Reel (1-reel DM session, block vertical scrolling)
    private var lastInDmAtMs = 0L
    private var inSentReelSession = false
    private var sentReelEnteredAtMs = 0L
    private var sentReelInitialAuthor: String? = null

    // Tab lock overlay
    private var tabLockOverlay: View? = null
    private var tabLockParams: WindowManager.LayoutParams? = null
    private val lastOverlayRect = Rect()

    private var watchdogRunning = false
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val pkg = currentForegroundPkg
            if (pkg == null || pkg !in MONITORED_PACKAGES) {
                watchdogRunning = false
                removeTabLockOverlay()
                return
            }
            try {
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
        serviceScope.launch { prefsManager.nativeBlockYtShorts.collectLatest { blockYtShorts = it } }
        serviceScope.launch { prefsManager.nativeBlockFbReels.collectLatest { blockFbReels = it } }
        serviceScope.launch { prefsManager.nativeBlockSnapSpotlight.collectLatest { blockSnapSpotlight = it } }

        Log.i(TAG, "SocialLite NativeReelsBlockerService connected (v2)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own overlay / keyboard / transient toast events so we don't clear monitored state falsely
        if (pkg == packageName || pkg.contains("inputmethod", ignoreCase = true)) {
            return
        }

        if (pkg !in MONITORED_PACKAGES) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentForegroundPkg = pkg
                resetSentReelState()
                removeTabLockOverlay()
            }
            return
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

    private fun ensureWatchdogStarted() {
        if (!watchdogRunning) {
            watchdogRunning = true
            mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
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
        when (pkg) {
            PKG_INSTAGRAM -> handleInstagram(event, root)
            PKG_YOUTUBE -> handleYouTube(root)
            PKG_SNAPCHAT -> handleSnapchat(root)
            PKG_FACEBOOK -> handleFacebook(root)
        }
    }

    private fun handleInstagram(event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        if (!blockIgReels && !blockIgExplore) {
            removeTabLockOverlay()
            return
        }

        val now = System.currentTimeMillis()
        val eventClass = event?.className?.toString() ?: ""

        // Track when user is inside Instagram DMs (Inbox or Chat Thread)
        if (root != null) {
            val inDm = hasAnyViewId(
                root,
                listOf(
                    "com.instagram.android:id/row_thread_composer_edittext",
                    "com.instagram.android:id/direct_inbox_action_bar",
                    "com.instagram.android:id/direct_thread_recycler_view"
                )
            )
            if (inDm) {
                lastInDmAtMs = now
            }
        }

        // Detect Instagram Reels viewer (explicitly excludes Stories which use reel_viewer_root)
        val isClipsActivity = eventClass.contains("ClipsUrlHandlerActivity", ignoreCase = true) ||
            eventClass.contains("ClipsViewer", ignoreCase = true)

        val inReelsViewer = isClipsActivity || (root != null && hasAnyViewId(
            root,
            listOf(
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_video_layout",
                "com.instagram.android:id/clips_author_username"
            )
        ))

        if (blockIgReels && inReelsViewer) {
            removeTabLockOverlay()

            // Determine if this entry came directly from a DM sent Reel
            if (!inSentReelSession) {
                val cameFromDm = allowSentReels && (now - lastInDmAtMs) <= DM_TO_REEL_WINDOW_MS
                if (cameFromDm) {
                    inSentReelSession = true
                    sentReelEnteredAtMs = now
                    sentReelInitialAuthor = readAuthorUsername(root)
                    return
                } else {
                    // Direct Reels tab / feed Reel / Explore Reel -> block & exit immediately!
                    triggerBackExit("Reels blocked by SocialLite")
                    return
                }
            }

            // Inside an allowed 1-Reel DM session: monitor for ANY vertical scroll or author change!
            if (sentReelInitialAuthor == null) {
                sentReelInitialAuthor = readAuthorUsername(root)
            }

            val elapsed = now - sentReelEnteredAtMs
            if (elapsed > SENT_REEL_SCROLL_GRACE_MS) {
                val scrolledEvent = event != null &&
                    event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                    isReelsScrollEvent(event)

                val currentAuthor = readAuthorUsername(root)
                val authorChanged = sentReelInitialAuthor != null &&
                    currentAuthor != null &&
                    currentAuthor != sentReelInitialAuthor

                if (scrolledEvent || authorChanged) {
                    resetSentReelState()
                    triggerBackExit("Reel scrolling blocked by SocialLite")
                }
            }
            return
        }

        // Left Reels viewer cleanly -> reset sent Reel session so future Reels are evaluated fresh
        if (!inReelsViewer && inSentReelSession) {
            resetSentReelState()
        }

        // Block Explore if enabled
        if (blockIgExplore && root != null) {
            if (hasViewId(root, "com.instagram.android:id/explore_action_bar")) {
                removeTabLockOverlay()
                triggerBackExit("Explore blocked by SocialLite")
                return
            }
        }

        // Cover / Lock the Reels tab button in Instagram bottom bar
        if (blockIgReels && lockIgReelsTab && root != null) {
            val reelsTab = findFirstByViewId(root, "com.instagram.android:id/clips_tab")
                ?: findBottomTabNode(root, "com.instagram.android:id/tab_bar", "reel")
            if (reelsTab != null && reelsTab.isVisibleToUser) {
                val rect = Rect()
                reelsTab.getBoundsInScreen(rect)
                if (rect.width() > 16 && rect.height() > 16 && rect.top > 250) {
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

    private fun isReelsScrollEvent(event: AccessibilityEvent): Boolean {
        val srcId = event.source?.viewIdResourceName ?: ""
        if (srcId.contains("clips_viewer", ignoreCase = true) ||
            srcId.contains("view_pager", ignoreCase = true)
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && abs(event.scrollDeltaY) > 25) {
            return true
        }
        if (event.fromIndex >= 0 && event.toIndex >= 0 && event.toIndex != event.fromIndex) {
            return true
        }
        return false
    }

    private fun readAuthorUsername(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val node = findFirstByViewId(root, "com.instagram.android:id/clips_author_username")
        return node?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun resetSentReelState() {
        inSentReelSession = false
        sentReelEnteredAtMs = 0L
        sentReelInitialAuthor = null
    }

    private fun handleYouTube(root: AccessibilityNodeInfo?) {
        if (!blockYtShorts || root == null) {
            removeTabLockOverlay()
            return
        }

        val inShortsPlayer = hasAnyViewId(
            root,
            listOf(
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/shorts_player_container",
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/reel_vod_player_container",
                "com.google.android.youtube:id/reel_watch_player"
            )
        ) || hasViewIdContainsAny(root, listOf("reel_player", "shorts_player", "reel_watch", "reel_recycler"))

        if (inShortsPlayer) {
            removeTabLockOverlay()
            triggerBackExit("YouTube Shorts blocked by SocialLite")
            return
        }

        // Cover Shorts tab in bottom pivot_bar
        val shortsTab = findBottomTabNode(root, "com.google.android.youtube:id/pivot_bar", "short")
        if (shortsTab != null && shortsTab.isVisibleToUser) {
            val rect = Rect()
            shortsTab.getBoundsInScreen(rect)
            if (rect.width() > 16 && rect.height() > 16 && rect.top > 250) {
                showOrMoveTabLockOverlay(
                    rect = rect,
                    iconRes = R.drawable.ig_reels_tab_locked,
                    blockMsg = "Shorts are blocked by SocialLite"
                )
                return
            }
        }
        removeTabLockOverlay()
    }

    private fun handleSnapchat(root: AccessibilityNodeInfo?) {
        if (!blockSnapSpotlight || root == null) {
            removeTabLockOverlay()
            return
        }

        if (hasViewId(root, "com.snapchat.android:id/spotlight_container")) {
            removeTabLockOverlay()
            triggerBackExit("Spotlight blocked by SocialLite")
            return
        }

        val spotTab = findFirstByViewId(root, "com.snapchat.android:id/ngs_spotlight_icon_container")
        if (spotTab != null && spotTab.isVisibleToUser) {
            val rect = Rect()
            spotTab.getBoundsInScreen(rect)
            if (rect.width() > 16 && rect.height() > 16 && rect.top > 250) {
                showOrMoveTabLockOverlay(
                    rect = rect,
                    iconRes = R.drawable.snapchat_spotlight_tab_locked,
                    blockMsg = "Spotlight is blocked by SocialLite"
                )
                return
            }
        }
        removeTabLockOverlay()
    }

    private fun handleFacebook(root: AccessibilityNodeInfo?) {
        if (!blockFbReels || root == null) {
            removeTabLockOverlay()
            return
        }
        if (hasViewIdContainsAny(root, listOf("reels_viewer", "fb_shorts"))) {
            removeTabLockOverlay()
            triggerBackExit("Facebook Reels blocked by SocialLite")
        } else {
            removeTabLockOverlay()
        }
    }

    private fun triggerBackExit(toastMessage: String) {
        val now = System.currentTimeMillis()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        serviceScope.launch { prefsManager.incrementNativeBlockedCount() }
        showRateLimitedToast(toastMessage)
    }

    private fun showRateLimitedToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastAtMs < TOAST_COOLDOWN_MS) return
        lastToastAtMs = now
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun showOrMoveTabLockOverlay(rect: Rect, iconRes: Int, blockMsg: String) {
        val wm = windowManager ?: return
        mainHandler.post {
            try {
                if (tabLockOverlay != null && lastOverlayRect == rect) {
                    return@post
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
                        setOnClickListener {
                            showRateLimitedToast(blockMsg)
                        }
                    }

                    val icon = ImageView(this).apply {
                        setImageResource(iconRes)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        alpha = 0.88f
                    }
                    val iconSize = (minOf(rect.width(), rect.height()) * 0.56f).toInt().coerceIn(36, 84)
                    container.addView(
                        icon,
                        FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                    )

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
            }
        }
    }

    private fun removeTabLockOverlay() {
        val overlay = tabLockOverlay ?: return
        mainHandler.post {
            try {
                windowManager?.removeViewImmediate(overlay)
            } catch (_: Exception) {
            } finally {
                tabLockOverlay = null
                tabLockParams = null
                lastOverlayRect.setEmpty()
            }
        }
    }

    private fun hasViewId(root: AccessibilityNodeInfo, id: String): Boolean {
        val list = root.findAccessibilityNodeInfosByViewId(id)
        return !list.isNullOrEmpty()
    }

    private fun hasAnyViewId(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) return true
        }
        return false
    }

    private fun findFirstByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByViewId(id)
        return list?.firstOrNull()
    }

    private fun hasViewIdContainsAny(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 95) {
            val node = queue.removeFirst()
            visited++
            val vid = node.viewIdResourceName
            if (vid != null) {
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
        val bar = findFirstByViewId(root, barViewId) ?: root
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(bar)
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val node = queue.removeFirst()
            visited++
            val cd = node.contentDescription?.toString()?.lowercase() ?: ""
            val txt = node.text?.toString()?.lowercase() ?: ""
            if (cd.contains(keyword) || txt.contains(keyword)) {
                // Walk up to clickable parent tab container if child itself is not clickable
                var target: AccessibilityNodeInfo? = node
                var hops = 0
                while (target != null && !target.isClickable && hops < 3) {
                    target = target.parent
                    hops++
                }
                return if (target != null && target.isClickable) target else node
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
    }

    override fun onDestroy() {
        watchdogRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        removeTabLockOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}
