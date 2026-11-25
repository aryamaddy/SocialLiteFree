package com.socialitefree.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun rememberAppSettings(prefsManager: PreferencesManager): AppSettings {
    val useClassicIcons by prefsManager.useClassicIcons.collectAsState(initial = false)
    val autoAppLaunch by prefsManager.autoAppLaunch.collectAsState(initial = false)
    val favoritePlatformId by prefsManager.defaultPlatform.collectAsState(initial = "instagram")

    val nativeBlockIgReels by prefsManager.nativeBlockIgReels.collectAsState(initial = true)
    val nativeAllowSentReels by prefsManager.nativeAllowSentReels.collectAsState(initial = true)
    val nativeLockIgReelsTab by prefsManager.nativeLockIgReelsTab.collectAsState(initial = true)
    val nativeBlockIgExplore by prefsManager.nativeBlockIgExplore.collectAsState(initial = false)
    val nativeBlockIgStories by prefsManager.nativeBlockIgStories.collectAsState(initial = false)
    val nativeBlockYtShorts by prefsManager.nativeBlockYtShorts.collectAsState(initial = true)
    val nativeBlockFbReels by prefsManager.nativeBlockFbReels.collectAsState(initial = true)
    val nativeBlockSnapSpotlight by prefsManager.nativeBlockSnapSpotlight.collectAsState(initial = true)
    val nativeBlockSnapDiscover by prefsManager.nativeBlockSnapDiscover.collectAsState(initial = false)
    val nativeBlockSnapStories by prefsManager.nativeBlockSnapStories.collectAsState(initial = false)
    val nativeBlockTiktok by prefsManager.nativeBlockTiktok.collectAsState(initial = true)
    val nativeBlockTwitterVideo by prefsManager.nativeBlockTwitterVideo.collectAsState(initial = true)
    val nativeBlockRedditVideo by prefsManager.nativeBlockRedditVideo.collectAsState(initial = true)
    val nativeBlockLinkedinVideo by prefsManager.nativeBlockLinkedinVideo.collectAsState(initial = true)
    val nativeBlockWhatsAppStatus by prefsManager.nativeBlockWhatsAppStatus.collectAsState(initial = false)
    val nativeBlockBrowserShorts by prefsManager.nativeBlockBrowserShorts.collectAsState(initial = true)

    val blockerPlanRaw by prefsManager.blockerPlan.collectAsState(initial = BlockerPlan.BLOCK_ALL.id)
    val blockActionRaw by prefsManager.blockActionMode.collectAsState(initial = BlockActionMode.PRESS_BACK.id)
    val curiousSessionMin by prefsManager.curiousSessionMinutes.collectAsState(initial = 5)
    val curiousCooldownMin by prefsManager.curiousCooldownMinutes.collectAsState(initial = 2)
    val curiousStartMs by prefsManager.curiousSessionStartMs.collectAsState(initial = 0L)
    val curiousCooldownUntil by prefsManager.curiousCooldownUntilMs.collectAsState(initial = 0L)
    val curiousLockSwitch by prefsManager.curiousLockSwitchInCooldown.collectAsState(initial = true)
    val curiousAllowOne by prefsManager.curiousAllowOneInCooldown.collectAsState(initial = false)
    val pauseUntilMs by prefsManager.pauseUntilMs.collectAsState(initial = 0L)
    val pauseDurationMin by prefsManager.pauseDurationMinutes.collectAsState(initial = 10)
    val pauseWaitSec by prefsManager.pauseWaitSeconds.collectAsState(initial = 10)
    val pauseCooldownMin by prefsManager.pauseCooldownMinutes.collectAsState(initial = 5)
    val pauseCooldownUntil by prefsManager.pauseCooldownUntilMs.collectAsState(initial = 0L)

    val reelCounterEnabled by prefsManager.reelCounterEnabled.collectAsState(initial = true)
    val reelCounterDetailed by prefsManager.reelCounterDetailed.collectAsState(initial = true)
    val reelCounterHaptics by prefsManager.reelCounterHaptics.collectAsState(initial = true)
    val blockHaptics by prefsManager.blockHaptics.collectAsState(initial = true)

    val blockedAppsJson by prefsManager.blockedAppsJson.collectAsState(initial = "[]")
    val blockedSitesJson by prefsManager.blockedSitesJson.collectAsState(initial = "[]")
    val pinTypeRaw by prefsManager.pinType.collectAsState(initial = PinType.NONE.id)
    val customPin by prefsManager.customPin.collectAsState(initial = "")
    val pinProtectSettings by prefsManager.pinProtectSettings.collectAsState(initial = true)
    val pinProtectPlanSwitch by prefsManager.pinProtectPlanSwitch.collectAsState(initial = true)
    val pinProtectAppLocks by prefsManager.pinProtectAppLocks.collectAsState(initial = true)
    val pinProtectSystemSettings by prefsManager.pinProtectSystemSettings.collectAsState(initial = false)

    val ytBlockAds by prefsManager.ytBlockAds.collectAsState(initial = true)
    val ytHideShorts by prefsManager.ytHideShorts.collectAsState(initial = true)
    val ytHideComments by prefsManager.ytHideComments.collectAsState(initial = false)
    val ytHideRecs by prefsManager.ytHideRecommendations.collectAsState(initial = false)
    val ytBgPlay by prefsManager.ytBackgroundPlay.collectAsState(initial = true)
    val ytSearchOnly by prefsManager.ytSearchOnly.collectAsState(initial = false)
    val ytBlockFeedAutoplay by prefsManager.ytBlockFeedAutoplay.collectAsState(initial = false)

    val igHideAds by prefsManager.igHideAds.collectAsState(initial = true)
    val igHideSuggested by prefsManager.igHideSuggested.collectAsState(initial = true)
    val igBlockReels by prefsManager.igBlockReels.collectAsState(initial = true)
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

    val fbBlockReels by prefsManager.fbBlockReels.collectAsState(initial = true)
    val fbHidePeople by prefsManager.fbHidePeople.collectAsState(initial = true)
    val fbHideMarketplace by prefsManager.fbHideMarketplace.collectAsState(initial = false)

    val redditBlockAds by prefsManager.redditBlockAds.collectAsState(initial = true)
    val redditHideSuggested by prefsManager.redditHideSuggested.collectAsState(initial = true)
    val redditBlockPopular by prefsManager.redditBlockPopular.collectAsState(initial = false)

    val grayscale by prefsManager.grayscaleMode.collectAsState(initial = false)
    val sleepEnabled by prefsManager.sleepModeEnabled.collectAsState(initial = false)
    val sleepStartHour by prefsManager.sleepStartHour.collectAsState(initial = 22)
    val sleepStartMinute by prefsManager.sleepStartMinute.collectAsState(initial = 0)
    val sleepEndHour by prefsManager.sleepEndHour.collectAsState(initial = 7)
    val sleepEndMinute by prefsManager.sleepEndMinute.collectAsState(initial = 0)

    val blockedApps = remember(blockedAppsJson) { SiteListSerializer.decodeApps(blockedAppsJson) }
    val blockedSites = remember(blockedSitesJson) { SiteListSerializer.decodeSites(blockedSitesJson) }

    return AppSettings(
        useClassicIcons = useClassicIcons,
        autoAppLaunch = autoAppLaunch,
        favoritePlatformId = favoritePlatformId,
        nativeBlockIgReels = nativeBlockIgReels,
        nativeAllowSentReels = nativeAllowSentReels,
        nativeLockIgReelsTab = nativeLockIgReelsTab,
        nativeBlockIgExplore = nativeBlockIgExplore,
        nativeBlockIgStories = nativeBlockIgStories,
        nativeBlockYtShorts = nativeBlockYtShorts,
        nativeBlockFbReels = nativeBlockFbReels,
        nativeBlockSnapSpotlight = nativeBlockSnapSpotlight,
        nativeBlockSnapDiscover = nativeBlockSnapDiscover,
        nativeBlockSnapStories = nativeBlockSnapStories,
        nativeBlockTiktok = nativeBlockTiktok,
        nativeBlockTwitterVideo = nativeBlockTwitterVideo,
        nativeBlockRedditVideo = nativeBlockRedditVideo,
        nativeBlockLinkedinVideo = nativeBlockLinkedinVideo,
        nativeBlockWhatsAppStatus = nativeBlockWhatsAppStatus,
        nativeBlockBrowserShorts = nativeBlockBrowserShorts,
        blockerPlan = BlockerPlan.fromId(blockerPlanRaw),
        blockActionMode = BlockActionMode.fromId(blockActionRaw),
        curiousSessionMinutes = curiousSessionMin,
        curiousCooldownMinutes = curiousCooldownMin,
        curiousSessionStartMs = curiousStartMs,
        curiousCooldownUntilMs = curiousCooldownUntil,
        curiousLockSwitchInCooldown = curiousLockSwitch,
        curiousAllowOneInCooldown = curiousAllowOne,
        pauseUntilMs = pauseUntilMs,
        pauseDurationMinutes = pauseDurationMin,
        pauseWaitSeconds = pauseWaitSec,
        pauseCooldownMinutes = pauseCooldownMin,
        pauseCooldownUntilMs = pauseCooldownUntil,
        reelCounterEnabled = reelCounterEnabled,
        reelCounterDetailed = reelCounterDetailed,
        reelCounterHaptics = reelCounterHaptics,
        blockHaptics = blockHaptics,
        blockedApps = blockedApps,
        blockedSites = blockedSites,
        pinType = PinType.fromId(pinTypeRaw),
        customPin = customPin,
        pinProtectSettings = pinProtectSettings,
        pinProtectPlanSwitch = pinProtectPlanSwitch,
        pinProtectAppLocks = pinProtectAppLocks,
        pinProtectSystemSettings = pinProtectSystemSettings,
        ytBlockAds = ytBlockAds,
        ytHideShorts = ytHideShorts,
        ytHideComments = ytHideComments,
        ytHideRecommendations = ytHideRecs,
        ytBackgroundPlay = ytBgPlay,
        ytSearchOnly = ytSearchOnly,
        ytBlockFeedAutoplay = ytBlockFeedAutoplay,
        igHideAds = igHideAds,
        igHideSuggested = igHideSuggested,
        igBlockReels = igBlockReels,
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
        fbBlockReels = fbBlockReels,
        fbHidePeople = fbHidePeople,
        fbHideMarketplace = fbHideMarketplace,
        redditBlockAds = redditBlockAds,
        redditHideSuggested = redditHideSuggested,
        redditBlockPopular = redditBlockPopular,
        grayscaleMode = grayscale,
        sleepModeEnabled = sleepEnabled,
        sleepStartHour = sleepStartHour,
        sleepStartMinute = sleepStartMinute,
        sleepEndHour = sleepEndHour,
        sleepEndMinute = sleepEndMinute
    )
}
