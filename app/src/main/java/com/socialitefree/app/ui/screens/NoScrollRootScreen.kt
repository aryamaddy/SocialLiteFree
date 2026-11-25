package com.socialitefree.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.R
import com.socialitefree.app.data.*
import com.socialitefree.app.ui.components.CuriousConfigDialog
import com.socialitefree.app.ui.components.MindfulWaitPauseDialog
import com.socialitefree.app.ui.components.PinVerifyDialog
import com.socialitefree.app.ui.theme.*
import com.socialitefree.app.update.AppUpdateInfo
import com.socialitefree.app.update.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NoScrollTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Home),
    BLOCKER("Blocker", Icons.Rounded.Shield),
    ANALYTICS("Analytics", Icons.Rounded.BarChart),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoScrollRootScreen(
    prefsManager: PreferencesManager,
    onNavigateToWebPlatform: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings = rememberAppSettings(prefsManager)

    var selectedTab by remember { mutableStateOf(NoScrollTab.DASHBOARD) }
    var a11yEnabled by remember { mutableStateOf(NativeAppHelper.isAccessibilityEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(NativeAppHelper.isIgnoringBatteryOptimizations(context)) }
    var uninstallGuardActive by remember { mutableStateOf(NativeAppHelper.isUninstallGuardActive(context)) }

    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showCuriousDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val usageMap by prefsManager.getAllUsageSeconds().collectAsState(initial = emptyMap())
    val nativeBlockedCount by prefsManager.nativeBlockedCount.collectAsState(initial = 0)
    val todayBlocksCount by prefsManager.todayBlocksCount.collectAsState(initial = 0)
    val todayReelsScrolled by prefsManager.todayReelsScrolled.collectAsState(initial = 0)
    val blockHistoryJson by prefsManager.blockHistoryJson.collectAsState(initial = "[]")

    // Tick every 1s for live Pause / Curious countdowns
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.blockerPlan, settings.pauseUntilMs, settings.curiousCooldownUntilMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        availableUpdate = UpdateChecker.checkForUpdate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = NativeAppHelper.isAccessibilityEnabled(context)
                batteryExempt = NativeAppHelper.isIgnoringBatteryOptimizations(context)
                uninstallGuardActive = NativeAppHelper.isUninstallGuardActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun guardWithPin(requirePin: Boolean, onAllowed: () -> Unit) {
        if (requirePin && settings.pinType != PinType.NONE) {
            pendingPinAction = onAllowed
        } else {
            onAllowed()
        }
    }

    fun selectTabGuarded(tab: NoScrollTab) {
        if (tab == NoScrollTab.SETTINGS && settings.pinProtectSettings && settings.pinType != PinType.NONE) {
            guardWithPin(true) { selectedTab = tab }
        } else {
            selectedTab = tab
        }
    }

    fun handleSelectPlan(plan: BlockerPlan) {
        val cooldownLocked = settings.blockerPlan == BlockerPlan.CURIOUS &&
            settings.curiousLockSwitchInCooldown &&
            settings.curiousCooldownUntilMs > nowMs &&
            (nowMs - settings.curiousSessionStartMs) >= settings.curiousSessionMinutes * 60_000L
        if (cooldownLocked && plan != BlockerPlan.CURIOUS) {
            Toast.makeText(context, "Curious Cooldown active — plan switch locked until cooldown ends", Toast.LENGTH_SHORT).show()
            return
        }
        val action: () -> Unit = {
            when (plan) {
                BlockerPlan.CURIOUS -> showCuriousDialog = true
                BlockerPlan.PAUSED -> showPauseDialog = true
                else -> scope.launch { prefsManager.switchPlan(plan) }
            }
        }
        guardWithPin(settings.pinProtectPlanSwitch && plan != BlockerPlan.BLOCK_ALL, action)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NoScrollTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectTabGuarded(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) NoScrollRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NoScrollRedSoft
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                NoScrollTab.DASHBOARD -> NoScrollDashboardTab(
                    settings = settings,
                    nowMs = nowMs,
                    a11yEnabled = a11yEnabled,
                    batteryExempt = batteryExempt,
                    todayBlocksCount = todayBlocksCount,
                    availableUpdate = availableUpdate,
                    prefsManager = prefsManager,
                    onSelectPlan = ::handleSelectPlan,
                    onOpenSupport = { showSupportSheet = true },
                    onOpenUpdate = { showUpdateDialog = true },
                    onOpenBlockerWebsites = { selectedTab = NoScrollTab.BLOCKER },
                    onLaunchWebPlatform = onNavigateToWebPlatform
                )
                NoScrollTab.BLOCKER -> NoScrollBlockerTab(
                    settings = settings,
                    prefsManager = prefsManager,
                    uninstallGuardActive = uninstallGuardActive,
                    onRefreshAdmin = {
                        uninstallGuardActive = NativeAppHelper.isUninstallGuardActive(context)
                    }
                )
                NoScrollTab.ANALYTICS -> NoScrollAnalyticsTab(
                    settings = settings,
                    usageMap = usageMap,
                    todayBlocksCount = todayBlocksCount,
                    totalBlocksCount = nativeBlockedCount,
                    todayReelsScrolled = todayReelsScrolled,
                    blockHistoryJson = blockHistoryJson,
                    prefsManager = prefsManager
                )
                NoScrollTab.SETTINGS -> NoScrollSettingsTab(
                    settings = settings,
                    a11yEnabled = a11yEnabled,
                    batteryExempt = batteryExempt,
                    uninstallGuardActive = uninstallGuardActive,
                    prefsManager = prefsManager,
                    onOpenSupport = { showSupportSheet = true },
                    onCheckUpdateNow = {
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate()
                            if (info != null) {
                                availableUpdate = info
                                showUpdateDialog = true
                            } else {
                                Toast.makeText(context, "You're on the latest version (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showPauseDialog) {
        MindfulWaitPauseDialog(
            initialWaitSeconds = settings.pauseWaitSeconds,
            defaultDurationMinutes = settings.pauseDurationMinutes,
            cooldownUntilMs = settings.pauseCooldownUntilMs,
            onDismiss = { showPauseDialog = false },
            onConfirmPause = { mins ->
                showPauseDialog = false
                scope.launch { prefsManager.startPauseSession(mins) }
            }
        )
    }

    if (showCuriousDialog) {
        CuriousConfigDialog(
            initialSessionMins = settings.curiousSessionMinutes,
            initialCooldownMins = settings.curiousCooldownMinutes,
            initialLockSwitch = settings.curiousLockSwitchInCooldown,
            initialAllowOne = settings.curiousAllowOneInCooldown,
            onDismiss = { showCuriousDialog = false },
            onSaveAndActivate = { sMins, cMins, lockSw, allowOne ->
                showCuriousDialog = false
                scope.launch {
                    prefsManager.setInt(PreferencesManager.CURIOUS_SESSION_MINUTES, sMins)
                    prefsManager.setInt(PreferencesManager.CURIOUS_COOLDOWN_MINUTES, cMins)
                    prefsManager.setBoolean(PreferencesManager.CURIOUS_LOCK_SWITCH_IN_COOLDOWN, lockSw)
                    prefsManager.setBoolean(PreferencesManager.CURIOUS_ALLOW_ONE_IN_COOLDOWN, allowOne)
                    prefsManager.switchPlan(BlockerPlan.CURIOUS)
                }
            }
        )
    }

    pendingPinAction?.let { act ->
        PinVerifyDialog(
            title = "NoScroll PIN Lock",
            pinType = settings.pinType,
            customPin = settings.customPin,
            onDismiss = { pendingPinAction = null },
            onVerified = {
                pendingPinAction = null
                act()
            }
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        val upd = availableUpdate!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available: ${upd.versionName}", fontWeight = FontWeight.Bold) },
            text = { Text("A new version of NoScroll Free is available.\n\n${upd.releaseNotes.take(280)}", fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = {
                    showUpdateDialog = false
                    UpdateChecker.openUpdateLink(context, upd.apkDownloadUrl)
                }) { Text("Update Now") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
            }
        )
    }

    if (showSupportSheet) {
        ModalBottomSheet(onDismissRequest = { showSupportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("How to use NoScroll", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                GuideStepCard("1. Enable Accessibility Service", "Turn ON 'NoScroll' in Accessibility -> Downloaded apps so Reels & Shorts exit automatically in real time.")
                GuideStepCard("2. Choose Your Blocker Plan", "Block All for strict zero-scroll, Curious Mode for timed breaks + cooldown, or One Reel Mode for friend-sent videos.")
                GuideStepCard("3. App Lock, Sites & PIN", "Use the Blocker tab to lock distracting apps with daily limits, block unlimited sites/keywords, and guard with PIN Lock.")
                GuideStepCard("4. 100% Free & Offline Privacy", "All features unlocked permanently. Zero ads, zero subscriptions, zero data collection.")
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GuideStepCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =============================================================================
// 1. DASHBOARD TAB (Exact NoScroll Shield + Plans + Scroll Counter + Active Restrictions)
// =============================================================================
@Composable
private fun NoScrollDashboardTab(
    settings: AppSettings,
    nowMs: Long,
    a11yEnabled: Boolean,
    batteryExempt: Boolean,
    todayBlocksCount: Int,
    availableUpdate: AppUpdateInfo?,
    prefsManager: PreferencesManager,
    onSelectPlan: (BlockerPlan) -> Unit,
    onOpenSupport: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenBlockerWebsites: () -> Unit,
    onLaunchWebPlatform: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isPaused = settings.blockerPlan == BlockerPlan.PAUSED &&
        (settings.pauseUntilMs == 0L || settings.pauseUntilMs > nowMs)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "NoScroll",
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NoScroll",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "All Premium Features Unlocked · Free",
                            fontSize = 11.sp,
                            color = NoScrollGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSupport) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = "How to use")
                    }
                }
            }
        }

        // Update available banner
        if (availableUpdate != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NoScrollRedSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NoScrollRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenUpdate)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = NoScrollRed)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "New update ${availableUpdate.versionName} available · Tap to install",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = NoScrollRed)
                    }
                }
            }
        }

        // Hero NoScroll Shield Status Card
        item {
            val heroGradient = when {
                !BuildConfig.SUPPORTS_NATIVE_BLOCKER -> listOf(Color(0xFF1A2334), Color(0xFF151C28))
                !a11yEnabled -> listOf(Color(0xFF3A161D), Color(0xFF23131A))
                isPaused -> listOf(Color(0xFF342712), Color(0xFF1F1911))
                else -> listOf(Color(0xFF112C22), Color(0xFF121C22))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(heroGradient))
                    .border(
                        1.dp,
                        when {
                            BuildConfig.SUPPORTS_NATIVE_BLOCKER && !a11yEnabled -> NoScrollRed.copy(alpha = 0.45f)
                            isPaused -> NoScrollAmber.copy(alpha = 0.45f)
                            else -> NoScrollGreen.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    BuildConfig.SUPPORTS_NATIVE_BLOCKER && !a11yEnabled -> Icons.Rounded.WarningAmber
                                    isPaused -> Icons.Rounded.PauseCircle
                                    else -> Icons.Rounded.VerifiedUser
                                },
                                contentDescription = null,
                                tint = when {
                                    BuildConfig.SUPPORTS_NATIVE_BLOCKER && !a11yEnabled -> NoScrollRed
                                    isPaused -> NoScrollAmber
                                    else -> NoScrollGreen
                                },
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = when {
                                        !BuildConfig.SUPPORTS_NATIVE_BLOCKER -> "Website Clean Mode Active"
                                        !a11yEnabled -> "Accessibility Required"
                                        isPaused -> "NoScroll Is Paused"
                                        else -> "NoScroll Is Active!"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = when {
                                        !BuildConfig.SUPPORTS_NATIVE_BLOCKER -> "Clean browser shield + Site/PIN tools ready"
                                        !a11yEnabled -> "Enable Accessibility Service to block Reels & Shorts"
                                        isPaused -> {
                                            val secsLeft = ((settings.pauseUntilMs - nowMs) / 1000L).coerceAtLeast(0L)
                                            "Auto-resumes in ${secsLeft / 60}m ${secsLeft % 60}s"
                                        }
                                        else -> "Stay focused · $todayBlocksCount reels blocked today"
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFFD1D5DB)
                                )
                            }
                        }
                    }

                    if (BuildConfig.SUPPORTS_NATIVE_BLOCKER && !a11yEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { NativeAppHelper.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = NoScrollRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Accessibility Service", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (BuildConfig.SUPPORTS_NATIVE_BLOCKER && !batteryExempt) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { NativeAppHelper.requestIgnoreBatteryOptimizations(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disable Battery Optimization (Recommended 24/7)", fontSize = 12.sp)
                        }
                    } else if (isPaused) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { scope.launch { prefsManager.resumeFromPause() } },
                            colors = ButtonDefaults.buttonColors(containerColor = NoScrollGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resume Blocker Now", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Blocker Plan Cards (Exact NoScroll 4-Plan System)
        item {
            Text(
                text = "BLOCKING PLAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PlanTile(
                        title = "Block All",
                        subtitle = "Instant strict block",
                        icon = Icons.Rounded.Block,
                        selected = settings.blockerPlan == BlockerPlan.BLOCK_ALL,
                        accentColor = NoScrollRed,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPlan(BlockerPlan.BLOCK_ALL) }
                    )
                    PlanTile(
                        title = "Curious Mode",
                        subtitle = "${settings.curiousSessionMinutes}m watch / ${settings.curiousCooldownMinutes}m lock",
                        icon = Icons.Rounded.Timelapse,
                        selected = settings.blockerPlan == BlockerPlan.CURIOUS,
                        accentColor = NoScrollBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPlan(BlockerPlan.CURIOUS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PlanTile(
                        title = "One Reel",
                        subtitle = "Watch 1 reel, block scroll",
                        icon = Icons.Rounded.LooksOne,
                        selected = settings.blockerPlan == BlockerPlan.ONE_REEL,
                        accentColor = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPlan(BlockerPlan.ONE_REEL) }
                    )
                    PlanTile(
                        title = "Pause",
                        subtitle = "Mindful wait timer",
                        icon = Icons.Rounded.Pause,
                        selected = settings.blockerPlan == BlockerPlan.PAUSED,
                        accentColor = NoScrollAmber,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPlan(BlockerPlan.PAUSED) }
                    )
                }
            }
        }

        // Scroll Watchtime Counter Overlay + Block Websites Quick Row
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = NoScrollBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scroll Watchtime Overlay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Floating timer chip & haptics while scrolling shorts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.reelCounterEnabled,
                            onCheckedChange = { scope.launch { prefsManager.setBoolean(PreferencesManager.REEL_COUNTER_ENABLED, it) } }
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBlockerWebsites)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PublicOff, contentDescription = null, tint = NoScrollRed)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block Websites & Apps", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${settings.blockedApps.count { it.enabled }} apps · ${settings.blockedSites.count { it.enabled }} sites restricted",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ACTIVE RESTRICTIONS Header
        item {
            Text(
                text = "ACTIVE RESTRICTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Per-Platform Restrictions List (Exact NoScroll Platform Roster)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    RestrictionToggleRow("YouTube Shorts", "YT, ReVanced, Vanced & Shorts tab", settings.nativeBlockYtShorts) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_YT_SHORTS, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Instagram Reels", "Feed reels, Reels tab & Clips viewer", settings.nativeBlockIgReels) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_IG_REELS, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Allow Reels By Friends", "Watch 1 DM sent reel; exits immediately if scrolled", settings.nativeAllowSentReels) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_ALLOW_SENT_REELS, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Instagram Explore / Search", "Blocks endless Explore grid", settings.nativeBlockIgExplore) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_IG_EXPLORE, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Instagram Stories", "Blocks Story viewer", settings.nativeBlockIgStories) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_IG_STORIES, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Facebook Reels & Watch", "FB & FB Lite short video viewer", settings.nativeBlockFbReels) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_FB_REELS, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Snapchat Spotlights", "Spotlight feed & bottom tab", settings.nativeBlockSnapSpotlight) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_SNAP_SPOTLIGHT, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Snapchat Discover & Stories", "Exits Discover cards & Stories", settings.nativeBlockSnapDiscover || settings.nativeBlockSnapStories) {
                        scope.launch {
                            prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_SNAP_DISCOVER, it)
                            prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_SNAP_STORIES, it)
                        }
                    }
                    RowDivider()
                    RestrictionToggleRow("TikTok & Douyin", "Exits TikTok short-video feed", settings.nativeBlockTiktok) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_TIKTOK, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("X (Twitter) Reels", "Blocks X immersive video tab", settings.nativeBlockTwitterVideo) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_TWITTER_VIDEO, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("Reddit Watch", "Blocks Reddit full-screen short video", settings.nativeBlockRedditVideo) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_REDDIT_VIDEO, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("LinkedIn Watch", "Blocks LinkedIn video carousel", settings.nativeBlockLinkedinVideo) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_LINKEDIN_VIDEO, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("WhatsApp Status", "Optional Status blocker (WA & WA Business)", settings.nativeBlockWhatsAppStatus) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_WHATSAPP_STATUS, it) }
                    }
                    RowDivider()
                    RestrictionToggleRow("External Browser Shorts", "Chrome, Brave, Edge, Firefox, Opera, Samsung", settings.nativeBlockBrowserShorts) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_BROWSER_SHORTS, it) }
                    }
                }
            }
        }

        // Distraction-Free Web Mode Quick Launchers (works even without Accessibility!)
        item {
            Text(
                text = "CLEAN DISTRACTION-FREE WEB MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Platform.entries) { plat ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLaunchWebPlatform(plat.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = plat.iconRes(settings.useClassicIcons)),
                                contentDescription = plat.displayName,
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(plat.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                if (selected) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RestrictionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowDivider() {
    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

// =============================================================================
// 2. BLOCKER TAB (Apps | Websites | PIN Lock)
// =============================================================================
private enum class BlockerSubTab(val label: String) {
    APPS("Apps"),
    WEBSITES("Websites"),
    PIN_LOCK("PIN Lock")
}

@Composable
private fun NoScrollBlockerTab(
    settings: AppSettings,
    prefsManager: PreferencesManager,
    uninstallGuardActive: Boolean,
    onRefreshAdmin: () -> Unit
) {
    var subTab by remember { mutableStateOf(BlockerSubTab.APPS) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Sub-Tab Strip
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Blocker", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BlockerSubTab.entries.forEach { tab ->
                    val sel = subTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) NoScrollRed else Color.Transparent)
                            .clickable { subTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab.label,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (subTab) {
            BlockerSubTab.APPS -> AppsBlockerEmbeddedContent(settings, prefsManager)
            BlockerSubTab.WEBSITES -> WebsitesBlockerEmbeddedContent(settings, prefsManager)
            BlockerSubTab.PIN_LOCK -> PinLockEmbeddedContent(settings, prefsManager, uninstallGuardActive, onRefreshAdmin)
        }
    }
}

@Composable
private fun AppsBlockerEmbeddedContent(
    settings: AppSettings,
    prefsManager: PreferencesManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedApps = remember { NativeAppHelper.getInstalledLaunchableApps(context) }
    var search by remember { mutableStateOf("") }
    var editingApp by remember { mutableStateOf<InstalledAppSummary?>(null) }

    val blockedMap = remember(settings.blockedApps) { settings.blockedApps.associateBy { it.packageName } }
    val filtered = remember(installedApps, search, blockedMap) {
        val base = if (search.isBlank()) installedApps else installedApps.filter {
            it.appName.contains(search, true) || it.packageName.contains(search, true)
        }
        base.sortedWith(compareByDescending<InstalledAppSummary> { blockedMap[it.packageName]?.enabled == true }
            .thenBy { it.appName.lowercase() })
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search installed apps...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${settings.blockedApps.count { it.enabled }} Apps Restricted · Unlimited Free",
            fontSize = 12.sp,
            color = NoScrollGreen,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val rule = blockedMap[app.packageName]
                val enabled = rule?.enabled == true
                val limit = rule?.dailyLimitMinutes ?: 0
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().clickable { editingApp = app }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = when {
                                    !enabled -> "Tap to configure daily limit or lock"
                                    limit <= 0 -> "Strict Lock (Unlock prompt on open)"
                                    else -> "Daily Limit: ${limit}m · Used ${rule!!.usedTodaySeconds / 60}m"
                                },
                                fontSize = 11.sp,
                                color = if (enabled) NoScrollRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                scope.launch {
                                    val mutable = settings.blockedApps.toMutableList()
                                    mutable.removeAll { it.packageName == app.packageName }
                                    if (on) {
                                        mutable.add(BlockedAppRule(app.packageName, app.appName, rule?.dailyLimitMinutes ?: 0, enabled = true))
                                    }
                                    prefsManager.setBlockedApps(mutable)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        val existingRule = blockedMap[app.packageName]
        var limitMins by remember(app.packageName) { mutableStateOf(existingRule?.dailyLimitMinutes ?: 0) }
        val mood = MindfulContent.dailyLimitMood(limitMins)
        AlertDialog(
            onDismissRequest = { editingApp = null },
            title = { Text("Restrict ${app.appName}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(mood.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(mood.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(mood.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Daily Time Allowance:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Lock", 15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (m, label) ->
                            val sel = limitMins == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) NoScrollRed else MaterialTheme.colorScheme.surface)
                                    .clickable { limitMins = m }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val mutable = settings.blockedApps.toMutableList()
                        mutable.removeAll { it.packageName == app.packageName }
                        mutable.add(
                            BlockedAppRule(
                                packageName = app.packageName,
                                appName = app.appName,
                                dailyLimitMinutes = limitMins,
                                usedTodaySeconds = existingRule?.usedTodaySeconds ?: 0,
                                usedDate = existingRule?.usedDate ?: "",
                                enabled = true
                            )
                        )
                        prefsManager.setBlockedApps(mutable)
                    }
                    editingApp = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingApp = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun WebsitesBlockerEmbeddedContent(
    settings: AppSettings,
    prefsManager: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    var pattern by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf(WebMatchType.DOMAIN) }
    var blockAction by remember { mutableStateOf(BlockActionMode.PRESS_BACK) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block Shorts in Supported Browsers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Chrome, Brave, Firefox, Edge, Opera, Samsung & Clean WebView", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.nativeBlockBrowserShorts,
                        onCheckedChange = { scope.launch { prefsManager.setBoolean(PreferencesManager.NATIVE_BLOCK_BROWSER_SHORTS, it) } }
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Add Unlimited Site or Keyword", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        placeholder = { Text("Enter website URL or keyword") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WebMatchType.entries.forEach { mt ->
                            val sel = matchType == mt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) NoScrollRed else MaterialTheme.colorScheme.surface)
                                    .clickable { matchType = mt }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(mt.label, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BlockActionMode.entries.forEach { act ->
                            val sel = blockAction == act
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) NoScrollBlue else MaterialTheme.colorScheme.surface)
                                    .clickable { blockAction = act }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(act.label, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val clean = pattern.trim()
                            if (clean.isNotEmpty()) {
                                scope.launch {
                                    val updated = listOf(BlockedSiteRule(clean, matchType, true, blockAction)) +
                                        settings.blockedSites.filterNot { it.pattern.equals(clean, true) }
                                    prefsManager.setBlockedSites(updated)
                                }
                                pattern = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Block Website / Keyword", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(settings.blockedSites, key = { "${it.matchType.id}:${it.pattern}" }) { rule ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.pattern, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${rule.matchType.label} · ${rule.action.label}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { en ->
                            scope.launch {
                                prefsManager.setBlockedSites(settings.blockedSites.map {
                                    if (it.pattern == rule.pattern && it.matchType == rule.matchType) it.copy(enabled = en) else it
                                })
                            }
                        }
                    )
                    IconButton(onClick = {
                        scope.launch {
                            prefsManager.setBlockedSites(settings.blockedSites.filterNot {
                                it.pattern == rule.pattern && it.matchType == rule.matchType
                            })
                        }
                    }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun PinLockEmbeddedContent(
    settings: AppSettings,
    prefsManager: PreferencesManager,
    uninstallGuardActive: Boolean,
    onRefreshAdmin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customPinDraft by remember(settings.customPin) { mutableStateOf(settings.customPin) }
    var note by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PIN Type", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    PinType.entries.forEach { pt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { prefsManager.setString(PreferencesManager.PIN_TYPE, pt.id) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.pinType == pt,
                                onClick = { scope.launch { prefsManager.setString(PreferencesManager.PIN_TYPE, pt.id) } }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(pt.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(pt.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (settings.pinType == PinType.CUSTOM) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPinDraft,
                            onValueChange = {
                                customPinDraft = it.filter { ch -> ch.isDigit() }.take(8)
                                note = null
                            },
                            label = { Text("Enter PIN (4–8 digits)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (customPinDraft.length >= 4) {
                                    scope.launch { prefsManager.setString(PreferencesManager.CUSTOM_PIN, customPinDraft) }
                                    note = "PIN updated successfully!"
                                } else {
                                    note = "PIN must be at least 4 digits"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save PIN") }
                        note?.let { Text(it, fontSize = 12.sp, color = NoScrollGreen) }
                    }
                }
            }
        }

        if (settings.pinType != PinType.NONE) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Protected Areas", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        RestrictionToggleRow("Protect NoScroll Settings", "Requires PIN before changing settings", settings.pinProtectSettings) {
                            scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_SETTINGS, it) }
                        }
                        RestrictionToggleRow("Protect Blocker Plan Switching", "Requires PIN to switch away from Block All", settings.pinProtectPlanSwitch) {
                            scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_PLAN_SWITCH, it) }
                        }
                        if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
                            RestrictionToggleRow("Protect App Unlocks", "Requires PIN before temporarily opening locked apps", settings.pinProtectAppLocks) {
                                scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_APP_LOCKS, it) }
                            }
                            RestrictionToggleRow("Protect Phone Settings App", "Anti-tamper guard for Android System Settings", settings.pinProtectSystemSettings) {
                                scope.launch { prefsManager.setBoolean(PreferencesManager.PIN_PROTECT_SYSTEM_SETTINGS, it) }
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    RestrictionToggleRow(
                        title = "Accidental Uninstall Protection",
                        subtitle = "Require deactivation inside NoScroll before uninstalling",
                        checked = uninstallGuardActive
                    ) { enable ->
                        if (enable) NativeAppHelper.enableUninstallGuard(context)
                        else NativeAppHelper.disableUninstallGuard(context)
                        onRefreshAdmin()
                    }
                }
            }
        }
    }
}

