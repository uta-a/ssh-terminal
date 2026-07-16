package com.uta.terminal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uta.terminal.core.model.SessionState
import com.uta.terminal.core.session.SessionInfo
import com.uta.terminal.core.session.SessionManager
import com.uta.terminal.core.ssh.SshConnectionRequest
import com.uta.terminal.data.ProfileRepository
import com.uta.terminal.data.SettingsStore
import com.uta.terminal.session.SessionController
import com.uta.terminal.ui.BiometricGate
import com.uta.terminal.ui.screens.AddressBookScreen
import com.uta.terminal.ui.screens.ConnectScreen
import com.uta.terminal.ui.screens.SettingsScreen
import com.uta.terminal.ui.screens.TerminalScreen
import com.uta.terminal.ui.theme.TerminalTheme
import kotlinx.coroutines.launch

// 生体認証（BiometricPrompt）を使うため FragmentActivity を継承する。
class MainActivity : FragmentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 常駐通知（フォアグラウンドサービス）表示のため、Android 13+ では通知権限を要求する。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as TerminalApp).container
        setContent {
            TerminalTheme {
                val biometricEnabled by container.settingsStore.biometricEnabled
                    .collectAsState(initial = true)
                // 設定で有効かつ端末対応時、起動/復帰でロック（デバッグビルドは無効）。
                BiometricGate(enabled = biometricEnabled) {
                    AppRoot(
                        container.sessionManager,
                        container.sessionController,
                        container.profileRepository,
                        container.settingsStore,
                    )
                }
            }
        }
    }
}

private object Routes {
    const val TERMINAL = "terminal"
    const val ADDRESS_BOOK = "address_book"
    const val CONNECT = "connect"
    const val SETTINGS = "settings"
}

@Composable
private fun AppRoot(
    sessionManager: SessionManager,
    sessionController: SessionController,
    profileRepository: ProfileRepository,
    settingsStore: SettingsStore,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessions by sessionManager.sessions.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                onSelectSession = { id ->
                    // 表示するセッションを切り替えて端末画面へ。
                    sessionController.setActive(id)
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.TERMINAL) { launchSingleTop = true }
                },
                onNewSession = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.ADDRESS_BOOK) {
                        popUpTo(Routes.ADDRESS_BOOK) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.SETTINGS)
                },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.ADDRESS_BOOK) {
            composable(Routes.TERMINAL) {
                TerminalScreen(
                    host = sessionController.host,
                    currentSessionLabel = sessionController.label,
                    state = sessionController.state,
                    busy = sessionController.busy,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onDisconnect = { sessionController.disconnect() },
                    onRename = { name -> sessionController.rename(name) },
                    // セッションが無くなったら（切断直後など）起動ページ（ホスト一覧）へ戻す。
                    onExit = { navController.popBackStack(Routes.ADDRESS_BOOK, inclusive = false) },
                )
            }
            composable(Routes.ADDRESS_BOOK) {
                val profiles by profileRepository.profiles.collectAsState(initial = emptyList())
                AddressBookScreen(
                    profiles = profiles,
                    onAddNew = { navController.navigate(Routes.CONNECT) },
                    onConnect = { profile ->
                        scope.launch {
                            val auth = try {
                                profileRepository.resolveAuth(profile.id)
                            } catch (e: Throwable) {
                                null
                            }
                            if (auth == null) {
                                Toast.makeText(
                                    context,
                                    "接続情報の復号に失敗しました",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            val req = SshConnectionRequest(
                                profile.host, profile.port, profile.username, auth, cols = 80, rows = 24,
                            )
                            sessionController.connect(req, profile.label)
                            navController.navigate(Routes.TERMINAL) { launchSingleTop = true }
                        }
                    },
                    onDelete = { id -> scope.launch { profileRepository.delete(id) } },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    // アクティブなセッションがあれば端末へ戻る導線を出す。
                    onReturnToTerminal = if (sessionController.host != null) {
                        { navController.navigate(Routes.TERMINAL) { launchSingleTop = true } }
                    } else {
                        null
                    },
                )
            }
            composable(Routes.CONNECT) {
                ConnectScreen(
                    onBack = { navController.popBackStack() },
                    onConnect = { req, label, save ->
                        if (save) scope.launch {
                            profileRepository.save(label, req.host, req.port, req.username, req.auth)
                        }
                        sessionController.connect(req, label)
                        // CONNECT を積み残さず、戻るでホスト一覧へ戻す。
                        navController.navigate(Routes.TERMINAL) {
                            popUpTo(Routes.ADDRESS_BOOK) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                val biometricEnabled by settingsStore.biometricEnabled.collectAsState(initial = true)
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    biometricEnabled = biometricEnabled,
                    onBiometricChange = { enabled ->
                        scope.launch { settingsStore.setBiometricEnabled(enabled) }
                    },
                )
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
                        // 生存インジケータ：接続中は緑、確立中は tertiary、失敗は error、切断は灰。
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(sessionDotColor(s.state), CircleShape),
                            )
                        },
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

/** ドロワーのセッション生存インジケータの色。 */
@Composable
private fun sessionDotColor(state: SessionState): Color = when (state) {
    is SessionState.Connected -> Color(0xFF4CAF50)
    is SessionState.Connecting, is SessionState.Reconnecting -> MaterialTheme.colorScheme.tertiary
    is SessionState.Failed -> MaterialTheme.colorScheme.error
    is SessionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
}
