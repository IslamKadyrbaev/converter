package com.example.converter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.converter.ui.theme.ConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConverterTheme {
                ConverterApp()
            }
        }
    }
}

private sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val badge: String
) {
    data object Converter : Screen("converter", R.string.nav_converter, "↔")
    data object LiveRate : Screen("live_rate", R.string.nav_live_rate, "$")
    data object Admin : Screen("admin", R.string.nav_admin, "⚙")
}

@Composable
private fun ConverterApp() {
    val context = LocalContext.current
    val repo = remember { ConverterRepository(context.applicationContext) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val screens = listOf(Screen.Converter, Screen.LiveRate, Screen.Admin)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(screen.badge) },
                        label = { Text(stringResource(screen.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(repo = repo, innerPadding = innerPadding, navController = navController)
    }
}

@Composable
private fun AppNavHost(
    repo: ConverterRepository,
    innerPadding: PaddingValues,
    navController: androidx.navigation.NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Converter.route,
        modifier = Modifier.padding(innerPadding)
    ) {
        composable(Screen.Converter.route) { ConverterScreen(repo = repo) }
        composable(Screen.LiveRate.route) { LiveRateScreen(repo = repo) }
        composable(Screen.Admin.route) { AdminScreen(repo = repo) }
    }
}