// =============================================================================
// 3. ANALYTICS TAB
// =============================================================================
@Composable
private fun NoScrollAnalyticsTab(
    settings: AppSettings,
    usageMap: Map<String, Int>,
    todayBlocksCount: Int,
    totalBlocksCount: Int,
    todayReelsScrolled: Int,
    blockHistoryJson: String,
    prefsManager: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    val totalWebSeconds = usageMap.values.sum()
    val savedMinutesToday = (todayBlocksCount * 1.5).toInt()
    val events = remember(blockHistoryJson) { SiteListSerializer.decodeHistory(blockHistoryJson) }
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Text("Analytics", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Today's focus & scrolling summary", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Reels Blocked Today", "$todayBlocksCount", "${savedMinutesToday}m reclaimed", NoScrollRed, Modifier.weight(1f))
                StatCard("All-Time Blocks", "$totalBlocksCount", "$todayReelsScrolled allowed today", NoScrollGreen, Modifier.weight(1f))
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CLEAN WEB BROWSER USAGE TODAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(formatDurationSecs(totalWebSeconds), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Platform.entries.forEach { p ->
                        val s = usageMap[p.id] ?: 0
                        if (s > 0) {
                            val ratio = if (totalWebSeconds > 0) (s.toFloat() / totalWebSeconds).coerceIn(0f, 1f) else 0f
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(p.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(formatDurationSecs(s), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = ratio,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = NoScrollRed,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("REEL WATCH & BLOCK LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (events.isNotEmpty()) {
                    Text(
                        "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NoScrollRed,
                        modifier = Modifier.clickable { scope.launch { prefsManager.clearBlockHistory() } }
                    )
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "No reel block activity recorded yet today.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(events) { ev ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.platformName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${ev.actionMode} · ${ev.planUsed}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt.format(Date(ev.timestampMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDurationSecs(sec: Int): String {
    val mins = sec / 60
    val rem = sec % 60
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m ${rem}s"
}

// =============================================================================
// 4. SETTINGS TAB (Theme, Blocking Mode, Scroll Counter & Haptics, Support, Updates)
// =============================================================================
@Composable
private fun NoScrollSettingsTab(
    settings: AppSettings,
    a11yEnabled: Boolean,
    batteryExempt: Boolean,
    uninstallGuardActive: Boolean,
    prefsManager: PreferencesManager,
    onOpenSupport: () -> Unit,
    onCheckUpdateNow: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }

        // Theme Switcher (Exact NoScroll AppThemeEnum)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("App Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM_DEFAULT" to "System").forEach { (id, label) ->
                            val sel = settings.appTheme.equals(id, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) NoScrollRed else MaterialTheme.colorScheme.surface)
                                    .clickable { scope.launch { prefsManager.setString(PreferencesManager.APP_THEME, id) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { NativeAppHelper.openAccessibilitySettings(context) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accessibility Service", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    if (a11yEnabled) "Running — monitoring short-form videos" else "Tap to enable NoScroll Accessibility",
                                    fontSize = 11.sp,
                                    color = if (a11yEnabled) NoScrollGreen else NoScrollRed
                                )
                            }
                            Text(if (a11yEnabled) "ACTIVE" else "ENABLE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (a11yEnabled) NoScrollGreen else NoScrollRed)
                        }
                        RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { NativeAppHelper.requestIgnoreBatteryOptimizations(context) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep Active When Closed (24/7)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    if (batteryExempt) "Battery Unrestricted" else "Tap to prevent background kill",
                                    fontSize = 11.sp,
                                    color = if (batteryExempt) NoScrollGreen else NoScrollAmber
                                )
                            }
                            Text(if (batteryExempt) "OK" else "FIX", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (batteryExempt) NoScrollGreen else NoScrollAmber)
                        }
                        RowDivider()
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Default Blocking Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(settings.blockActionMode.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                BlockActionMode.entries.forEach { mode ->
                                    val sel = settings.blockActionMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (sel) NoScrollRed else MaterialTheme.colorScheme.surface)
                                            .clickable { scope.launch { prefsManager.setString(PreferencesManager.BLOCK_ACTION_MODE, mode.id) } }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scroll Watchtime, Mindful Wait & Clean Mode Settings
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    RestrictionToggleRow("Detailed Watchtime & Haptics", "Show reel count + subtle vibrate on scroll/block", settings.reelCounterHaptics) {
                        scope.launch {
                            prefsManager.setBoolean(PreferencesManager.REEL_COUNTER_HAPTICS, it)
                            prefsManager.setBoolean(PreferencesManager.REEL_COUNTER_DETAILED, it)
                        }
                    }
                    RowDivider()
                    RestrictionToggleRow("Vibrate When Reel / App Blocked", "Haptic confirmation when blocker triggers", settings.blockHaptics) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.BLOCK_HAPTICS, it) }
                    }
                    RowDivider()
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Mindful Wait Before Pausing", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(0 to "Off", 5 to "5s", 10 to "10s", 20 to "20s").forEach { (sec, label) ->
                                val sel = settings.pauseWaitSeconds == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (sel) NoScrollBlue else MaterialTheme.colorScheme.surface)
                                        .clickable { scope.launch { prefsManager.setInt(PreferencesManager.PAUSE_WAIT_SECONDS, sec) } }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    RowDivider()
                    RestrictionToggleRow("Grayscale Web Mode", "Remove color in Clean Web Browser to reduce dopamine pull", settings.grayscaleMode) {
                        scope.launch { prefsManager.setBoolean(PreferencesManager.GRAYSCALE_MODE, it) }
                    }
                }
            }
        }

        // Support, Privacy & Updates
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSupport).padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = NoScrollBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quick Setup Guide & Privacy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("100% local offline engine · No ads · No subscriptions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onCheckUpdateNow).padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = NoScrollGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check for App Updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.EDITION_NAME}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
