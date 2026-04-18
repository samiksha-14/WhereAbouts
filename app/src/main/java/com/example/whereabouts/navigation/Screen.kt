package com.example.whereabouts.navigation

sealed class Screen(val route: String) {
    data object SignIn : Screen("sign_in")
    data object PermissionRationale : Screen("permission_rationale")
    data object BlockingWall : Screen("blocking_wall")
    data object Home : Screen("home")
    data object Circle : Screen("circle")
    data object Settings : Screen("settings")
}
