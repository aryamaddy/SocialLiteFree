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

/**
 * Accessibility-powered native app blocker.
 *
 * Inspects live UI node hierarchies of installed social apps (Instagram, YouTube,
 * Facebook, Snapchat) to lock Reels/Shorts tabs with a touch-intercepting overlay
 * and automatically exit Reels/Shorts playback or scroll loops.
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

        private const val ACTION_COOLDOWN_MS = 550L
        private const val TOAST_COOLDOWN_MS = 2500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefsManager: PreferencesManager
    private var windowManager: WindowManager? = null

    @Volatile private var blockIgReels = true
    @Volatile private var lockIgReelsTab = true
    @Volatile private var blockIgExplore = false
    @Volatile private var blockYtShorts = true
    @Volatile private var blockFbReels = true
    @Volatile private var blockSnapSpotlight = true

    private var lastActionAtMs = 0L
    private var lastToastAtMs = 0L
    private var currentForegroundPkg: String? = null

    // Floating tab-lock overlay covering Reels / Shorts tab inside native apps
    private var tabLockOverlay: View? = null
    private var tabLockParams: WindowManager.LayoutParams? = null
    private val lastOverlayRect = Rect()

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefsManager = PreferencesManager.getInstance(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

        serviceScope.launch {
            prefsManager.nativeBlockIgReels.collectLatest { blockIgReels = it }
        }
        serviceScope.launch {
            prefsManager.nativeLockIgReelsTab.collectLatest { lockIgReelsTab = it }
        }
        serviceScope.launch {
            prefsManager.nativeBlockIgExplore.collectLatest { blockIgExplore = it }
        }
        serviceScope.launch {
            prefsManager.nativeBlockYtShorts.collectLatest { blockYtShorts = it }
        }
        serviceScope.launch {
            prefsManager.nativeBlockFbReels.collectLatest { blockFbReels = it }
        }
        serviceScope.launch {
            prefsManager.nativeBlockSnapSpotlight.collectLatest { blockSnapSpotlight = it }
        }

        Log.i(TAG, "SocialLite NativeReelsBlockerService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg != currentForegroundPkg && pkg !in MONITORED_PACKAGES && !pkg.contains("inputmethod")) {
            currentForegroundPkg = pkg
            removeTabLockOverlay()
            return
        }

        if (pkg !in MONITORED_PACKAGES) return
        currentForegroundPkg = pkg

        try {
            val root = rootInActiveWindow
            when (pkg) {
                PKG_INSTAGRAM -> handleInstagram(event, root)
                PKG_YOUTUBE -> handleYouTube(event, root)
                PKG_SNAPCHAT -> handleSnapchat(root)
                PKG_FACEBOOK -> handleFacebook(root)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onAccessibilityEvent error swallowed to keep service alive: ${t.message}")
        }
    }

    private fun handleInstagram(event: AccessibilityEvent, root: AccessibilityNodeInfo?) {
        if (!blockIgReels && !blockIgExplore) {
            removeTabLockOverlay()
            return
        }

        val eventClass = event.className?.toString() ?: ""

        // 1. Detect Clips/Reels Viewer immediately (never block Stories which use reel_viewer_root)
        if (blockIgReels) {
            val isClipsActivity = eventClass.contains("ClipsUrlHandlerActivity", ignoreCase = true) ||
                eventClass.contains("ClipsViewer", ignoreCase = true)

            val inReelsViewer = isClipsActivity || (root != null && (
                hasViewId(root, "com.instagram.android:id/clips_viewer_container") ||
                    hasViewId(root, "com.instagram.android:id/clips_viewer_view_pager") ||
                    hasViewId(root, "com.instagram.android:id/clips_viewer_video_layout") ||
                    hasViewId(root, "com.instagram.android:id/clips_author_username")
                ))

            if (inReelsViewer) {
                removeTabLockOverlay()
                triggerInstagramExit("Reels blocked by SocialLite", root)
                return
            }

            // Extra scroll guard if user scrolls inside a Reels pager
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val srcId = event.source?.viewIdResourceName ?: ""
                if (srcId.contains("clips_viewer", ignoreCase = true)) {
                    removeTabLockOverlay()
                    triggerInstagramExit("Reels scrolling disabled", root)
                    return
                }
            }
        }

        // 2. Detect Explore tab / search & explore if enabled
        if (blockIgExplore && root != null) {
            if (hasViewId(root, "com.instagram.android:id/explore_action_bar")) {
                removeTabLockOverlay()
                triggerInstagramExit("Explore blocked by SocialLite", root)
                return
            }
        }

        // 3. Cover / Lock the Reels tab button in the bottom navigation bar
        if (blockIgReels && lockIgReelsTab && root != null) {
            val reelsTab = findFirstByViewId(root, "com.instagram.android:id/clips_tab")
                ?: findBottomTabByDescription(root, "com.instagram.android:id/tab_bar", "reel")
            if (reelsTab != null && reelsTab.isVisibleToUser) {
                val rect = Rect()
                reelsTab.getBoundsInScreen(rect)
                if (rect.width() > 16 && rect.height() > 16 && rect.top > 200) {
                    showOrMoveTabLockOverlay(
                        rect = rect,
                        iconRes = R.drawable.ig_reels_tab_locked,
                        blockMsg = "Reels are blocked by SocialLite"
                    )
                } else {
                    removeTabLockOverlay()
                }
            } else {
                removeTabLockOverlay()
            }
        } else {
            removeTabLockOverlay()
        }
    }

    private fun handleYouTube(event: AccessibilityEvent, root: AccessibilityNodeInfo?) {
        if (!blockYtShorts) {
            removeTabLockOverlay()
            return
        }

        if (root != null) {
            val inShorts = hasViewId(root, "com.google.android.youtube:id/reel_player_page_container") ||
                hasViewId(root, "com.google.android.youtube:id/reel_recycler") ||
                hasViewId(root, "com.google.android.youtube:id/shorts_player_container")

            if (inShorts) {
                removeTabLockOverlay()
                triggerGlobalBack("Shorts blocked by SocialLite")
                return
            }

            // Cover Shorts tab in bottom pivot_bar
            val shortsTab = findBottomTabByDescription(root, "com.google.android.youtube:id/pivot_bar", "short")
            if (shortsTab != null && shortsTab.isVisibleToUser) {
                val rect = Rect()
                shortsTab.getBoundsInScreen(rect)
                if (rect.width() > 16 && rect.height() > 16 && rect.top > 200) {
                    showOrMoveTabLockOverlay(
                        rect = rect,
                        iconRes = R.drawable.ig_reels_tab_locked,
                        blockMsg = "Shorts are blocked by SocialLite"
                    )
                } else {
                    removeTabLockOverlay()
                }
            } else {
                removeTabLockOverlay()
            }
        }
    }

    private fun handleSnapchat(root: AccessibilityNodeInfo?) {
        if (!blockSnapSpotlight || root == null) {
            removeTabLockOverlay()
            return
        }

        if (hasViewId(root, "com.snapchat.android:id/spotlight_container")) {
            removeTabLockOverlay()
            triggerGlobalBack("Spotlight blocked by SocialLite")
            return
        }

        val spotTab = findFirstByViewId(root, "com.snapchat.android:id/ngs_spotlight_icon_container")
        if (spotTab != null && spotTab.isVisibleToUser) {
            val rect = Rect()
            spotTab.getBoundsInScreen(rect)
            if (rect.width() > 16 && rect.height() > 16 && rect.top > 200) {
                showOrMoveTabLockOverlay(
                    rect = rect,
                    iconRes = R.drawable.snapchat_spotlight_tab_locked,
                    blockMsg = "Spotlight is blocked by SocialLite"
                )
            } else {
                removeTabLockOverlay()
            }
        } else {
            removeTabLockOverlay()
        }
    }

    private fun handleFacebook(root: AccessibilityNodeInfo?) {
        if (!blockFbReels || root == null) {
            removeTabLockOverlay()
            return
        }
        val reelsViewer = findFirstByDescriptionExactOrPrefix(root, "Reels")
        // Only act if we are clearly inside a full-screen Reels viewer surface
        if (reelsViewer != null && hasViewIdContains(root, "reels_viewer")) {
            removeTabLockOverlay()
            triggerGlobalBack("Facebook Reels blocked by SocialLite")
        } else {
            removeTabLockOverlay()
        }
    }

    private fun triggerInstagramExit(toastMessage: String, root: AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now

        // First try switching cleanly to Home feed tab if visible, otherwise press Back
        val feedTab = root?.let { findFirstByViewId(it, "com.instagram.android:id/feed_tab") }
        val clickedFeed = feedTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!clickedFeed) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        onBlockedAction(toastMessage)
    }

    private fun triggerGlobalBack(toastMessage: String) {
        val now = System.currentTimeMillis()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        onBlockedAction(toastMessage)
    }

    private fun onBlockedAction(toastMessage: String) {
        serviceScope.launch {
            prefsManager.incrementNativeBlockedCount()
        }
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
                            setColor(Color.parseColor("#E6F4F0EA"))
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
                        alpha = 0.85f
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

    private fun findFirstByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByViewId(id)
        return list?.firstOrNull()
    }

    private fun hasViewIdContains(root: AccessibilityNodeInfo, needle: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val node = queue.removeFirst()
            visited++
            val vid = node.viewIdResourceName
            if (vid != null && vid.contains(needle, ignoreCase = true)) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun findFirstByDescriptionExactOrPrefix(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 60) {
            val node = queue.removeFirst()
            visited++
            val cd = node.contentDescription?.toString()
            if (cd != null && cd.equals(desc, ignoreCase = true)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findBottomTabByDescription(
        root: AccessibilityNodeInfo,
        barViewId: String,
        keyword: String
    ): AccessibilityNodeInfo? {
        val bar = findFirstByViewId(root, barViewId) ?: root
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(bar)
        var visited = 0
        while (queue.isNotEmpty() && visited < 60) {
            val node = queue.removeFirst()
            visited++
            val cd = node.contentDescription?.toString()?.lowercase() ?: ""
            val txt = node.text?.toString()?.lowercase() ?: ""
            if (node.isClickable && (cd.contains(keyword) || txt.contains(keyword))) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {
        removeTabLockOverlay()
    }

    override fun onDestroy() {
        removeTabLockOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}
