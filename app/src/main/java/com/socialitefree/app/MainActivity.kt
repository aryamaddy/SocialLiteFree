package com.socialitefree.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.socialitefree.app.data.PreferencesManager
import com.socialitefree.app.ui.screens.BrowserScreen
import com.socialitefree.app.ui.screens.HomeScreen
import com.socialitefree.app.ui.screens.SettingsScreen
import com.socialitefree.app.ui.theme.SocialLiteFreeTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager.getInstance(applicationContext)

        setContent {
            SocialLiteFreeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToPlatform = { platformId ->
                                    navController.navigate("browser/$platformId")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
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
                                    navController.popBackStack("home", inclusive = false)
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                prefsManager = preferencesManager,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
