package com.richwatson.electrofind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.richwatson.electrofind.ui.screens.LoginScreen
import com.richwatson.electrofind.ui.screens.ResultsScreen
import com.richwatson.electrofind.ui.screens.SearchScreen
import com.richwatson.electrofind.ui.theme.ElectroFindTheme
import com.richwatson.electrofind.viewmodel.AuthViewModel
import com.richwatson.electrofind.viewmodel.ChargerViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by lazy {
        val tokenManager = (application as ElectroFindApp).tokenManager
        ViewModelProvider(this, factory { AuthViewModel(tokenManager) })[AuthViewModel::class.java]
    }

    private val chargerViewModel: ChargerViewModel by lazy {
        val repository = (application as ElectroFindApp).repository
        ViewModelProvider(this, factory { ChargerViewModel(repository) })[ChargerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElectroFindTheme {
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
                            }
                        )
                    }
                    composable("results") {
                        ResultsScreen(
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
