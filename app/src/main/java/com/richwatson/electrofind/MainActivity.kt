package com.richwatson.electrofind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.richwatson.electrofind.ui.screens.AboutScreen
import com.richwatson.electrofind.ui.screens.BrowseMapScreen
import com.richwatson.electrofind.ui.screens.ComparisonScreen
import com.richwatson.electrofind.ui.screens.LoginScreen
import com.richwatson.electrofind.ui.screens.ResultsMapScreen
import com.richwatson.electrofind.ui.screens.ResultsScreen
import com.richwatson.electrofind.ui.screens.SearchScreen
import com.richwatson.electrofind.ui.screens.SettingsScreen
import com.richwatson.electrofind.ui.theme.ElectroFindTheme
import com.richwatson.electrofind.viewmodel.AuthViewModel
import com.richwatson.electrofind.viewmodel.ChargerViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by lazy {
        val tokenManager = (application as ElectroFindApp).tokenManager
        ViewModelProvider(this, factory { AuthViewModel(tokenManager) })[AuthViewModel::class.java]
    }

    private val chargerViewModel: ChargerViewModel by lazy {
        val app = application as ElectroFindApp
        ViewModelProvider(this, factory {
            ChargerViewModel(app.repository, app.ocmRepository, app.appPreferences)
        })[ChargerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val state by chargerViewModel.state.collectAsState()
            ElectroFindTheme(themeMode = state.themeMode) {
                val navController = rememberNavController()
                val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

                NavHost(navController, startDestination = if (isLoggedIn) "search" else "login") {
                    composable("login") {
                        LoginScreen(authViewModel)
                        LaunchedEffect(isLoggedIn) {
                            if (isLoggedIn) {
                                navController.navigate("search") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        }
                    }
                    composable("search") {
                        SearchScreen(
                            chargerViewModel = chargerViewModel,
                            onResultsReady = {
                                navController.navigate("results") {
                                    launchSingleTop = true
                                }
                            },
                            onBrowseMap = {
                                navController.navigate("browse_map")
                            },
                            onSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("browse_map") {
                        val state by chargerViewModel.state.collectAsState()
                        BrowseMapScreen(
                            initialLat = if (state.searchLat != 0.0) state.searchLat else 46.0,
                            initialLng = if (state.searchLng != 0.0) state.searchLng else 2.0,
                            chargerViewModel = chargerViewModel,
                            onLocationSelected = { lat, lng ->
                                chargerViewModel.searchByCoordinates(lat, lng)
                                navController.navigate("results") { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("results") {
                        ResultsScreen(
                            chargerViewModel = chargerViewModel,
                            onBack = { navController.popBackStack() },
                            onCompare = { navController.navigate("comparison") },
                            onViewMap = { navController.navigate("results_map") { launchSingleTop = true } }
                        )
                    }
                    composable("results_map") {
                        ResultsMapScreen(
                            chargerViewModel = chargerViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            chargerViewModel = chargerViewModel,
                            appPreferences = (application as ElectroFindApp).appPreferences,
                            onBack = { navController.popBackStack() },
                            onAbout = { navController.navigate("about") }
                        )
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable("comparison") {
                        ComparisonScreen(
                            chargerViewModel = chargerViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private fun <VM : ViewModel> factory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
