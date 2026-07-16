package com.uta.terminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 端末ホーム画面（スケルトン）。
 * 浮きカードの中に端末出力を描く方針の器だけを用意する。
 * 実際の描画は `TerminalCanvas`（自作 Compose Canvas）で置き換える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    currentSessionLabel: String?,
    onOpenDrawer: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSessionLabel ?: "未接続") },
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
            // 浮きカード：ページ背景の上に、端末出力を描く器を浮かせる。
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
                // TODO: TerminalCanvas に置換。現状は方針確認用のプレースホルダ出力。
                Text(
                    text = "user@host:~$ \n（ここに端末出力が描画されます）",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // TODO: 本来は IME 上端にアンカーする ExtraKeysRow に置換。
            PlaceholderExtraKeysRow()
        }
    }
}

@Composable
private fun PlaceholderExtraKeysRow() {
    val keys = listOf("Esc", "Ctrl", "Shift", "Alt", "Tab", "←", "↓", "↑", "→")
    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { k ->
            FilledTonalButton(onClick = { /* TODO: stdin へ送出 */ }) {
                Text(k, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
