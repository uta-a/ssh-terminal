package com.uta.terminal.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.LocalEchoTransport
import com.uta.terminal.terminal.TerminalCanvas
import com.uta.terminal.terminal.TerminalPalette
import kotlinx.coroutines.delay

/**
 * 端末ホーム画面。PoC ではローカルエコー Transport で `TerminalEmulator` を駆動し、
 * 浮きカードの中に自作 Compose Canvas（[TerminalCanvas]）で描画する。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    // ソフトキーボードが出ているか（補助キー行の表示可否に使う）。
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // 出力ストリーム中（＝frame が更新され続けている間）はローディング表示にする。
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(host.frame) {
        running = true
        delay(500)
        running = false
    }

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
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
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
                Box(modifier = Modifier.fillMaxSize()) {
                    TerminalCanvas(
                        host = host,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    )
                    // 透明な入力フィールド：タップでキーボード表示、テキスト/Enter/Backspace を捕捉する。
                    // Canvas は編集器ではないため IME を開けない。フリック等の日本語入力にも対応するため
                    // 通常キーボード型にし、IME の「変換確定」した文字だけを端末へ送る（合成中は送らない）。
                    var tfv by remember {
                        mutableStateOf(TextFieldValue(INPUT_ANCHOR, TextRange(INPUT_ANCHOR.length)))
                    }
                    // 前回までに端末へ送った「確定済みテキスト」（anchor を含む）。
                    var committedSoFar by remember { mutableStateOf(INPUT_ANCHOR) }

                    // カーソル・選択ハンドルを透明化（左上に出るカーソルを消す）。
                    val transparentSelection = TextSelectionColors(
                        handleColor = Color.Transparent,
                        backgroundColor = Color.Transparent,
                    )
                    CompositionLocalProvider(LocalTextSelectionColors provides transparentSelection) {
                        BasicTextField(
                            value = tfv,
                            onValueChange = { nv ->
                                // 合成（変換）中の範囲は未確定。確定済み部分だけを対象に差分送出する。
                                val comp = nv.composition
                                val committedEnd =
                                    if (comp != null && comp.length > 0) comp.start else nv.text.length
                                val committed = nv.text.substring(0, committedEnd.coerceIn(0, nv.text.length))
                                diffAndSend(committedSoFar, committed, host, stickyCtrl, stickyAlt)
                                clearSticky()
                                committedSoFar = committed

                                // IME とのデシンクを避けるため通常はフィールドを維持（リセットしない）。
                                // 空になった時だけ anchor を再シードして backspace を継続可能に保ち、
                                // 合成なしで肥大化した時だけ縮約する。
                                if (nv.text.isEmpty() || (comp == null && nv.text.length > 256)) {
                                    tfv = TextFieldValue(INPUT_ANCHOR, TextRange(INPUT_ANCHOR.length))
                                    committedSoFar = INPUT_ANCHOR
                                } else {
                                    tfv = nv
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(color = Color.Transparent),
                            cursorBrush = SolidColor(Color.Transparent),
                            // フリック/日本語入力を許可（変換確定分のみ送出する方式）。
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.None,
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

/** 差分検出用のアンカー文字（ゼロ幅スペース）。入力フィールドは常にこの1文字だけを保持する。 */
private const val INPUT_ANCHOR = "\u200B"

/**
 * 旧テキストと新テキストの差分を端末へ送る（IME と同期を保つためフィールドはリセットしない）。
 * 共通接頭辞より後ろで、消えた分だけ Backspace(DEL)、増えた分だけ文字を送出する。
 */
private fun diffAndSend(
    old: String,
    new: String,
    host: EmulatorHost,
    ctrl: Boolean,
    alt: Boolean,
) {
    if (old == new) return
    var common = 0
    val limit = minOf(old.length, new.length)
    while (common < limit && old[common] == new[common]) common++
    // 削除された分だけ Backspace
    repeat(old.length - common) { host.sendBytes(byteArrayOf(0x7f)) }
    // 追加された分を送出（改行は CR、それ以外はコードポイント）
    val added = new.substring(common)
    var i = 0
    while (i < added.length) {
        val cp = added.codePointAt(i)
        i += Character.charCount(cp)
        when (cp) {
            '\n'.code, '\r'.code -> host.sendBytes(byteArrayOf(0x0d))
            else -> host.sendCodePoint(cp, ctrl, alt)
        }
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
