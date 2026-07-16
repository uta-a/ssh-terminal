package com.uta.terminal.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.LocalEchoTransport
import com.uta.terminal.terminal.TerminalCanvas

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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun clearSticky() { stickyCtrl = false; stickyAlt = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSessionLabel ?: "ローカルエコー (PoC)") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "メニュー")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                TerminalCanvas(
                    host = host,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .focusRequester(focusRequester)
                        .focusable()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }
                        }
                        .onKeyEvent { ke ->
                            val ev = ke.nativeKeyEvent
                            if (ev.action != AndroidKeyEvent.ACTION_DOWN) return@onKeyEvent false
                            val h = host
                            val ctrl = ev.isCtrlPressed || stickyCtrl
                            val alt = ev.isAltPressed || stickyAlt
                            val shift = ev.isShiftPressed
                            when (ev.keyCode) {
                                AndroidKeyEvent.KEYCODE_ENTER -> {
                                    h.sendBytes(byteArrayOf(0x0d)); clearSticky(); return@onKeyEvent true
                                }
                                AndroidKeyEvent.KEYCODE_DEL -> {
                                    h.sendBytes(byteArrayOf(0x7f)); clearSticky(); return@onKeyEvent true
                                }
                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                    h.sendBytes(byteArrayOf(0x1b)); clearSticky(); return@onKeyEvent true
                                }
                                AndroidKeyEvent.KEYCODE_TAB -> {
                                    h.sendBytes(byteArrayOf(0x09)); clearSticky(); return@onKeyEvent true
                                }
                            }
                            if (h.sendKeyCode(ev.keyCode, ctrl, alt, shift)) {
                                clearSticky(); return@onKeyEvent true
                            }
                            var cp = ev.unicodeChar
                            if (cp == 0) cp = ev.getUnicodeChar(if (shift) AndroidKeyEvent.META_SHIFT_ON else 0)
                            if (cp != 0) {
                                h.sendCodePoint(cp, ctrl, alt); clearSticky(); return@onKeyEvent true
                            }
                            false
                        },
                )
            }

            ExtraKeysRow(
                stickyCtrl = stickyCtrl,
                stickyAlt = stickyAlt,
                onToggleCtrl = { stickyCtrl = !stickyCtrl },
                onToggleAlt = { stickyAlt = !stickyAlt },
                onKey = { action -> action(host); focusRequester.requestFocus() },
            )
        }
    }

    LaunchedEffect(Unit) {
        // 描画・ANSI 色・ワイド文字を一目で検証できる初期バナー（stdout 相当として流し込む）。
        val banner = "\u001b[1;32mTerminal PoC\u001b[0m — ローカルエコー\r\n" +
            "\u001b[36mcyan\u001b[0m \u001b[33myellow\u001b[0m \u001b[31mred\u001b[0m 日本語ワイド文字\r\n" +
            "文字を入力するとエコーされます。\r\n$ "
        val bytes = banner.toByteArray(Charsets.UTF_8)
        host.feed(bytes, bytes.size)
        focusRequester.requestFocus()
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
