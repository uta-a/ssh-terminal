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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uta.terminal.core.model.SessionState
import com.uta.terminal.core.session.SessionInfo
import com.uta.terminal.core.session.SessionManager
import com.uta.terminal.core.ssh.SshAuth
import com.uta.terminal.core.ssh.SshConnectionRequest
import com.uta.terminal.data.AuthInput
import com.uta.terminal.data.HostProfile
import com.uta.terminal.data.ProfileRepository
import com.uta.terminal.data.SettingsStore
import com.uta.terminal.data.SshKeyRepository
import com.uta.terminal.session.SessionController
import com.uta.terminal.ui.BiometricGate
import com.uta.terminal.ui.screens.AddressBookScreen
import com.uta.terminal.ui.screens.AuthSpec
import com.uta.terminal.ui.screens.ConnectScreen
import com.uta.terminal.ui.screens.KeysScreen
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
                        container.sshKeyRepository,
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
    const val CONNECT = "connect?editId={editId}"
    const val SETTINGS = "settings"
    const val KEYS = "keys"

    /** 接続フォームへの遷移先。[editId] を渡すと編集モードで開く。 */
    fun connect(editId: String? = null): String =
        if (editId != null) "connect?editId=$editId" else "connect"
}

/**
 * フォームの [AuthSpec] を実際の認証情報へ解決する。
 * 新規鍵は鍵ストアへ登録し、以後は参照（keyId）で扱う。
 * @return 接続に使う [SshAuth] と保存に使う [AuthInput]。鍵が見つからなければ null。
 */
private suspend fun resolveAuthSpec(
    spec: AuthSpec,
    keys: SshKeyRepository,
): Pair<SshAuth, AuthInput>? = when (spec) {
    is AuthSpec.Password ->
        SshAuth.Password(spec.password) to AuthInput.Password(spec.password)
    is AuthSpec.ExistingKey -> {
        val auth = keys.resolveAuth(spec.keyId)
        if (auth == null) null else auth to AuthInput.KeyRef(spec.keyId)
    }
    is AuthSpec.NewKey -> {
        val id = keys.add(spec.name, spec.pem, spec.passphrase)
        SshAuth.PrivateKey(spec.pem, spec.passphrase) to AuthInput.KeyRef(id)
    }
}

@Composable
private fun AppRoot(
    sessionManager: SessionManager,
    sessionController: SessionController,
    profileRepository: ProfileRepository,
    sshKeyRepository: SshKeyRepository,
    settingsStore: SettingsStore,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessions by sessionManager.sessions.collectAsState()
    // 長押しで削除確認するセッション（null＝ダイアログ非表示）。
    var sessionToDelete by remember { mutableStateOf<SessionInfo?>(null) }

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
                onLongPressSession = { info -> sessionToDelete = info },
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
                val tags by profileRepository.tags.collectAsState(initial = emptyList())
                AddressBookScreen(
                    profiles = profiles,
                    allTags = tags,
                    onAddNew = { navController.navigate(Routes.connect()) },
                    onEdit = { profile -> navController.navigate(Routes.connect(profile.id)) },
                    onDuplicate = { profile ->
                        scope.launch { profileRepository.duplicate(profile.id) }
                    },
                    onTogglePin = { profile ->
                        scope.launch { profileRepository.setPinned(profile.id, !profile.pinned) }
                    },
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
                    onReorder = { ids -> scope.launch { profileRepository.reorder(ids) } },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    // アクティブなセッションがあれば端末へ戻る導線を出す。
                    onReturnToTerminal = if (sessionController.host != null) {
                        { navController.navigate(Routes.TERMINAL) { launchSingleTop = true } }
                    } else {
                        null
                    },
                )
            }
            composable(
                route = Routes.CONNECT,
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val editId = backStackEntry.arguments?.getString("editId")
                val keys by sshKeyRepository.keys.collectAsState(initial = emptyList())
                // 編集時はプロファイルを読み込んでから画面を出す（新規モードが一瞬映るのを防ぐ）。
                var initial by remember { mutableStateOf<HostProfile?>(null) }
                var ready by remember { mutableStateOf(editId == null) }
                LaunchedEffect(editId) {
                    if (editId != null) {
                        initial = profileRepository.get(editId)
                        ready = true
                    }
                }
                val tagNames by profileRepository.tags.collectAsState(initial = emptyList())
                if (ready) {
                    ConnectScreen(
                        keys = keys,
                        allTags = tagNames.map { it.name },
                        initial = initial,
                        onBack = { navController.popBackStack() },
                        onConnect = { label, host, port, username, spec, save, tags ->
                            scope.launch {
                                val resolved = try {
                                    resolveAuthSpec(spec, sshKeyRepository)
                                } catch (e: Throwable) {
                                    null
                                }
                                if (resolved == null) {
                                    Toast.makeText(context, "認証情報の準備に失敗しました", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val (auth, input) = resolved
                                if (save) profileRepository.save(label, host, port, username, input, tags)
                                val req = SshConnectionRequest(host, port, username, auth, cols = 80, rows = 24)
                                sessionController.connect(req, label)
                                // CONNECT を積み残さず、戻るでホスト一覧へ戻す。
                                navController.navigate(Routes.TERMINAL) {
                                    popUpTo(Routes.ADDRESS_BOOK) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onSaveEdit = { label, host, port, username, spec, tags ->
                            val id = initial?.id
                            scope.launch {
                                if (id != null) {
                                    val input = when (spec) {
                                        null -> null
                                        is AuthSpec.Password -> AuthInput.Password(spec.password)
                                        is AuthSpec.ExistingKey -> AuthInput.KeyRef(spec.keyId)
                                        is AuthSpec.NewKey -> AuthInput.KeyRef(
                                            sshKeyRepository.add(spec.name, spec.pem, spec.passphrase),
                                        )
                                    }
                                    profileRepository.update(id, label, host, port, username, input, tags)
                                }
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
            composable(Routes.KEYS) {
                val keys by sshKeyRepository.keys.collectAsState(initial = emptyList())
                KeysScreen(
                    keys = keys,
                    onBack = { navController.popBackStack() },
                    onAdd = { name, pem, pass ->
                        scope.launch { sshKeyRepository.add(name, pem, pass) }
                    },
                    onRename = { id, name ->
                        scope.launch { sshKeyRepository.rename(id, name) }
                    },
                    onDelete = { id ->
                        scope.launch { sshKeyRepository.delete(id) }
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
                    onOpenKeys = { navController.navigate(Routes.KEYS) },
                )
            }
        }
    }

    // セッション長押し → 切断して一覧から削除する確認ダイアログ。
    val toDelete = sessionToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("セッションを削除") },
            text = { Text("「${toDelete.label}」を切断して一覧から削除します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    sessionController.disconnect(toDelete.id)
                    sessionToDelete = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("キャンセル") }
            },
        )
    }
}

/** ドロワー：上部＝開いているセッション一覧＋新規、下部固定＝設定。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawer(
    sessions: List<SessionInfo>,
    onSelectSession: (com.uta.terminal.core.model.SessionId) -> Unit,
    onLongPressSession: (SessionInfo) -> Unit,
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
                    // タップで切替、長押しで削除確認。NavigationDrawerItem は長押しを扱えないため
                    // combinedClickable で自前の行を組む（見た目は近似）。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .combinedClickable(
                                onClick = { onSelectSession(s.id) },
                                onLongClick = { onLongPressSession(s) },
                            )
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        // 生存インジケータ：接続中は緑、確立中は tertiary、失敗は error、切断は灰。
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(sessionDotColor(s.state), CircleShape),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            s.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
