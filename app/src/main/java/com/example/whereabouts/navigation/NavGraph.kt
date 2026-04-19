package com.example.whereabouts.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.whereabouts.ui.screens.BlockingWallScreen
import com.example.whereabouts.ui.screens.CircleScreen
import com.example.whereabouts.ui.screens.HomeScreen
import com.example.whereabouts.ui.screens.PermissionRationaleScreen
import com.example.whereabouts.ui.screens.SettingsScreen
import com.example.whereabouts.ui.screens.SignInScreen
import com.google.firebase.auth.FirebaseAuth

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Map", Icons.Filled.LocationOn),
    BottomNavItem(Screen.Circle, "Circle", Icons.Filled.Add),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings),
)

private val bottomNavRoutes = bottomNavItems.map { it.screen.route }.toSet()

@Composable
fun WhereAboutsNavGraph() {
    // Returning users skip Sign In and go straight to permission check
    val startDestination = remember {
        if (FirebaseAuth.getInstance().currentUser != null) {
            Screen.PermissionRationale.route
        } else {
            Screen.SignIn.route
        }
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Screen.SignIn.route) {
                SignInScreen(
                    onSignedIn = {
                        navController.navigate(Screen.PermissionRationale.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.PermissionRationale.route) {
                PermissionRationaleScreen(
                    onPermissionGranted = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.PermissionRationale.route) { inclusive = true }
                        }
                    },
                    onPermissionPermanentlyDenied = {
                        navController.navigate(Screen.BlockingWall.route) {
                            popUpTo(Screen.PermissionRationale.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.BlockingWall.route) {
                BlockingWallScreen(
                    onPermissionGranted = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.BlockingWall.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(innerPadding = innerPadding)
            }
            composable(Screen.Circle.route) {
                CircleScreen(innerPadding = innerPadding)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    innerPadding = innerPadding,
                    onSignOut    = {
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
