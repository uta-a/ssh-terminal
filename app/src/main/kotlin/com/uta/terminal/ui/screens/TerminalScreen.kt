package com.uta.terminal.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.LocalEchoTransport
import com.uta.terminal.terminal.TerminalCanvas
import com.uta.terminal.terminal.TerminalPalette
import kotlin.math.roundToInt

/**
 * 端末ホーム画面。PoC ではローカルエコー Transport で `TerminalEmulator` を駆動し、
 * 浮きカードの中に自作 Compose Canvas（[TerminalCanvas]）で描画する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    currentSessionLabel: String?,
    onOpenDrawer: () -> Unit,
) {
    val transport = remember { LocalEchoTransport() }
    // host は一度だけ生成する。初期サイズは仮値で、実測後に onSizeChanged で resize する。
    val host = remember(transport) { EmulatorHost(80, 24, transport) }
    var stickyCtrl by remember { mutableStateOf(false) }
    var stickyAlt by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 入力中の 1 行（IME 変換中テキストを含む）。Enter で送信し空に戻す。
    var inputTfv by remember { mutableStateOf(TextFieldValue("")) }
    // 端末フォントの表示倍率（ピンチで拡縮、⋮ メニューでリセット）。
    var fontScale by remember { mutableFloatStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    // ソフトキーボードが出ているか（補助キー行の表示可否に使う）。アニメの途中値ではなく
    // 最終目標値（imeAnimationTarget）で判定し、キーボード位置へスライドせず即スナップさせる。
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(density) > 0

    // 上部アクセントラインのローディング表示。SSH 先でコマンド実行中のみ true にする想定。
    // 入力（ローカルエコー）では光らせない。TODO: SSH 実装時に実行状態と接続する。
    @Suppress("UNUSED")
    val running by remember { mutableStateOf(false) }

    fun clearSticky() { stickyCtrl = false; stickyAlt = false }
    fun disconnect() {
        menuOpen = false
        val msg = "\r\n\u001b[31m[セッションを切断しました]\u001b[0m\r\n"
        val b = msg.toByteArray(Charsets.UTF_8)
        host.feed(b, b.size)
    }

    Scaffold(
        // インセットは content 側で扱う（補助キー行をキーボード上端へアンカーするため）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        // 接続状態インジケータ（Dynamic Color の primary を効かせるアクセント）。
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(currentSessionLabel ?: "ローカルエコー (PoC)")
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
                            onClick = { disconnect() },
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
                // 下端はナビゲーションバーと IME の大きい方に合わせる。
                // キーボード表示時はこの padding が持ち上がり、補助キー行がキーボード上端に載る。
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget)),
        ) {
            // 上部アクセントライン：実行中（出力ストリーム中）はローディングアニメ、待機中は静的な細線。
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

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                // 端末カードは参照デザインの calm な地色。周囲クロムの Dynamic Color とは分離する。
                color = androidx.compose.ui.graphics.Color(TerminalPalette.BACKGROUND),
                shadowElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 2 本指ピンチで表示倍率を変更する。倍率は 0.5〜3.0 倍にクランプ。
                        // 親 Box に置くことで、透明入力フィールドのタップ（キーボード表示）は素通しし、
                        // ピンチ（zoom≠1）のときだけ倍率を更新する。
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    fontScale = (fontScale * zoom).coerceIn(0.5f, 3.0f)
                                }
                            }
                        },
                ) {
                    // 既定文字色＝もとの Material You 色（primary）。変換確定したインライン文字も、
                    // 送信して端末に出た文字もこの色で描く（確定=送信で同色に落ち着く）。
                    val baseFgArgb = MaterialTheme.colorScheme.primary.toArgb()
                    // 入力中（IME 変換中）だけ primary を白へ寄せて明るくし、目立たせる。
                    val composingColorArgb = lerp(
                        MaterialTheme.colorScheme.primary,
                        Color.White,
                        0.62f,
                    ).toArgb()
                    // IME 変換中（未確定）の範囲。変換確定でこの範囲が消え、色が既定色へ落ち着く。
                    val composition = inputTfv.composition
                    TerminalCanvas(
                        host = host,
                        pendingInput = inputTfv.text,
                        composingStart = composition?.start ?: -1,
                        composingEnd = composition?.end ?: -1,
                        defaultFgArgb = baseFgArgb,
                        composingColorArgb = composingColorArgb,
                        fontScale = fontScale,
                        allowResize = !imeVisible,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    )
                    // 透明な入力フィールド：タップでキーボード表示。入力中の文字（IME 変換含む）は
                    // フィールドが保持し、Canvas がカーソル位置にインライン表示する。Enter で 1 行を送信。
                    // カーソル・選択ハンドルは透明化（左上に出るカーソルを消す）。
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
                            // フリック等の日本語入力を許可。Enter（Go）で行を確定送信する。
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val line = inputTfv.text
                                    val bytes = (line + "\r").toByteArray(Charsets.UTF_8)
                                    host.sendBytes(bytes)
                                    inputTfv = TextFieldValue("")
                                },
                            ),
                        )
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
                    onKey = { action -> action(host); focusRequester.requestFocus() },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        // 描画・ANSI 色・ワイド文字を一目で検証できる初期バナー（stdout 相当として流し込む）。
        val banner = "\u001b[1;32mTerminal PoC\u001b[0m — ローカルエコー\r\n" +
            "\u001b[36mcyan\u001b[0m \u001b[33myellow\u001b[0m \u001b[31mred\u001b[0m 日本語ワイド文字\r\n" +
            "文字を入力するとエコーされます。\r\n$ "
        val bytes = banner.toByteArray(Charsets.UTF_8)
        host.feed(bytes, bytes.size)
    }
}

/** キーボード上端相当に置く補助キー行（PoC 版。Ctrl/Alt は sticky トグル）。 */
@Composable
private fun ExtraKeysRow(
    stickyCtrl: Boolean,
    stickyAlt: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: ((EmulatorHost) -> Unit) -> Unit,
) {
    val scroll = rememberScrollState()
    // Dynamic Color を効かせる tonal なバー。
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
