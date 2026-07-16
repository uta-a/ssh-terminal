package com.uta.terminal.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.uta.terminal.core.model.SessionState
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.TerminalCanvas
import com.uta.terminal.terminal.TerminalPalette
import kotlin.math.roundToInt

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
    onOpenDrawer: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var stickyCtrl by remember { mutableStateOf(false) }
    var stickyAlt by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 入力中の 1 行（IME 変換中テキストを含む）。Enter で送信し空に戻す。
    var inputTfv by remember { mutableStateOf(TextFieldValue("")) }
    // 端末フォントの表示倍率（ピンチで拡縮、⋮ メニューでリセット）。
    var fontScale by remember { mutableFloatStateOf(1f) }
    // 履歴スクロール量（行）。0＝最新（ライブ画面）。上方向ドラッグで増える。
    var scrollOffset by remember { mutableIntStateOf(0) }
    // ドラッグ px の端数を持ち越して行換算する。
    var scrollAccumPx by remember { mutableFloatStateOf(0f) }
    // TerminalCanvas が報告するセル高（px→行の換算に使う）。
    var cellHeightPx by remember { mutableFloatStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    // ソフトキーボードが出ているか。アニメの途中値でなく最終目標値で判定し、即スナップさせる。
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(density) > 0

    // 上部アクセントラインのローディング：接続確立中（Connecting/Reconnecting）に光らせる。
    val connecting = state is SessionState.Connecting || state is SessionState.Reconnecting

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

    Scaffold(
        // インセットは content 側で扱う（補助キー行をキーボード上端へアンカーするため）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "メニュー")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // 現在の表示サイズ（100% = 基準）。行自体は情報表示なので無効化。
                        DropdownMenuItem(
                            text = { Text("表示サイズ ${(fontScale * 100).roundToInt()}%") },
                            onClick = {},
                            enabled = false,
                        )
                        DropdownMenuItem(
                            text = { Text("表示サイズをリセット") },
                            onClick = { fontScale = 1f; menuOpen = false },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("セッションを切断") },
                            enabled = host != null,
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
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget)),
        ) {
            // 上部アクセントライン：接続確立中はローディングアニメ、それ以外は静的な細線。
            if (connecting) {
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

            if (host == null) {
                EmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(TerminalPalette.BACKGROUND),
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
                                        fontScale = (fontScale * zoom).coerceIn(0.5f, 3.0f)
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
                            fontScale = fontScale,
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
                        CompositionLocalProvider(LocalTextSelectionColors provides transparentSelection) {
                            BasicTextField(
                                value = inputTfv,
                                onValueChange = { inputTfv = it },
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

                // 補助キー行はキーボードが開いているときだけ表示する。
                if (imeVisible) {
                    ExtraKeysRow(
                        stickyCtrl = stickyCtrl,
                        stickyAlt = stickyAlt,
                        onToggleCtrl = { stickyCtrl = !stickyCtrl },
                        onToggleAlt = { stickyAlt = !stickyAlt },
                        onKey = { action ->
                            action(host); focusRequester.requestFocus()
                            scrollOffset = 0; scrollAccumPx = 0f
                        },
                    )
                }
            }
        }
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

/** 未接続時の空状態。 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "接続がありません",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "左上のメニュー →「新規セッション」から接続してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** キーボード上端相当に置く補助キー行（Ctrl/Alt は sticky トグル）。 */
@Composable
private fun ExtraKeysRow(
    stickyCtrl: Boolean,
    stickyAlt: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: ((EmulatorHost) -> Unit) -> Unit,
) {
    val scroll = rememberScrollState()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyChip("Esc") { onKey { it.sendBytes(byteArrayOf(0x1b)) } }
            KeyChip("Ctrl", active = stickyCtrl) { onToggleCtrl() }
            KeyChip("Alt", active = stickyAlt) { onToggleAlt() }
            KeyChip("Tab") { onKey { it.sendBytes(byteArrayOf(0x09)) } }
            KeyChip("^C") { onKey { it.sendBytes(byteArrayOf(0x03)) } }
            KeyChip("←") { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_LEFT, false, false, false) } }
            KeyChip("↓") { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_DOWN, false, false, false) } }
            KeyChip("↑") { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_UP, false, false, false) } }
            KeyChip("→") { onKey { it.sendKeyCode(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, false, false, false) } }
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
