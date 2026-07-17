package com.uta.tunnel.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uta.tunnel.BuildConfig

/**
 * 設定画面。TopAppBar + スクロール Column にセクションを並べる IR Tool 流儀。
 * 鍵管理はこの設定内の一項目として置く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    biometricEnabled: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    sessionsTabFirst: Boolean,
    onSessionsTabFirstChange: (Boolean) -> Unit,
    onOpenKeys: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    // タブとして表示するときは戻る矢印を出さない（onBack=null）。
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
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
            ListItem(
                headlineContent = { Text("タブの並びを入れ替え") },
                supportingContent = {
                    Text(
                        if (sessionsTabFirst) {
                            "下タブ：セッション / ホスト / 設定"
                        } else {
                            "下タブ：ホスト / セッション / 設定"
                        },
                    )
                },
                trailingContent = {
                    Switch(checked = sessionsTabFirst, onCheckedChange = onSessionsTabFirstChange)
                },
            )

            SettingsSectionTitle("端末")
            PlaceholderItem("スクロールバック行数", "履歴の保持行数")
            PlaceholderItem("カーソル形状", "ブロック / ビーム")

            SettingsSectionTitle("接続既定値")
            PlaceholderItem("既定ポート / keepalive", "接続の既定パラメータ")

            SettingsSectionTitle("セキュリティ")
            ListItem(
                headlineContent = { Text("生体認証でロック") },
                supportingContent = { Text("起動・復帰時に指紋認証を要求（デバッグビルドでは無効）") },
                trailingContent = {
                    Switch(checked = biometricEnabled, onCheckedChange = onBiometricChange)
                },
            )
            PlaceholderItem("既知ホスト（TOFU）", "保存済みフィンガープリントの管理")
            ListItem(
                headlineContent = { Text("鍵管理") },
                supportingContent = { Text("SSH 秘密鍵の登録・公開鍵コピー・削除") },
                modifier = Modifier.clickable(onClick = onOpenKeys),
            )

            SettingsSectionTitle("情報")
            PlaceholderItem("バージョン", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
