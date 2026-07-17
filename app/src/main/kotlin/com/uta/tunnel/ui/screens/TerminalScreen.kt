package com.uta.tunnel.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.uta.tunnel.core.model.SessionState
import com.uta.tunnel.data.SettingsStore
import com.uta.tunnel.terminal.EmulatorHost
import com.uta.tunnel.terminal.TerminalCanvas
import com.uta.tunnel.terminal.TerminalPalette
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 端末ホーム画面。アクティブセッションの [EmulatorHost] を [SessionController] から受け取り、
 * 浮きカードの中に自作 Compose Canvas（[TerminalCanvas]）で描画する。未接続なら空状態を出す。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    host: EmulatorHost?,
    currentSessionLabel: String?,
    state: SessionState,
    busy: Boolean,
    fontSizeSp: Float,
    lineSpacing: Float,
    palette: TerminalPalette,
    onFontSizeChange: (Float) -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onRename: (String) -> Unit,
    onExit: () -> Unit,
) {
    // アクティブセッションが無いのに端末画面にいる場合（切断直後など）は起動ページへ戻す。
    // 「接続がありません」画面は出さない。
    if (host == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    // Ctrl/Alt sticky はセッション固有。切替で別ホストへ引き継がない（host をキーにする）。
    // 回転でも失わないよう rememberSaveable。
    var stickyCtrl by rememberSaveable(host) { mutableStateOf(false) }
    var stickyAlt by rememberSaveable(host) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // フルスクリーン（上部タイトルバー非表示）。戻るキーで解除。回転で保持。
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // 補助キー行（Esc/Ctrl/矢印等）の表示。⋮ メニューで切替。回転で保持。
    var showExtraKeys by rememberSaveable { mutableStateOf(true) }
    // セッション名変更ダイアログ。
    var renameOpen by remember { mutableStateOf(false) }
    // パスワード入力ダイアログ（sudo 等。インライン平文表示せずに送る）。
    var passDialogOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    // 入力中の 1 行（IME 変換中テキストを含む）。Enter で送信し空に戻す。
    // host をキーにし、セッション切替で未送信の入力行を別ホストへ持ち越さない（誤送信防止）。回転で保持。
    var inputTfv by rememberSaveable(host, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    // 端末フォントサイズ（sp）。設定値を初期値にし、ピンチ中はローカルで追従してから
    // デバウンスして設定へ書き戻す（ピンチのたびに DataStore へ書くと重いため）。
    // 表示設定なので全セッション共通。
    var liveFontSizeSp by remember { mutableFloatStateOf(fontSizeSp) }
    // 設定画面など外から変わったら追従する。
    LaunchedEffect(fontSizeSp) { liveFontSizeSp = fontSizeSp }
    LaunchedEffect(liveFontSizeSp) {
        if (liveFontSizeSp != fontSizeSp) {
            delay(400)
            onFontSizeChange(liveFontSizeSp)
        }
    }
    // 選択中パレットの ANSI 16 色をエミュレータへ反映する（背景・カーソルは Canvas へ直接渡す）。
    LaunchedEffect(palette, host) { host.applyPalette(palette) }
    // 履歴スクロール量（行）。0＝最新（ライブ画面）。上方向ドラッグで増える。セッション固有。回転で保持。
    var scrollOffset by rememberSaveable(host) { mutableStateOf(0) }
    // ドラッグ px の端数を持ち越して行換算する。セッション固有（回転で消えても実害なし）。
    var scrollAccumPx by remember(host) { mutableFloatStateOf(0f) }
    // TerminalCanvas が報告するセル高（px→行の換算に使う）。
    var cellHeightPx by remember { mutableFloatStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    // ソフトキーボードが出ているか。アニメの途中値でなく最終目標値で判定し、即スナップさせる。
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(density) > 0

    // 接続状態インジケータ用。
    val connecting = state is SessionState.Connecting || state is SessionState.Reconnecting
    // キーボード表示/非表示の直後はリサイズ→リモート再描画で出力が来るため、その分の busy を抑制する。
    var suppressBusy by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        suppressBusy = true
        kotlinx.coroutines.delay(700)
        suppressBusy = false
    }
    // 上部ローディング：接続確立中、またはリモートが出力中（コマンド実行中）に走らせる。
    val running = connecting || (busy && !suppressBusy)

    // 出力で履歴が伸びたとき、スクロール位置（表示中の行）を保持する。最新表示中(0)は追従して最新のまま。
    DisposableEffect(host) {
        val h = host ?: return@DisposableEffect onDispose {}
        var lastRows = h.activeRows
        h.onContentChanged = {
            val now = h.activeRows
            val delta = now - lastRows
            lastRows = now
            if (scrollOffset > 0 && delta > 0) {
                scrollOffset = (scrollOffset + delta).coerceIn(0, h.activeTranscriptRows)
            }
        }
        onDispose { h.onContentChanged = null }
    }

    // フルスクリーン中は戻るキーで解除する。
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Scaffold(
        // インセットは content 側で扱う（補助キー行をキーボード上端へアンカーするため）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!fullscreen) TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        // 接続状態インジケータ。状態に応じて色を変える。
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(statusColor(state), CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(currentSessionLabel ?: "未接続")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // フルスクリーンは ⋮ の左に独立アイコンとして置く。
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "フルスクリーン")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("セッション名を変更") },
                            onClick = { menuOpen = false; renameOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (showExtraKeys) "補助キーを隠す" else "補助キーを表示") },
                            onClick = { menuOpen = false; showExtraKeys = !showExtraKeys },
                        )
                        HorizontalDivider()
                        // 現在のフォントサイズ。行自体は情報表示なので無効化。
                        DropdownMenuItem(
                            text = { Text("表示サイズ ${liveFontSizeSp.roundToInt()} sp") },
                            onClick = {},
                            enabled = false,
                        )
                        DropdownMenuItem(
                            text = { Text("表示サイズをリセット") },
                            onClick = {
                                liveFontSizeSp = SettingsStore.DEFAULT_FONT_SIZE_SP
                                menuOpen = false
                            },
                        )
                        HorizontalDivider()
                        // 切断/失敗しているときだけ「再接続」を出す（同じ宛先へ繋ぎ直す）。
                        if (state is SessionState.Disconnected || state is SessionState.Failed) {
                            DropdownMenuItem(
                                text = { Text("再接続") },
                                onClick = { menuOpen = false; onReconnect() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("セッションを切断") },
                            onClick = { menuOpen = false; onDisconnect() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                // フルスクリーン中はタイトルバーが無いのでステータスバー分を content 側で確保する。
                .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget)),
        ) {
            // 上部アクセントライン：実行中（接続確立中・リモート出力中）はローディングアニメ、待機中は細線。
            if (running) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            run {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(palette.background),
                    shadowElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 2 本指ピンチで表示倍率、縦ドラッグで履歴スクロール。親 Box に置くことで
                            // 透明入力フィールドのタップ（キーボード表示）は素通しし、移動時のみ消費する。
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (zoom != 1f) {
                                        liveFontSizeSp = (liveFontSizeSp * zoom).coerceIn(
                                            SettingsStore.MIN_FONT_SIZE_SP,
                                            SettingsStore.MAX_FONT_SIZE_SP,
                                        )
                                    }
                                    if (pan.y != 0f && cellHeightPx > 0f) {
                                        // 下ドラッグ（pan.y>0）で過去へ、上ドラッグで最新へ。
                                        scrollAccumPx += pan.y
                                        val step = (scrollAccumPx / cellHeightPx).toInt()
                                        if (step != 0) {
                                            scrollAccumPx -= step * cellHeightPx
                                            val max = host.activeTranscriptRows
                                            scrollOffset = (scrollOffset + step).coerceIn(0, max)
                                        }
                                    }
                                }
                            }
                            // 長押しでクリップボードを入力行へ貼り付ける（Initial パスで検知し、
                            // 透明入力フィールドの誤配置ツールバーに頼らない）。タップは素通し。
                            .pointerInput(Unit) {
                                val slop = viewConfiguration.touchSlop
                                val timeout = viewConfiguration.longPressTimeoutMillis
                                while (true) {
                                    val down = awaitPointerEventScope {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    }
                                    val longPressed = try {
                                        kotlinx.coroutines.withTimeout(timeout) {
                                            awaitPointerEventScope {
                                                var pressed = true
                                                while (pressed) {
                                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                                    val ch = e.changes.firstOrNull { it.id == down.id }
                                                    if (ch == null || !ch.pressed) {
                                                        pressed = false
                                                    } else if ((ch.position - down.position).getDistance() > slop) {
                                                        pressed = false
                                                    }
                                                }
                                            }
                                            false
                                        }
                                    } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                                        true
                                    }
                                    if (longPressed) {
                                        clipboard.getText()?.let { pasted ->
                                            val text = inputTfv.text + pasted.text
                                            inputTfv = TextFieldValue(text, TextRange(text.length))
                                        }
                                    }
                                }
                            },
                    ) {
                        // 既定文字色＝もとの Material You 色（primary）。確定インラインも送信後の端末出力も同色。
                        val baseFgArgb = MaterialTheme.colorScheme.primary.toArgb()
                        // 入力中（IME 変換中）だけ primary を白へ寄せて明るくし、目立たせる。
                        val composingColorArgb = lerp(
                            MaterialTheme.colorScheme.primary,
                            Color.White,
                            0.62f,
                        ).toArgb()
                        val composition = inputTfv.composition
                        TerminalCanvas(
                            host = host,
                            pendingInput = inputTfv.text,
                            composingStart = composition?.start ?: -1,
                            composingEnd = composition?.end ?: -1,
                            defaultFgArgb = baseFgArgb,
                            composingColorArgb = composingColorArgb,
                            fontSizeSp = liveFontSizeSp,
                            lineSpacing = lineSpacing,
                            backgroundArgb = palette.background,
                            cursorArgb = palette.cursor,
                            scrollOffset = scrollOffset,
                            onCellHeight = { cellHeightPx = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                        // 透明な入力フィールド：タップでキーボード表示。入力中の文字（IME 変換含む）は
                        // フィールドが保持し、Canvas がカーソル位置にインライン表示する。Enter で 1 行を送信。
                        val transparentSelection = TextSelectionColors(
                            handleColor = Color.Transparent,
                            backgroundColor = Color.Transparent,
                        )
                        // 既定の貼り付けツールバー（左上に誤配置）を抑制。貼り付けは長押しで行う。
                        val noToolbar = remember {
                            object : TextToolbar {
                                override val status: TextToolbarStatus = TextToolbarStatus.Hidden
                                override fun hide() {}
                                override fun showMenu(
                                    rect: Rect,
                                    onCopyRequested: (() -> Unit)?,
                                    onPasteRequested: (() -> Unit)?,
                                    onCutRequested: (() -> Unit)?,
                                    onSelectAllRequested: (() -> Unit)?,
                                ) {}
                            }
                        }
                        CompositionLocalProvider(
                            LocalTextSelectionColors provides transparentSelection,
                            LocalTextToolbar provides noToolbar,
                        ) {
                            BasicTextField(
                                value = inputTfv,
                                onValueChange = { newV ->
                                    // Ctrl/Alt sticky が有効なら、挿入された 1 文字を制御コード
                                    // （Ctrl-x／Alt-x）として即送信し、バッファへは入れず消費する。
                                    // 対象は ASCII 印字文字のみ（CJK 変換中などは通常入力として扱う）。
                                    val old = inputTfv.text
                                    val consumed = if ((stickyCtrl || stickyAlt) &&
                                        newV.text.length == old.length + 1
                                    ) {
                                        var i = 0
                                        while (i < old.length && old[i] == newV.text[i]) i++
                                        val ch = newV.text[i]
                                        if (ch.code in 0x20..0x7E) {
                                            host.sendCodePoint(ch.code, stickyCtrl, stickyAlt)
                                            stickyCtrl = false
                                            stickyAlt = false
                                            scrollOffset = 0; scrollAccumPx = 0f
                                            true
                                        } else false
                                    } else false
                                    if (consumed) {
                                        // 消費した文字はバッファに残さず、合成（composition）を解除した
                                        // 確定状態へ明示的に戻して IME と再同期する（表示デシンク防止）。
                                        inputTfv = TextFieldValue(old, TextRange(old.length))
                                    } else {
                                        inputTfv = newV
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(color = Color.Transparent),
                                cursorBrush = SolidColor(Color.Transparent),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Go,
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        val bytes = (inputTfv.text + "\r").toByteArray(Charsets.UTF_8)
                                        host.sendBytes(bytes)
                                        inputTfv = TextFieldValue("")
                                        // 送信したら最新へ戻す。
                                        scrollOffset = 0; scrollAccumPx = 0f
                                    },
                                ),
                            )
                        }

                        // 履歴を遡っているときだけ「最新へ」ボタンを右下に出す。
                        if (scrollOffset > 0) {
                            FilledTonalButton(
                                onClick = { scrollOffset = 0; scrollAccumPx = 0f },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 8.dp,
                                ),
                            ) {
                                Text("最新へ ▼")
                            }
                        }
                    }
                }

                // 補助キー行はキーボードが開いていて、かつ ⋮ で表示 ON のときだけ出す。
                if (imeVisible && showExtraKeys) {
                    ExtraKeysRow(
                        stickyCtrl = stickyCtrl,
                        stickyAlt = stickyAlt,
                        onToggleCtrl = { stickyCtrl = !stickyCtrl },
                        onToggleAlt = { stickyAlt = !stickyAlt },
                        onPasswordEntry = { passDialogOpen = true },
                        onKey = { action ->
                            action(host); focusRequester.requestFocus()
                            // 修飾キーは one-shot。特殊キー送出後に解除する。
                            stickyCtrl = false; stickyAlt = false
                            scrollOffset = 0; scrollAccumPx = 0f
                        },
                    )
                }
            }
        }
    }

    // セッション名変更ダイアログ。
    if (renameOpen) {
        var newName by remember { mutableStateOf(currentSessionLabel ?: "") }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("セッション名を変更") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("名前") },
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(newName); renameOpen = false }) { Text("変更") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("キャンセル") }
            },
        )
    }

    // パスワード入力ダイアログ：マスクした入力を改行付きで送信（インラインに平文表示しない）。
    if (passDialogOpen) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passDialogOpen = false },
            title = { Text("パスワードを送信") },
            text = {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    label = { Text("パスワード") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    host.sendBytes((pass + "\r").toByteArray(Charsets.UTF_8))
                    passDialogOpen = false
                }) { Text("送信") }
            },
            dismissButton = {
                TextButton(onClick = { passDialogOpen = false }) { Text("キャンセル") }
            },
        )
    }
}

