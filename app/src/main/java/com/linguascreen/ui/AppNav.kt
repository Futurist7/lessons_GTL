package com.linguascreen.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.linguascreen.ui.history.HistoryScreen
import com.linguascreen.ui.home.HomeScreen
import com.linguascreen.ui.settings.SettingsScreen

@Composable
fun AppNav(startRoute: String = "home") {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startRoute) {
        composable("home") {
            HomeScreen(
                onOpenHistory = { nav.navigate("history") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
