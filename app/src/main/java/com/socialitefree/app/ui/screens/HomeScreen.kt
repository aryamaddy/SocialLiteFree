package com.socialitefree.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.R
import com.socialitefree.app.data.AppSettings
import com.socialitefree.app.data.NativeAppHelper
import com.socialitefree.app.data.Platform
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.ui.theme.*
import com.socialitefree.app.update.AppUpdateInfo
import com.socialitefree.app.update.UpdateChecker
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefsManager: PreferencesManager,
    onNavigateToPlatform: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = rememberAppSettings(prefsManager)
    val usageMap by prefsManager.getAllUsageSeconds().collectAsState(initial = emptyMap())
    val nativeBlockedCount by prefsManager.nativeBlockedCount.collectAsState(initial = 0)
    val coroutineScope = rememberCoroutineScope()

    var a11yEnabled by remember { mutableStateOf(NativeAppHelper.isAccessibilityEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(NativeAppHelper.canDrawOverlays(context)) }
    var batteryExempt by remember { mutableStateOf(NativeAppHelper.isIgnoringBatteryOptimizations(context)) }

    // Refresh Android permission statuses automatically whenever user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = NativeAppHelper.isAccessibilityEnabled(context)
                overlayEnabled = NativeAppHelper.canDrawOverlays(context)
                batteryExempt = NativeAppHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Collect visibility & native-launch preference for each platform
    val visiblePlatforms = Platform.entries.filter { platform ->
        val visible by prefsManager.isPlatformVisible(platform).collectAsState(initial = platform.defaultVisible)
        visible
    }

    val preferNativeMap = Platform.entries.associate { platform ->
        val pref by prefsManager.preferNativeApp(platform).collectAsState(initial = platform.defaultPreferNative)
        platform.id to pref
    }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    var showGuideSheet by remember { mutableStateOf(false) }
    var pendingNativePlatform by remember { mutableStateOf<Platform?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availableUpdate = UpdateChecker.checkForUpdate()
    }

    fun handlePlatformClick(platform: Platform) {
        val wantsNative = BuildConfig.SUPPORTS_NATIVE_BLOCKER && (preferNativeMap[platform.id] == true)
        val installedPkg = if (wantsNative) NativeAppHelper.findInstalledPackage(context, platform) else null
        if (wantsNative && installedPkg != null) {
            if (!NativeAppHelper.isAccessibilityEnabled(context)) {
                a11yEnabled = false
                overlayEnabled = NativeAppHelper.canDrawOverlays(context)
                pendingNativePlatform = platform
            } else {
                NativeAppHelper.launchNativeApp(context, platform)
            }
        } else {
            onNavigateToPlatform(platform.id)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> ScreenTimePage(
                    settings = settings,
                    usageMap = usageMap,
                    nativeBlockedCount = nativeBlockedCount,
                    prefsManager = prefsManager,
                    onGoHome = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> LauncherHomePage(
                    settings = settings,
                    visiblePlatforms = visiblePlatforms,
                    preferNativeMap = preferNativeMap,
                    a11yEnabled = a11yEnabled,
                    availableUpdate = availableUpdate,
                    onPlatformClick = ::handlePlatformClick,
                    onOpenUpdateDialog = { showUpdateDialog = true },
                    onShieldClick = {
                        a11yEnabled = NativeAppHelper.isAccessibilityEnabled(context)
                        overlayEnabled = NativeAppHelper.canDrawOverlays(context)
                        pendingNativePlatform = Platform.INSTAGRAM
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onOpenGuide = { showGuideSheet = true },
                    onJumpToSleep = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    onJumpToScreenTime = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                )
                2 -> SleepModePage(
                    settings = settings,
                    prefsManager = prefsManager,
                    onGoHome = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
            }
        }

        // Pager dots indicator anchored above bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isSelected = pagerState.currentPage == index
                val isDarkPage = pagerState.currentPage == 2
                val dotColor by animateColorAsState(
                    targetValue = when {
                        isSelected && isDarkPage -> SleepMoonAccent
                        isSelected -> InkPrimary.copy(alpha = 0.75f)
                        isDarkPage -> Color.White.copy(alpha = 0.28f)
                        else -> InkPrimary.copy(alpha = 0.22f)
                    },
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .clickable {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                )
            }
        }
    }

    if (showGuideSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGuideSheet = false },
            containerColor = CreamSurface
        ) {
            QuickGuideSheetContent(onDismiss = { showGuideSheet = false })
        }
    }

    pendingNativePlatform?.let { targetPlatform ->
        ModalBottomSheet(
            onDismissRequest = { pendingNativePlatform = null },
            containerColor = CreamSurface
        ) {
            NativeBlockerSetupSheet(
                platform = targetPlatform,
                a11yEnabled = a11yEnabled,
                overlayEnabled = overlayEnabled,
                batteryExempt = batteryExempt,
                onEnableAccessibility = { NativeAppHelper.openAccessibilitySettings(context) },
                onEnableOverlay = { NativeAppHelper.openOverlaySettings(context) },
                onRequestBatteryExempt = { NativeAppHelper.requestIgnoreBatteryOptimizations(context) },
                onLaunchNativeAnyway = {
                    pendingNativePlatform = null
                    val launched = NativeAppHelper.launchNativeApp(context, targetPlatform)
                    if (!launched) onNavigateToPlatform(targetPlatform.id)
                },
                onOpenCleanWebInstead = {
                    pendingNativePlatform = null
                    onNavigateToPlatform(targetPlatform.id)
                }
            )
        }
    }

    if (showUpdateDialog && availableUpdate != null) {
        val upd = availableUpdate!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available: ${upd.versionName}", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "A new version of SocialLite is ready with bug fixes and new features!\n\n${upd.releaseNotes.take(260)}",
                    fontSize = 13.sp,
                    color = InkSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        UpdateChecker.openUpdateLink(context, upd.apkDownloadUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SocialPurple)
                ) {
                    Text("Update Now", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
private fun LauncherHomePage(
    settings: AppSettings,
    visiblePlatforms: List<Platform>,
    preferNativeMap: Map<String, Boolean>,
    a11yEnabled: Boolean,
    availableUpdate: AppUpdateInfo?,
    onPlatformClick: (Platform) -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onShieldClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onJumpToSleep: () -> Unit,
    onJumpToScreenTime: () -> Unit
) {
    val context = LocalContext.current
    val now = remember { LocalTime.now() }
    val inSleepWindow = settings.isInSleepWindow(now.hour, now.minute)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Header: SocialLite / Focus forward + Native Shield Pill + Settings Gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SocialLite",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = InkPrimary
                    )
                    Text(
                        text = if (inSleepWindow) "Sleep Mode active" else "Focus forward",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (inSleepWindow) SocialPurple else InkSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (a11yEnabled) SocialGreen.copy(alpha = 0.14f) else SocialPurpleSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onShieldClick)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (a11yEnabled) SocialGreen else SocialPurple)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (a11yEnabled) "Shield ON" else "Enable Shield",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (a11yEnabled) SocialGreen else SocialPurple
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = InkSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (availableUpdate != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialPurpleSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenUpdateDialog)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = SocialPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New update ${availableUpdate.versionName} available · Tap to update",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SocialPurple,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = SocialPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2-Column 3D Glossy Painted Squircle Icon Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visiblePlatforms, key = { it.id }) { platform ->
                    val usesNative = BuildConfig.SUPPORTS_NATIVE_BLOCKER &&
                        preferNativeMap[platform.id] == true &&
                        NativeAppHelper.findInstalledPackage(context, platform) != null
                    PlatformSquircleTile(
                        platform = platform,
                        useClassicIcons = settings.useClassicIcons,
                        isNativeMode = usesNative,
                        onClick = { onPlatformClick(platform) }
                    )
                }
            }

            // Minimal Bottom Action Bar matching tut_home.webp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(InkPrimary.copy(alpha = 0.14f))
                        .clickable(onClick = onOpenGuide),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary.copy(alpha = 0.72f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onJumpToScreenTime)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = InkSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Focus forward · All Pro Free",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }

                IconButton(
                    onClick = onJumpToSleep,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Mode",
                        tint = InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformSquircleTile(
    platform: Platform,
    useClassicIcons: Boolean,
    isNativeMode: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier.size(94.dp)
        ) {
            Image(
                painter = painterResource(id = platform.iconRes(useClassicIcons)),
                contentDescription = platform.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
                    .align(Alignment.Center)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.20f)
                    )
                    .clip(RoundedCornerShape(22.dp))
            )

            if (isNativeMode) {
                Surface(
                    color = SocialGreen,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 2.dp,
                    modifier = Modifier.offset(x = (-6).dp, y = 4.dp)
                ) {
                    Text(
                        text = "APP",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else if (platform.isBeta) {
                Surface(
                    color = SocialCoral,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 2.dp,
                    modifier = Modifier.offset(x = (-6).dp, y = 4.dp)
                ) {
                    Text(
                        text = "BETA",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = platform.displayName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NativeBlockerSetupSheet(
    platform: Platform,
    a11yEnabled: Boolean,
    overlayEnabled: Boolean,
    batteryExempt: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onRequestBatteryExempt: () -> Unit,
    onLaunchNativeAnyway: () -> Unit,
    onOpenCleanWebInstead: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = SocialPurple,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Original App Reels Blocker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkPrimary
                    )
                    Text(
                        text = "Blocks Reels & Shorts directly inside your real ${platform.displayName} app",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                }
            }
        }

        // Step 1: Accessibility Service
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CreamCard,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Turn ON Content Blocker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = InkPrimary
                        )
                        Text(
                            text = if (a11yEnabled) "ACTIVE ✓" else "REQUIRED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (a11yEnabled) SocialGreen else SocialCoral
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "In Accessibility -> Downloaded apps, turn ON \"SocialLite Content Blocker\" so SocialLite can exit Reels automatically when opened or scrolled.",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ob_native_a11y_hint),
                        contentDescription = "Accessibility hint",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onEnableAccessibility,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (a11yEnabled) SocialGreen else SocialPurple
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (a11yEnabled) "Accessibility Enabled ✓" else "Open Accessibility Settings",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Step 2: Optional Overlay Permission
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CreamCard,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Draw Over Other Apps",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = InkPrimary
                        )
                        Text(
                            text = if (overlayEnabled) "ALLOWED ✓" else "RECOMMENDED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overlayEnabled) SocialGreen else SocialAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Covers & locks the Reels tab button inside the original Instagram app.",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onEnableOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayEnabled) SocialGreen else InkPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (overlayEnabled) "Overlay Permission Allowed ✓" else "Allow Display Over Other Apps",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Step 3: Keep Running 24/7 (Battery Optimization Exemption)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CreamCard,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Keep Active When Closed (24/7)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = InkPrimary
                        )
                        Text(
                            text = if (batteryExempt) "UNRESTRICTED ✓" else "IMPORTANT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (batteryExempt) SocialGreen else SocialPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Prevents Android/MIUI from stopping the blocker when you close SocialLite.",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestBatteryExempt,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (batteryExempt) SocialGreen else SocialPurple
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (batteryExempt) "24/7 Background Allowed ✓" else "Allow Unrestricted Background",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
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
                    onClick = onOpenCleanWebInstead,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Web Instead", fontSize = 12.sp, color = InkPrimary)
                }
                Button(
                    onClick = onLaunchNativeAnyway,
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open ${platform.displayName}", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ScreenTimePage(
    settings: AppSettings,
    usageMap: Map<String, Int>,
    nativeBlockedCount: Int,
    prefsManager: PreferencesManager,
    onGoHome: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val totalSeconds = usageMap.values.sum()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.screen_time_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Screen Time",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkPrimary
                        )
                        Text(
                            text = "Today's intentional usage",
                            fontSize = 13.sp,
                            color = InkSecondary
                        )
                    }
                    IconButton(onClick = onGoHome) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = "Back to Home",
                            tint = InkSecondary
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .shadow(4.dp, RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.status_card),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "TOTAL IN SOCIALLITE TODAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatDuration(totalSeconds),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = SocialGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$nativeBlockedCount Reels / Shorts caught in native apps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SocialGreen
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CreamCard.copy(alpha = 0.92f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Grayscale Mode",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = InkPrimary
                            )
                            Text(
                                text = "Remove color to curb dopamine loops",
                                fontSize = 12.sp,
                                color = InkSecondary
                            )
                        }
                        Switch(
                            checked = settings.grayscaleMode,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    prefsManager.setBoolean(PreferencesManager.GRAYSCALE_MODE, it)
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "BY PLATFORM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            items(Platform.entries) { platform ->
                val secs = usageMap[platform.id] ?: 0
                val ratio = if (totalSeconds > 0) (secs.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CreamCard.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = platform.iconRes(settings.useClassicIcons)),
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = platform.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = InkPrimary
                                )
                                Text(
                                    text = formatDuration(secs),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = InkSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = ratio,
                                color = SocialPurple,
                                trackColor = CreamCardSubtle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepModePage(
    settings: AppSettings,
    prefsManager: PreferencesManager,
    onGoHome: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val now = remember { LocalTime.now() }
    val activeNow = settings.isInSleepWindow(now.hour, now.minute)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sleep_mode_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onGoHome) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Sleep Mode",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Downtime schedule",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.NightlightRound,
                    contentDescription = null,
                    tint = SleepMoonAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SleepNightCard.copy(alpha = 0.88f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Downtime",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (activeNow) "Currently resting — feeds are paused"
                                else "Blocks social feeds automatically during bedtime",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = settings.sleepModeEnabled,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    prefsManager.setBoolean(PreferencesManager.SLEEP_MODE_ENABLED, it)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    HourSelectorRow(
                        label = "Bedtime Starts",
                        hour = settings.sleepStartHour,
                        minute = settings.sleepStartMinute,
                        onDeltaHour = { delta ->
                            val next = (settings.sleepStartHour + delta + 24) % 24
                            coroutineScope.launch {
                                prefsManager.setInt(PreferencesManager.SLEEP_START_HOUR, next)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HourSelectorRow(
                        label = "Wake Up Time",
                        hour = settings.sleepEndHour,
                        minute = settings.sleepEndMinute,
                        onDeltaHour = { delta ->
                            val next = (settings.sleepEndHour + delta + 24) % 24
                            coroutineScope.launch {
                                prefsManager.setInt(PreferencesManager.SLEEP_END_HOUR, next)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HourSelectorRow(
    label: String,
    hour: Int,
    minute: Int,
    onDeltaHour: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onDeltaHour(-1) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(
                text = String.format("%02d:%02d", hour, minute),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SleepMoonAccent,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            IconButton(
                onClick = { onDeltaHour(1) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun QuickGuideSheetContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Welcome to SocialLite",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = InkPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Blocks Reels & Shorts both in your original installed apps AND inside the ad-free browser.",
            fontSize = 14.sp,
            color = InkSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        GuideItem(
            icon = Icons.Rounded.VerifiedUser,
            title = "Original Instagram App Reels Blocker",
            desc = "Enable SocialLite Content Blocker in Accessibility Settings to lock the Reels tab and stop Reels scrolling inside your original Instagram app."
        )
        Spacer(modifier = Modifier.height(12.dp))
        GuideItem(
            icon = Icons.Rounded.Menu,
            title = "Floating Browser Pill Menu",
            desc = "For web platforms like ad-free YouTube, tap the translucent ≡ button in the bottom-right to navigate or change filters live."
        )
        Spacer(modifier = Modifier.height(12.dp))
        GuideItem(
            icon = Icons.Rounded.TouchApp,
            title = "Swipe Left or Right on Home",
            desc = "Swipe left for Screen Time & Blocked count, or swipe right for Sleep Mode downtime scheduling."
        )

        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Got it", fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GuideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SocialPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SocialPurple, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = InkPrimary)
            Text(desc, fontSize = 12.sp, color = InkSecondary)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0m"
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${secs}s"
    }
}
