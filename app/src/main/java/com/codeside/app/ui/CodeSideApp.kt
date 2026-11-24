package com.codeside.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codeside.app.ui.chat.ChatScreen
import com.codeside.app.ui.files.FilesScreen
import com.codeside.app.ui.settings.SettingsScreen
import com.codeside.app.ui.terminal.TerminalScreen
import kotlinx.coroutines.launch

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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(500); loading = false }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("CodeSide", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                    listOf(Dest.Chat, Dest.Terminal, Dest.Files, Dest.Settings).forEach { dest ->
                        val icon = when (dest) {
                            Dest.Chat -> Icons.AutoMirrored.Filled.Chat
                            Dest.Terminal -> Icons.Default.Terminal
                            Dest.Files -> Icons.Default.Folder
                            Dest.Settings -> Icons.Default.Settings
                        }
                        NavigationDrawerItem(
                            label = { Text(dest.label) },
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(icon, contentDescription = dest.label) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Dest.Chat.route,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable(Dest.Chat.route) { ChatScreen(onOpenDrawer = openDrawer) }
                    composable(Dest.Terminal.route) { TerminalScreen(onOpenDrawer = openDrawer) }
                    composable(Dest.Files.route) { FilesScreen(onOpenDrawer = openDrawer) }
                    composable(Dest.Settings.route) { SettingsScreen(onOpenDrawer = openDrawer) }
                }
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
