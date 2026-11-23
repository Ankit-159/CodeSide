package com.codeside.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codeside.app.ui.chat.ChatScreen
import com.codeside.app.ui.settings.SettingsScreen
import com.codeside.app.ui.terminal.TerminalScreen

private enum class Dest(val route: String, val label: String) {
    Chat("chat", "Chat"),
    Terminal("terminal", "Terminal"),
    Settings("settings", "Settings"),
}

@Composable
fun CodeSideApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Dest.Chat.route,
                    onClick = {
                        navController.navigate(Dest.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = Dest.Chat.label) },
                    label = { Text(Dest.Chat.label) },
                )
                NavigationBarItem(
                    selected = currentRoute == Dest.Terminal.route,
                    onClick = {
                        navController.navigate(Dest.Terminal.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = Dest.Terminal.label) },
                    label = { Text(Dest.Terminal.label) },
                )
                NavigationBarItem(
                    selected = currentRoute == Dest.Settings.route,
                    onClick = {
                        navController.navigate(Dest.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = Dest.Settings.label) },
                    label = { Text(Dest.Settings.label) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Dest.Chat.route) { ChatScreen() }
            composable(Dest.Terminal.route) { TerminalScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
