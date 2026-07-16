package com.uta.terminal.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 設定画面（スケルトン）。TopAppBar + スクロール Column にセクションを並べる IR Tool 流儀。
 * 鍵管理はこの設定内の一項目として置く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionTitle("外観")
            PlaceholderItem("テーマ・配色パレット", "Dynamic Color / モダンパレット")
            PlaceholderItem("フォント・行間", "端末の等幅フォントと行間")

            SettingsSectionTitle("端末")
            PlaceholderItem("スクロールバック行数", "履歴の保持行数")
            PlaceholderItem("カーソル形状", "ブロック / ビーム")

            SettingsSectionTitle("接続既定値")
            PlaceholderItem("既定ポート / keepalive", "接続の既定パラメータ")

            SettingsSectionTitle("セキュリティ")
            PlaceholderItem("生体認証", "秘密のアンロック方式")
            PlaceholderItem("既知ホスト（TOFU）", "保存済みフィンガープリントの管理")
            PlaceholderItem("鍵管理", "鍵の生成・インポート・削除")

            SettingsSectionTitle("情報")
            PlaceholderItem("バージョン", "0.1.0")
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlaceholderItem(title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}
