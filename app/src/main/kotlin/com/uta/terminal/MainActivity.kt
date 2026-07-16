package com.uta.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uta.terminal.core.session.SessionInfo
import com.uta.terminal.core.session.SessionManager
import com.uta.terminal.ui.screens.AddressBookScreen
import com.uta.terminal.ui.screens.SettingsScreen
import com.uta.terminal.ui.screens.TerminalScreen
import com.uta.terminal.ui.theme.TerminalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TerminalApp).container
        setContent {
            TerminalTheme {
                AppRoot(container.sessionManager)
            }
        }
    }
}

private object Routes {
    const val TERMINAL = "terminal"
    const val ADDRESS_BOOK = "address_book"
    const val SETTINGS = "settings"
}

@Composable
private fun AppRoot(sessionManager: SessionManager) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by sessionManager.sessions.collectAsState()
    val activeId by sessionManager.activeId.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                onSelectSession = { id ->
                    sessionManager.setActive(id)
                    scope.launch { drawerState.close() }
                },
                onNewSession = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.ADDRESS_BOOK)
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.SETTINGS)
                },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.TERMINAL) {
            composable(Routes.TERMINAL) {
                val activeLabel = sessions.firstOrNull { it.id == activeId }?.label
                TerminalScreen(
                    currentSessionLabel = activeLabel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(Routes.ADDRESS_BOOK) {
                AddressBookScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** ドロワー：上部＝開いているセッション一覧＋新規、下部固定＝設定。 */
@Composable
private fun SessionDrawer(
    sessions: List<SessionInfo>,
    onSelectSession: (com.uta.terminal.core.model.SessionId) -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "セッション",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id.value }) { s ->
                    NavigationDrawerItem(
                        label = { Text(s.label) },
                        selected = false,
                        onClick = { onSelectSession(s.id) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                item {
                    NavigationDrawerItem(
                        label = { Text("新規セッション") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        badge = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        selected = false,
                        onClick = onNewSession,
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text("設定") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
