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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialitefree.app.R
import com.socialitefree.app.data.AppSettings
import com.socialitefree.app.data.Platform
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefsManager: PreferencesManager,
    onNavigateToPlatform: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val settings = rememberAppSettings(prefsManager)
    val usageMap by prefsManager.getAllUsageSeconds().collectAsState(initial = emptyMap())
    val coroutineScope = rememberCoroutineScope()

    // Collect visibility for each platform
    val visiblePlatforms = Platform.entries.filter { platform ->
        val visible by prefsManager.isPlatformVisible(platform).collectAsState(initial = platform.defaultVisible)
        visible
    }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    var showGuideSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> ScreenTimePage(
                    settings = settings,
                    usageMap = usageMap,
                    prefsManager = prefsManager,
                    onGoHome = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> LauncherHomePage(
                    settings = settings,
                    visiblePlatforms = visiblePlatforms,
                    onNavigateToPlatform = onNavigateToPlatform,
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
}

@Composable
private fun LauncherHomePage(
    settings: AppSettings,
    visiblePlatforms: List<Platform>,
    onNavigateToPlatform: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onJumpToSleep: () -> Unit,
    onJumpToScreenTime: () -> Unit
) {
    val now = remember { LocalTime.now() }
    val inSleepWindow = settings.isInSleepWindow(now.hour, now.minute)

    Box(modifier = Modifier.fillMaxSize()) {
        // Warm cream botanical background matching base.apk home_bg.webp
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

            // Top Header: SocialLite / Focus forward + Settings Gear
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

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = InkSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 2-Column 3D Glossy Painted Squircle Icon Grid (matching tut_home.webp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visiblePlatforms, key = { it.id }) { platform ->
                    PlatformSquircleTile(
                        platform = platform,
                        useClassicIcons = settings.useClassicIcons,
                        onClick = { onNavigateToPlatform(platform.id) }
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
                // Help '?' circle button
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

                // Center subtle Screen Time shortcut
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

                // Right Sleep / Bedtime shortcut
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

            if (platform.isBeta) {
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
private fun ScreenTimePage(
    settings: AppSettings,
    usageMap: Map<String, Int>,
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

            // Summary Card backed by status_card.webp
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
                                text = "Ads, Shorts & Algorithmic Traps Blocked",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SocialGreen
                            )
                        }
                    }
                }
            }

            // Quick Grayscale Well-Being Card
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

            // Per-Platform Breakdown
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

            // Main Enable Card
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

                    // Bedtime Start Hour Adjuster
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

                    // Wake Up Hour Adjuster
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
            text = "All Pro features are unlocked and 100% free — no ads, no subscriptions.",
            fontSize = 14.sp,
            color = InkSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))

        GuideItem(
            icon = Icons.Rounded.Menu,
            title = "Floating Browser Pill Menu",
            desc = "Inside any platform, tap the translucent ≡ button in the bottom-right to go Back, Home, Refresh, Switch Apps, or change platform filters live."
        )
        Spacer(modifier = Modifier.height(12.dp))
        GuideItem(
            icon = Icons.Rounded.TouchApp,
            title = "Swipe Left or Right on Home",
            desc = "Swipe left for Screen Time tracking & Grayscale Mode, or swipe right for Sleep Mode downtime scheduling."
        )
        Spacer(modifier = Modifier.height(12.dp))
        GuideItem(
            icon = Icons.Rounded.Palette,
            title = "Painted 3D or Classic Icons",
            desc = "Customize which apps show on your Home grid and switch between Painted 3D or Classic icons in Settings."
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
