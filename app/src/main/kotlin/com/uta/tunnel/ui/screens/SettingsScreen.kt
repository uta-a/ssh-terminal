package com.uta.tunnel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uta.tunnel.BuildConfig
import com.uta.tunnel.data.SettingsStore
import com.uta.tunnel.data.SettingsUiState
import com.uta.tunnel.terminal.TerminalPalettes
import kotlin.math.roundToInt

/**
 * 設定画面。TopAppBar + スクロール Column にセクションを並べる IR Tool 流儀。
 * 鍵管理はこの設定内の一項目として置く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsUiState,
    onBiometricChange: (Boolean) -> Unit,
    onSessionsTabFirstChange: (Boolean) -> Unit,
    onPaletteChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onFontChange: (sizeSp: Float, lineSpacing: Float) -> Unit,
    onOpenKeys: () -> Unit,
    onOpenKnownHosts: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var paletteDialog by rememberSaveable { mutableStateOf(false) }
    var themeModeDialog by rememberSaveable { mutableStateOf(false) }
    var fontDialog by rememberSaveable { mutableStateOf(false) }

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
            ListItem(
                headlineContent = { Text("テーマモード（明暗）") },
                supportingContent = { Text(themeModeLabel(settings.themeMode)) },
                modifier = Modifier.clickable { themeModeDialog = true },
            )
            ListItem(
                headlineContent = { Text("テーマ・配色パレット") },
                supportingContent = { Text(paletteLabel(settings.paletteId)) },
                modifier = Modifier.clickable { paletteDialog = true },
            )
            ListItem(
                headlineContent = { Text("フォント・行間") },
                supportingContent = {
                    Text(
                        "端末の等幅フォント ${settings.fontSizeSp.roundToInt()} sp / " +
                            "行間 ${"%.2f".format(settings.lineSpacing)}",
                    )
                },
                modifier = Modifier.clickable { fontDialog = true },
            )
            ListItem(
                headlineContent = { Text("タブの並びを入れ替え") },
                supportingContent = {
                    Text(
                        if (settings.sessionsTabFirst) {
                            "下タブ：セッション / ホスト / 設定"
                        } else {
                            "下タブ：ホスト / セッション / 設定"
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.sessionsTabFirst,
                        onCheckedChange = onSessionsTabFirstChange,
                    )
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
                    Switch(
                        checked = settings.biometricEnabled == true,
                        onCheckedChange = onBiometricChange,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("既知ホスト（TOFU）") },
                supportingContent = { Text("初回接続で信頼したホスト鍵の確認・削除") },
                modifier = Modifier.clickable(onClick = onOpenKnownHosts),
            )
            ListItem(
                headlineContent = { Text("鍵管理") },
                supportingContent = { Text("SSH 秘密鍵の登録・公開鍵コピー・削除") },
                modifier = Modifier.clickable(onClick = onOpenKeys),
            )

            SettingsSectionTitle("情報")
            PlaceholderItem("バージョン", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    if (themeModeDialog) {
        ThemeModeDialog(
            current = settings.themeMode,
            onSelect = {
                onThemeModeChange(it)
                themeModeDialog = false
            },
            onDismiss = { themeModeDialog = false },
        )
    }

    if (paletteDialog) {
        PaletteDialog(
            current = settings.paletteId,
            onSelect = {
                onPaletteChange(it)
                paletteDialog = false
            },
            onDismiss = { paletteDialog = false },
        )
    }

    if (fontDialog) {
        FontDialog(
            fontSizeSp = settings.fontSizeSp,
            lineSpacing = settings.lineSpacing,
            onApply = { size, spacing ->
                onFontChange(size, spacing)
                fontDialog = false
            },
            onDismiss = { fontDialog = false },
        )
    }
}

/** 選択中パレットの表示名（不明な id は既定として扱う）。 */
private fun paletteLabel(id: String): String = when (id) {
    TerminalPalettes.DYNAMIC_ID -> "Dynamic Color（壁紙連動）"
    else -> (TerminalPalettes.byId(id) ?: TerminalPalettes.Default).label
}

/** 明暗モードの表示名。 */
private fun themeModeLabel(mode: String): String = when (mode) {
    SettingsStore.THEME_MODE_LIGHT -> "ライト"
    SettingsStore.THEME_MODE_DARK -> "ダーク"
    else -> "システムに追従"
}

@Composable
private fun ThemeModeDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SettingsStore.THEME_MODE_SYSTEM to "システムに追従",
        SettingsStore.THEME_MODE_LIGHT to "ライト",
        SettingsStore.THEME_MODE_DARK to "ダーク",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("テーマモード") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun PaletteDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 固定プリセット ＋ 壁紙連動。ANSI 16 色は Dynamic では作れないため、
    // Dynamic は背景・文字色・カーソルのみテーマ由来になる（一覧の説明で明示する）。
    val options = TerminalPalettes.presets.map { it.id to it.label } +
        (TerminalPalettes.DYNAMIC_ID to "Dynamic Color（壁紙連動）")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配色パレット") },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == current, onClick = { onSelect(id) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun FontDialog(
    fontSizeSp: Float,
    lineSpacing: Float,
    onApply: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var size by rememberSaveable { mutableFloatStateOf(fontSizeSp) }
    var spacing by rememberSaveable { mutableFloatStateOf(lineSpacing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォント・行間") },
        text = {
            Column {
                Text("サイズ ${size.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = SettingsStore.MIN_FONT_SIZE_SP..SettingsStore.MAX_FONT_SIZE_SP,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "行間 ${"%.2f".format(spacing)}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = spacing,
                    onValueChange = { spacing = it },
                    valueRange = SettingsStore.MIN_LINE_SPACING..SettingsStore.MAX_LINE_SPACING,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "端末画面のピンチ操作でもサイズを変えられます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(size, spacing) }) { Text("適用") }
        },
        dismissButton = {
            TextButton(onClick = {
                size = SettingsStore.DEFAULT_FONT_SIZE_SP
                spacing = SettingsStore.DEFAULT_LINE_SPACING
            }) { Text("既定に戻す") }
        },
    )
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
