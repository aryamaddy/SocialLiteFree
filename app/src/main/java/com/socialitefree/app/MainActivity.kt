package com.socialitefree.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.socialitefree.app.data.PinType
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.ui.components.PinVerifyDialog
import com.socialitefree.app.ui.screens.AppBlockerScreen
import com.socialitefree.app.ui.screens.BrowserScreen
import com.socialitefree.app.ui.screens.HomeScreen
import com.socialitefree.app.ui.screens.PinLockScreen
import com.socialitefree.app.ui.screens.SettingsScreen
import com.socialitefree.app.ui.screens.WebsiteBlockerScreen
import com.socialitefree.app.ui.theme.SocialLiteFreeTheme
import com.socialitefree.app.update.UpdateCheckWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Granted or dismissed cleanly */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager.getInstance(applicationContext)

        requestNotificationPermissionIfNeeded()
        scheduleBackgroundUpdateChecks()

        val autoLaunch = runBlocking { preferencesManager.autoAppLaunch.first() }
        val favPlatform = runBlocking { preferencesManager.defaultPlatform.first() }
        val initialRoute = if (autoLaunch && favPlatform.isNotBlank()) "browser/$favPlatform" else "home"

        setContent {
            SocialLiteFreeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val settings = rememberAppSettings(preferencesManager)
                    var pendingPinRoute by remember { mutableStateOf<String?>(null) }

                    fun navigateProtected(route: String, requirePin: Boolean) {
                        if (requirePin && settings.pinType != PinType.NONE) {
                            pendingPinRoute = route
                        } else {
                            navController.navigate(route)
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = initialRoute
                    ) {
                        composable("home") {
                            HomeScreen(
                                prefsManager = preferencesManager,
                                onNavigateToPlatform = { platformId ->
                                    navController.navigate("browser/$platformId")
                                },
                                onNavigateToSettings = {
                                    navigateProtected("settings", settings.pinProtectSettings)
                                },
                                onNavigateToAppBlocker = {
                                    navigateProtected("app_blocker", settings.pinProtectSettings)
                                },
                                onNavigateToWebsiteBlocker = {
                                    navigateProtected("website_blocker", settings.pinProtectSettings)
                                },
                                onNavigateToPinLock = {
                                    navigateProtected("pin_lock", settings.pinProtectSettings)
                                }
                            )
                        }

                        composable(
                            route = "browser/{platformId}",
                            arguments = listOf(
                                navArgument("platformId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val platformId = backStackEntry.arguments?.getString("platformId") ?: "youtube"
                            BrowserScreen(
                                platformId = platformId,
                                prefsManager = preferencesManager,
                                onNavigateHome = {
                                    navController.navigate("home") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onSwitchPlatform = { newId ->
                                    navController.navigate("browser/$newId") {
                                        popUpTo("home") { inclusive = false }
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                prefsManager = preferencesManager,
                                onNavigateToAppBlocker = { navController.navigate("app_blocker") },
                                onNavigateToWebsiteBlocker = { navController.navigate("website_blocker") },
                                onNavigateToPinLock = { navController.navigate("pin_lock") },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("app_blocker") {
                            AppBlockerScreen(
                                prefsManager = preferencesManager,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("website_blocker") {
                            WebsiteBlockerScreen(
                                prefsManager = preferencesManager,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("pin_lock") {
                            PinLockScreen(
                                prefsManager = preferencesManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    pendingPinRoute?.let { route ->
                        PinVerifyDialog(
                            title = "Unlock Protected Area",
                            pinType = settings.pinType,
                            customPin = settings.customPin,
                            onDismiss = { pendingPinRoute = null },
                            onVerified = {
                                val target = route
                                pendingPinRoute = null
                                navController.navigate(target)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleBackgroundUpdateChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "sociallite_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
