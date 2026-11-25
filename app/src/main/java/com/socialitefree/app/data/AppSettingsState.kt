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
    val nativeBlockYtShorts by prefsManager.nativeBlockYtShorts.collectAsState(initial = true)
    val nativeBlockFbReels by prefsManager.nativeBlockFbReels.collectAsState(initial = true)
    val nativeBlockSnapSpotlight by prefsManager.nativeBlockSnapSpotlight.collectAsState(initial = true)

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

    return remember(
        useClassicIcons, autoAppLaunch, favoritePlatformId,
        nativeBlockIgReels, nativeAllowSentReels, nativeLockIgReelsTab, nativeBlockIgExplore, nativeBlockYtShorts, nativeBlockFbReels, nativeBlockSnapSpotlight,
        ytBlockAds, ytHideShorts, ytHideComments, ytHideRecs, ytBgPlay, ytSearchOnly, ytBlockFeedAutoplay,
        igHideAds, igHideSuggested, igBlockReels, igHideComments, igBlockPosting, igReelsBudget,
        ttBlockExplore, ttBlockLive, ttBlockShop, ttBlockUpload, ttBlockSearch, ttBlockComments,
        twHideForYou, twHideAds,
        fbBlockReels, fbHidePeople, fbHideMarketplace,
        redditBlockAds, redditHideSuggested, redditBlockPopular,
        grayscale, sleepEnabled, sleepStartHour, sleepStartMinute, sleepEndHour, sleepEndMinute
    ) {
        AppSettings(
            useClassicIcons = useClassicIcons,
            autoAppLaunch = autoAppLaunch,
            favoritePlatformId = favoritePlatformId,
            nativeBlockIgReels = nativeBlockIgReels,
            nativeAllowSentReels = nativeAllowSentReels,
            nativeLockIgReelsTab = nativeLockIgReelsTab,
            nativeBlockIgExplore = nativeBlockIgExplore,
            nativeBlockYtShorts = nativeBlockYtShorts,
            nativeBlockFbReels = nativeBlockFbReels,
            nativeBlockSnapSpotlight = nativeBlockSnapSpotlight,
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
}