/** 接続状態を表すドット色。 */
@Composable
private fun statusColor(state: SessionState): Color = when (state) {
    is SessionState.Connected -> Color(0xFF4CAF50)
    is SessionState.Connecting, is SessionState.Reconnecting -> MaterialTheme.colorScheme.tertiary
    is SessionState.Failed -> MaterialTheme.colorScheme.error
    is SessionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * キーボード上端相当に置く補助キー行。2 段構成：
 * - 上段＝Esc/Ctrl/Alt/Tab/^C/pass（横スクロール）。Ctrl/Alt は sticky トグル。
 * - 下段＝矢印キー（幅いっぱいに均等配置で見切れず押しやすい）。
 */
@Composable
private fun ExtraKeysRow(
    stickyCtrl: Boolean,
    stickyAlt: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onPasswordEntry: () -> Unit,
    onKey: ((EmulatorHost) -> Unit) -> Unit,
) {
    val scroll = rememberScrollState()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 上段：機能キー。
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeyChip("Esc") { onKey { it.sendBytes(byteArrayOf(0x1b)) } }
                KeyChip("Ctrl", active = stickyCtrl) { onToggleCtrl() }
                KeyChip("Alt", active = stickyAlt) { onToggleAlt() }
                KeyChip("Tab") { onKey { it.sendBytes(byteArrayOf(0x09)) } }
                KeyChip("^C") { onKey { it.sendBytes(byteArrayOf(0x03)) } }
                KeyChip("pass") { onPasswordEntry() }
            }
            // 下段：矢印。幅いっぱいに均等（見切れない）。
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // sticky Ctrl/Alt を矢印にも適用（Ctrl+←/Alt+→ 等の単語移動）。送出後に onKey が解除する。
                ArrowKey("←", Modifier.weight(1f)) { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_LEFT, stickyCtrl, stickyAlt, false) } }
                ArrowKey("↓", Modifier.weight(1f)) { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_DOWN, stickyCtrl, stickyAlt, false) } }
                ArrowKey("↑", Modifier.weight(1f)) { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_UP, stickyCtrl, stickyAlt, false) } }
                ArrowKey("→", Modifier.weight(1f)) { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, stickyCtrl, stickyAlt, false) } }
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = if (active) {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun ArrowKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        Text(label, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
