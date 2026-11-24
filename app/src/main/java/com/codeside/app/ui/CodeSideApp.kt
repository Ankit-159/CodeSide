package com.codeside.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
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
import com.codeside.app.ui.files.FilesScreen
import com.codeside.app.ui.settings.SettingsScreen
import com.codeside.app.ui.terminal.TerminalScreen

private enum class Dest(val route: String, val label: String) {
    Chat("chat", "Chat"),
    Terminal("terminal", "Terminal"),
    Files("files", "Files"),
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
                listOf(Dest.Chat, Dest.Terminal, Dest.Files, Dest.Settings).forEach { dest ->
                    val icon = when (dest) {
                        Dest.Chat -> Icons.AutoMirrored.Filled.Chat
                        Dest.Terminal -> Icons.Default.Terminal
                        Dest.Files -> Icons.Default.Folder
                        Dest.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
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
            composable(Dest.Files.route) { FilesScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
