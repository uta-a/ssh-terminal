package com.uta.tunnel

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uta.tunnel.core.model.SessionState
import com.uta.tunnel.core.session.SessionManager
import com.uta.tunnel.core.ssh.SshAuth
import com.uta.tunnel.core.ssh.SshConnectionRequest
import com.uta.tunnel.data.AuthInput
import com.uta.tunnel.data.HostProfile
import com.uta.tunnel.data.ProfileRepository
import com.uta.tunnel.data.RoomHostKeyStore
import com.uta.tunnel.data.SettingsStore
import com.uta.tunnel.data.SettingsUiState
import com.uta.tunnel.data.SshKeyRepository
import com.uta.tunnel.data.rememberSettingsUiState
import com.uta.tunnel.session.SessionController
import com.uta.tunnel.terminal.TerminalPalettes
import com.uta.tunnel.ui.BiometricGate
import com.uta.tunnel.ui.screens.AddressBookScreen
import com.uta.tunnel.ui.screens.AuthSpec
import com.uta.tunnel.ui.screens.ConnectScreen
import com.uta.tunnel.ui.screens.KeysScreen
import com.uta.tunnel.ui.screens.KnownHostsScreen
import com.uta.tunnel.ui.screens.SessionsScreen
import com.uta.tunnel.ui.screens.SettingsScreen
import com.uta.tunnel.ui.screens.TerminalScreen
import com.uta.tunnel.ui.theme.TunnelTheme
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
        val container = (application as TunnelApp).container
        setContent {
            TunnelTheme {
                // 設定はここで一度だけ購読し、ロック判定と AppRoot の両方に配る。
                val settings = rememberSettingsUiState(container.settingsStore)
                // 設定で有効かつ端末対応時、起動/復帰でロック（デバッグビルドは無効）。
                BiometricGate(enabled = settings.biometricEnabled) {
                    AppRoot(
                        container.sessionManager,
                        container.sessionController,
                        container.profileRepository,
                        container.sshKeyRepository,
                        container.hostKeyStore,
                        container.settingsStore,
                        settings,
                    )
                }
            }
        }
    }
}

private object Routes {
    const val SESSIONS = "sessions"
    const val TERMINAL = "terminal"
    const val ADDRESS_BOOK = "address_book"
    const val CONNECT = "connect?editId={editId}"
    const val SETTINGS = "settings"
    const val KEYS = "keys"
    const val KNOWN_HOSTS = "known_hosts"

