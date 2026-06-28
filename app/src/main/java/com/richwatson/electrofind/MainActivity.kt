package com.richwatson.electrofind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.richwatson.electrofind.ui.screens.AboutScreen
import com.richwatson.electrofind.ui.screens.BackupRestoreScreen
import com.richwatson.electrofind.ui.screens.AddEditCustomChargerScreen
import com.richwatson.electrofind.ui.screens.ChargeCurveScreen
import com.richwatson.electrofind.ui.screens.BrowseMapScreen
import com.richwatson.electrofind.ui.screens.CustomChargersScreen
import com.richwatson.electrofind.ui.screens.LoginScreen
import com.richwatson.electrofind.ui.screens.ResultsMapScreen
import com.richwatson.electrofind.ui.screens.ResultsScreen
import com.richwatson.electrofind.ui.screens.RoutePlannerScreen
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
            ChargerViewModel(app.repository, app.appPreferences, app, app.carProfileRepository)
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
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val hasResults = state.chargers.isNotEmpty()
                val secondaryScreens = setOf("login")
                val showBottomNav = isLoggedIn && currentRoute !in secondaryScreens

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == "search",
                                    onClick = {
                                        navController.navigate("search") {
                                            popUpTo("search") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Search, null) },
                                    label = { Text("Search", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "results",
                                    enabled = hasResults,
                                    onClick = {
                                        navController.navigate("results") {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    label = { Text("List", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute in listOf("results_map", "browse_map"),
                                    onClick = {
                                        val dest = if (hasResults) "results_map" else "browse_map"
                                        navController.navigate(dest) {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Map, null) },
                                    label = { Text("Map", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "route",
                                    onClick = {
                                        navController.navigate("route") {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Route, null) },
                                    label = { Text("Route", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute in listOf("settings", "curve"),
                                    onClick = {
                                        navController.navigate("settings") {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    label = { Text("Settings", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "about",
                                    onClick = {
                                        navController.navigate("about") {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Info, null) },
                                    label = { Text("About", fontSize = 10.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentRoute in listOf("custom_chargers", "custom_charger_form"),
                                    onClick = {
                                        navController.navigate("custom_chargers") {
                                            popUpTo("search")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.AddCircle, null) },
                                    label = { Text("Mine", fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (isLoggedIn) "search" else "login",
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
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
                                    navController.navigate("results") { launchSingleTop = true }
                                },
                                onBrowseMap = {
                                    navController.navigate("browse_map") { launchSingleTop = true }
                                },
                                onSettings = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("browse_map") {
                            BrowseMapScreen(
                                initialLat = if (state.searchLat != 0.0) state.searchLat else 51.5,
                                initialLng = if (state.searchLng != 0.0) state.searchLng else -0.1,
                                chargerViewModel = chargerViewModel,
                                onLocationSelected = { lat, lng, label ->
                                    chargerViewModel.searchByCoordinates(lat, lng, label = label, reverseGeocodePrefix = if (label == null) "Map pin" else null)
                                    navController.navigate("results_map") { launchSingleTop = true }
                                },
                                onAddCustomCharger = { lat, lng ->
                                    navController.navigate("custom_charger_form?lat=$lat&lng=$lng") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("results") {
                            ResultsScreen(
                                chargerViewModel = chargerViewModel,
                                onShowOnMap = { charger ->
                                    chargerViewModel.selectCharger(charger.pk)
                                    navController.navigate("results_map") { launchSingleTop = true }
                                },
                                onEditCustomCharger = { id ->
                                    navController.navigate("custom_charger_form?id=$id") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("results_map") {
                            ResultsMapScreen(
                                chargerViewModel = chargerViewModel,
                                onClear = {
                                    navController.navigate("browse_map") {
                                        popUpTo("search")
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("route") {
                            RoutePlannerScreen(
                                chargerViewModel = chargerViewModel,
                                onShowOnMap = { pk ->
                                    chargerViewModel.selectCharger(pk)
                                    navController.navigate("results_map") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                chargerViewModel = chargerViewModel,
                                appPreferences = (application as ElectroFindApp).appPreferences,
                                onBack = { navController.popBackStack() },
                                onShowCurve = {
                                    navController.navigate("curve") { launchSingleTop = true }
                                },
                                onShowBackup = {
                                    navController.navigate("backup_restore") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("backup_restore") {
                            BackupRestoreScreen(
                                chargerViewModel = chargerViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("curve") {
                            ChargeCurveScreen(chargerViewModel = chargerViewModel)
                        }
                        composable("about") {
                            AboutScreen()
                        }
                        composable("custom_chargers") {
                            CustomChargersScreen(
                                chargerViewModel = chargerViewModel,
                                onAddNew = {
                                    navController.navigate("custom_charger_form") { launchSingleTop = true }
                                },
                                onEdit = { id ->
                                    navController.navigate("custom_charger_form?id=$id") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("custom_charger_form?id={id}&lat={lat}&lng={lng}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                            val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull()
                            val lng = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull()
                            AddEditCustomChargerScreen(
                                chargerViewModel = chargerViewModel,
                                existingId = id,
                                initialLat = lat,
                                initialLng = lng,
                                onSave = { navController.popBackStack() },
                                onCancel = { navController.popBackStack() }
                            )
                        }
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
