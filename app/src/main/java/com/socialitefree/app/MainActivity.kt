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
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.data.rememberAppSettings
import com.socialitefree.app.ui.screens.BrowserScreen
import com.socialitefree.app.ui.screens.NoScrollRootScreen
import com.socialitefree.app.ui.theme.SocialLiteFreeTheme
import com.socialitefree.app.update.UpdateCheckWorker
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

        setContent {
            val settings = rememberAppSettings(preferencesManager)
            SocialLiteFreeTheme(appTheme = settings.appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "noscroll_root"
                    ) {
                        composable("noscroll_root") {
                            NoScrollRootScreen(
                                prefsManager = preferencesManager,
                                onNavigateToWebPlatform = { platformId ->
                                    navController.navigate("browser/$platformId")
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
                                    navController.navigate("noscroll_root") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onSwitchPlatform = { newId ->
                                    navController.navigate("browser/$newId") {
                                        popUpTo("noscroll_root") { inclusive = false }
                                    }
                                }
                            )
                        }
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