    /** 下タブとして出すトップレベルルート（この3つでのみボトムナビを表示）。 */
    val TOP_LEVEL = setOf(SESSIONS, ADDRESS_BOOK, SETTINGS)

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

/** 代表セッション状態を選ぶ優先度（生存を優先）。 */
private fun rank(state: SessionState): Int = when (state) {
    is SessionState.Connected -> 4
    is SessionState.Connecting, is SessionState.Reconnecting -> 3
    is SessionState.Failed -> 1
    is SessionState.Disconnected -> 0
}

@Composable
private fun AppRoot(
    sessionManager: SessionManager,
    sessionController: SessionController,
    profileRepository: ProfileRepository,
    sshKeyRepository: SshKeyRepository,
    hostKeyStore: RoomHostKeyStore,
    settingsStore: SettingsStore,
    settings: SettingsUiState,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessions by sessionManager.sessions.collectAsState()
    val sessionsTabFirst = settings.sessionsTabFirst

    // Dynamic Color は ColorScheme が要るので Composable 側でしか組み立てられない。
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(settings.paletteId, colorScheme) {
        if (settings.paletteId == TerminalPalettes.DYNAMIC_ID) {
            TerminalPalettes.dynamic(colorScheme)
        } else {
            TerminalPalettes.byId(settings.paletteId) ?: TerminalPalettes.Default
        }
    }

    // 起動タブは状況で切替：生存セッションがあれば「セッション」、無ければ「ホスト」。
    val startTab = remember {
        if (sessionManager.sessions.value.isNotEmpty()) Routes.SESSIONS else Routes.ADDRESS_BOOK
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ホストから接続してシェルを開く共通処理（[forceNew]=true で既存があっても新規）。
    fun openHost(profile: HostProfile, forceNew: Boolean) {
        if (!forceNew && sessionController.activateExistingForProfile(profile.id)) {
            navController.navigate(Routes.TERMINAL) { launchSingleTop = true }
            return
        }
        scope.launch {
            val auth = try {
                profileRepository.resolveAuth(profile.id)
            } catch (e: Throwable) {
                null
            }
            if (auth == null) {
                Toast.makeText(context, "接続情報の復号に失敗しました", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val req = SshConnectionRequest(
                profile.host, profile.port, profile.username, auth, cols = 80, rows = 24,
            )
            sessionController.connect(req, profile.label, profileId = profile.id)
            navController.navigate(Routes.TERMINAL) { launchSingleTop = true }
        }
    }

    Scaffold(
        // 端末画面など各スクリーンが自前でインセットを扱うため、外側は 0 にする。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in Routes.TOP_LEVEL) {
                NavigationBar {
                    fun switchTab(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    // セッション/ホストの並びは設定で入れ替える（設定タブは常に末尾）。
                    val sessionsItem: @Composable RowScope.() -> Unit = {
                        NavigationBarItem(
                            selected = currentRoute == Routes.SESSIONS,
                            onClick = { switchTab(Routes.SESSIONS) },
                            icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                            label = { Text("セッション") },
                        )
                    }
                    val hostsItem: @Composable RowScope.() -> Unit = {
                        NavigationBarItem(
                            selected = currentRoute == Routes.ADDRESS_BOOK,
                            onClick = { switchTab(Routes.ADDRESS_BOOK) },
                            icon = { Icon(Icons.Filled.Dns, contentDescription = null) },
                            label = { Text("ホスト") },
                        )
                    }
                    if (sessionsTabFirst) {
                        sessionsItem()
                        hostsItem()
                    } else {
                        hostsItem()
                        sessionsItem()
                    }
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { switchTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("設定") },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = startTab,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.SESSIONS) {
                SessionsScreen(
                    sessions = sessions,
                    onOpen = { id ->
                        sessionController.setActive(id)
                        navController.navigate(Routes.TERMINAL) { launchSingleTop = true }
                    },
                    onRename = { id, name -> sessionController.rename(id, name) },
                    onDisconnect = { id -> sessionController.disconnect(id) },
                )
            }
            composable(Routes.TERMINAL) {
                TerminalScreen(
                    host = sessionController.host,
                    currentSessionLabel = sessionController.label,
                    state = sessionController.state,
                    busy = sessionController.busy,
                    fontSizeSp = settings.fontSizeSp,
                    lineSpacing = settings.lineSpacing,
                    palette = palette,
                    onFontSizeChange = { size ->
                        scope.launch { settingsStore.setTerminalFontSizeSp(size) }
                    },
                    onBack = { navController.popBackStack() },
                    onDisconnect = { sessionController.disconnect() },
                    onRename = { name -> sessionController.rename(name) },
                    // セッションが無くなったら（切断直後など）直前のタブへ戻す。
                    onExit = { navController.popBackStack() },
                )
            }
            composable(Routes.ADDRESS_BOOK) {
                val profiles by profileRepository.profiles.collectAsState(initial = emptyList())
                val tags by profileRepository.tags.collectAsState(initial = emptyList())
                // プロファイルごとの「代表セッション状態」。生存（接続中/確立中）を優先して選ぶ。
                val sessionStateByProfile = remember(sessions) {
                    val map = HashMap<String, SessionState>()
                    for (s in sessions) {
                        val pid = s.profileId ?: continue
                        val cur = map[pid]
                        if (cur == null || rank(s.state) > rank(cur)) map[pid] = s.state
                    }
                    map
                }
                AddressBookScreen(
                    profiles = profiles,
                    allTags = tags,
                    sessionStateByProfile = sessionStateByProfile,
                    onAddNew = { navController.navigate(Routes.connect()) },
                    onEdit = { profile -> navController.navigate(Routes.connect(profile.id)) },
                    onDuplicate = { profile ->
                        scope.launch { profileRepository.duplicate(profile.id) }
                    },
                    onTogglePin = { profile ->
                        scope.launch { profileRepository.setPinned(profile.id, !profile.pinned) }
                    },
                    // 1タップ：同ホストの生存セッションがあれば切替、無ければ新規。
                    onConnect = { profile -> openHost(profile, forceNew = false) },
                    // ⋮「新しいセッション」：常に新規。
                    onNewSession = { profile -> openHost(profile, forceNew = true) },
                    onDelete = { id -> scope.launch { profileRepository.delete(id) } },
                    onReorder = { ids -> scope.launch { profileRepository.reorder(ids) } },
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
                                // 保存する場合は新規プロファイル id をセッションに紐付ける
                                // （後でホスト再タップ時に既存セッションへ切替できるように）。
                                val savedId = if (save) {
                                    profileRepository.save(label, host, port, username, input, tags)
                                } else {
                                    null
                                }
                                val req = SshConnectionRequest(host, port, username, auth, cols = 80, rows = 24)
                                sessionController.connect(req, label, profileId = savedId)
                                // CONNECT を積み残さず、戻るでホスト一覧へ戻す。
                                navController.navigate(Routes.TERMINAL) {
                                    popUpTo(Routes.ADDRESS_BOOK) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onSaveOnly = { label, host, port, username, spec, tags ->
                            scope.launch {
                                // 接続しないので SshAuth は使わず、保存用の AuthInput だけ作る。
                                // 新しい鍵はここで鍵ストアへ登録し、以後は参照（keyId）で扱う。
                                val input = try {
                                    when (spec) {
                                        is AuthSpec.Password -> AuthInput.Password(spec.password)
                                        is AuthSpec.ExistingKey -> AuthInput.KeyRef(spec.keyId)
                                        is AuthSpec.NewKey -> AuthInput.KeyRef(
                                            sshKeyRepository.add(spec.name, spec.pem, spec.passphrase),
                                        )
                                    }
                                } catch (e: Throwable) {
                                    Toast.makeText(context, "認証情報の保存に失敗しました", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                profileRepository.save(label, host, port, username, input, tags)
                                navController.popBackStack()
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
                SettingsScreen(
                    // タブとして表示するので戻る矢印は出さない（onBack=null）。
                    settings = settings,
                    onBiometricChange = { enabled ->
                        scope.launch { settingsStore.setBiometricEnabled(enabled) }
                    },
                    onSessionsTabFirstChange = { enabled ->
                        scope.launch { settingsStore.setSessionsTabFirst(enabled) }
                    },
                    onPaletteChange = { id ->
                        scope.launch { settingsStore.setTerminalPaletteId(id) }
                    },
                    onFontChange = { size, spacing ->
                        scope.launch {
                            settingsStore.setTerminalFontSizeSp(size)
                            settingsStore.setTerminalLineSpacing(spacing)
                        }
                    },
                    onOpenKeys = { navController.navigate(Routes.KEYS) },
                    onOpenKnownHosts = { navController.navigate(Routes.KNOWN_HOSTS) },
                )
            }
            composable(Routes.KNOWN_HOSTS) {
                val hosts by hostKeyStore.observeAll().collectAsState(initial = emptyList())
                KnownHostsScreen(
                    hosts = hosts,
                    onBack = { navController.popBackStack() },
                    onDelete = { entry ->
                        scope.launch { hostKeyStore.deleteAsync(entry.address) }
                    },
                )
            }
        }
    }

    // ホスト鍵が変化したときの MITM 警告。承認するまで上書きも接続もしない。
    // タブ位置に関わらず出したいので NavHost の外に置く。
    sessionController.pendingHostKeyChange?.let { pending ->
        HostKeyChangedDialog(
            pending = pending,
            onApprove = { sessionController.approveHostKeyChange() },
            onDismiss = { sessionController.dismissHostKeyChange() },
        )
    }
}

/**
 * ホスト鍵の変化を提示し、明示的な承認を求めるダイアログ。
 *
 * 中間者攻撃と区別がつかないため、既定の導線はキャンセル（＝接続しない）。承認したときだけ
 * 新しい鍵で上書きして繋ぎ直す。判断材料として旧/新のフィンガープリントを併記する。
 */
@Composable
private fun HostKeyChangedDialog(
    pending: SessionController.PendingHostKeyChange,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("ホスト鍵が変化しています") },
        text = {
            Column {
                Text(
                    "「${pending.cause.address}」が前回と異なる鍵を提示しました。" +
                        "サーバーを再構築した場合にも起こりますが、通信が傍受されている" +
                        "（中間者攻撃）可能性もあります。心当たりが無ければ承認しないでください。",
                )
                Spacer(Modifier.height(12.dp))
                Text("以前の鍵", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${pending.cause.expected.keyType} · ${pending.cause.expected.fingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text("今回の鍵", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${pending.cause.presentedKeyType} · ${pending.cause.presentedFingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
        dismissButton = {
            TextButton(onClick = onApprove) {
                Text("承認して接続", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
