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
 * Persistent Accessibility-powered blocker for native social apps (v3).
 *
 * Key reliability fixes:
 * - Only treats views as active when truly visible inside screen bounds (ignores off-screen
 *   Home <-> DM ViewPager fragments in Instagram, fixing the "works 1st time, fails 2nd time" bug).
 * - Checks attached state on tabLockOverlay so stale detached overlays never block re-attaching.
 * - Starts BlockerKeepAliveService so OEM battery killers don't stop blocking when SocialLite closes.
 * - Enforces 1-Reel DM Sent-Reel viewing with multi-signal scroll detection (TYPE_VIEW_SCROLLED,
 *   author change, and pager child signature change).
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

        private const val ACTION_COOLDOWN_MS = 350L
        private const val TOAST_COOLDOWN_MS = 2200L
        private const val WATCHDOG_INTERVAL_MS = 220L
        private const val DM_TO_REEL_WINDOW_MS = 5000L
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
    private var sentReelInitialSignature: String? = null

    // Tab lock overlay (always mutated strictly on mainHandler)
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

        // Keep process alive when SocialLite is closed from Recents
        BlockerKeepAliveService.start(applicationContext)
        Log.i(TAG, "SocialLite NativeReelsBlockerService connected (v3)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

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

        // Check if user is actively in a DM chat thread (strict on-screen bounds check!)
        val inDmChatOnScreen = root != null && hasVisibleOnScreenViewId(
            root,
            listOf(
                "com.instagram.android:id/row_thread_composer_edittext",
                "com.instagram.android:id/direct_thread_recycler_view"
            )
        )
        if (inDmChatOnScreen) {
            lastInDmAtMs = now
        }

        // Detect Instagram Reels viewer (strict on-screen check, excludes Stories)
        val isClipsActivity = eventClass.contains("ClipsUrlHandlerActivity", ignoreCase = true) ||
            eventClass.contains("ClipsViewer", ignoreCase = true)

        val inReelsViewer = isClipsActivity || (root != null && hasVisibleOnScreenViewId(
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

            if (!inSentReelSession) {
                val cameFromDm = allowSentReels && (now - lastInDmAtMs) <= DM_TO_REEL_WINDOW_MS
                if (cameFromDm) {
                    inSentReelSession = true
                    sentReelEnteredAtMs = now
                    sentReelInitialSignature = readReelsContentSignature(root)
                    return
                } else {
                    triggerBackExit("Reels blocked by SocialLite")
                    return
                }
            }

            // Inside 1-Reel DM session: capture signature & block any scroll attempt!
            if (sentReelInitialSignature == null) {
                sentReelInitialSignature = readReelsContentSignature(root)
            }

            val elapsed = now - sentReelEnteredAtMs
            if (elapsed > SENT_REEL_SCROLL_GRACE_MS) {
                val scrolledEvent = event != null &&
                    event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                    isReelsScrollEvent(event)

                val currentSig = readReelsContentSignature(root)
                val contentChanged = sentReelInitialSignature != null &&
                    currentSig != null &&
                    currentSig != sentReelInitialSignature

                if (scrolledEvent || contentChanged) {
                    resetSentReelState()
                    lastInDmAtMs = 0L
                    triggerBackExit("Reel scrolling blocked by SocialLite")
                }
            }
            return
        }

        // Outside Reels viewer: reset sent session immediately
        if (inSentReelSession) {
            resetSentReelState()
        }

        // If user is on Home / Feed / Search tab (not in a DM chat), clear DM timestamp so
        // tapping a feed Reel or Reels tab can never be mistaken for a DM sent Reel!
        if (!inDmChatOnScreen && root != null) {
            val onMainTabs = hasVisibleOnScreenViewId(
                root,
                listOf(
                    "com.instagram.android:id/tab_bar",
                    "com.instagram.android:id/feed_tab",
                    "com.instagram.android:id/search_tab"
                )
            )
            if (onMainTabs) {
                lastInDmAtMs = 0L
            }
        }

        // Block Explore if enabled
        if (blockIgExplore && root != null) {
            if (hasVisibleOnScreenViewId(root, listOf("com.instagram.android:id/explore_action_bar"))) {
                removeTabLockOverlay()
                triggerBackExit("Explore blocked by SocialLite")
                return
            }
        }

        // Lock Reels tab button in Instagram bottom bar
        if (blockIgReels && lockIgReelsTab && root != null) {
            val reelsTab = findVisibleViewById(root, "com.instagram.android:id/clips_tab")
                ?: findBottomTabNode(root, "com.instagram.android:id/tab_bar", "reel")
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

    private fun isReelsScrollEvent(event: AccessibilityEvent): Boolean {
        val srcId = event.source?.viewIdResourceName ?: ""
        val srcCls = event.source?.className?.toString() ?: ""
        if (srcId.contains("clips_viewer", ignoreCase = true) ||
            srcId.contains("view_pager", ignoreCase = true) ||
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

    private fun readReelsContentSignature(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val author = findVisibleViewById(root, "com.instagram.android:id/clips_author_username")
            ?.text?.toString()?.trim()
        if (!author.isNullOrEmpty()) return "author:$author"

        // Fallback: scan visible text inside clips_viewer_container to detect reel swap
        val viewer = findVisibleViewById(root, "com.instagram.android:id/clips_viewer_container") ?: return null
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

    private fun resetSentReelState() {
        inSentReelSession = false
        sentReelEnteredAtMs = 0L
        sentReelInitialSignature = null
    }

    private fun handleYouTube(root: AccessibilityNodeInfo?) {
        if (!blockYtShorts || root == null) {
            removeTabLockOverlay()
            return
        }

        val inShortsPlayer = hasVisibleOnScreenViewId(
            root,
            listOf(
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/shorts_player_container",
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/reel_vod_player_container",
                "com.google.android.youtube:id/reel_watch_player"
            )
        ) || hasVisibleViewIdContainsAny(root, listOf("reel_player", "shorts_player", "reel_watch", "reel_recycler"))

        if (inShortsPlayer) {
            removeTabLockOverlay()
            triggerBackExit("YouTube Shorts blocked by SocialLite")
            return
        }

        // Cover Shorts tab in bottom pivot_bar
        val shortsTab = findBottomTabNode(root, "com.google.android.youtube:id/pivot_bar", "short")
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
        removeTabLockOverlay()
    }

    private fun handleSnapchat(root: AccessibilityNodeInfo?) {
        if (!blockSnapSpotlight || root == null) {
            removeTabLockOverlay()
            return
        }

        if (hasVisibleOnScreenViewId(root, listOf("com.snapchat.android:id/spotlight_container"))) {
            removeTabLockOverlay()
            triggerBackExit("Spotlight blocked by SocialLite")
            return
        }

        val spotTab = findVisibleViewById(root, "com.snapchat.android:id/ngs_spotlight_icon_container")
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
        removeTabLockOverlay()
    }

    private fun handleFacebook(root: AccessibilityNodeInfo?) {
        if (!blockFbReels || root == null) {
            removeTabLockOverlay()
            return
        }
        if (hasVisibleViewIdContainsAny(root, listOf("reels_viewer", "fb_shorts"))) {
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
                val existing = tabLockOverlay
                if (existing != null && existing.isAttachedToWindow && lastOverlayRect == rect) {
                    return@post
                }

                // Clean up stale detached reference if WindowManager evicted it
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
    }

    override fun onDestroy() {
        watchdogRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        removeTabLockOverlay()
        BlockerKeepAliveService.stop(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }
}
